package com.andymods.murimcultivation.npc;

import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.sect.Sect;
import com.andymods.murimcultivation.sect.SectRank;
import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.technique.TechniqueBehaviours;
import com.andymods.murimcultivation.technique.TechniqueMastery;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * A martial artist's side of {@code TechniqueService.cast}: which arts it knows, when one is
 * worth casting, and casting it through the very same behaviour a player's cast runs.
 *
 * <p>Artists have no Qi pool and no mastery to train. Cooldowns — the technique's own,
 * stretched by {@link MurimConfig#npcCooldownMultiplier()} — are the only limit, and mastery
 * is fixed by realm, so a higher-realm artist's arts hit harder and come round faster.
 */
public final class NpcTechniques {

    /** Below this fraction of its health, an artist that knows a healing art uses it. */
    private static final float HEAL_BELOW = 0.5F;

    /** Shadowless Step is a gap-closer: not worth it when already this close, in blocks. */
    private static final double DASH_MIN_DISTANCE = 5.0D;

    private NpcTechniques() {
    }

    /** One art an artist can cast. */
    public record Art(ResourceLocation id, Technique technique) {
    }

    /**
     * The rank an artist of a realm holds in its own sect, which bounds what it was taught.
     * One rank per realm, from outer disciple at the first; elders are the fourth realm and up.
     */
    public static SectRank rankFor(int realmTier) {
        return switch (Math.max(1, realmTier)) {
            case 1 -> SectRank.OUTER_DISCIPLE;
            case 2 -> SectRank.INNER_DISCIPLE;
            case 3 -> SectRank.CORE_DISCIPLE;
            default -> SectRank.ELDER;
        };
    }

    /** The mastery an artist of a realm fights with: a fifth of the way per realm. */
    public static int masteryFor(int realmTier) {
        return TechniqueMastery.clamp(Math.max(1, realmTier) * 20);
    }

    /**
     * What the artist can cast: its sect's teachings up to the rank its realm implies, keeping
     * only arts an NPC may use and whose realm gate it meets.
     */
    public static List<Art> knownArts(MartialArtistEntity artist) {
        Optional<Sect> sect = artist.sect();
        if (sect.isEmpty()) {
            return List.of();
        }
        Registry<Technique> registry =
                artist.level().registryAccess().registryOrThrow(MurimRegistries.TECHNIQUE);
        return sect.get().teachingsUpTo(rankFor(artist.realmTier())).stream()
                .flatMap(id -> registry.getOptional(id).map(technique -> new Art(id, technique)).stream())
                .filter(art -> TechniqueBehaviours.npcUsable(art.technique().behaviour()))
                .filter(art -> art.technique().requiredRealmTier() <= artist.realmTier())
                .toList();
    }

    /**
     * The art worth casting right now, if any: a heal when hurt, otherwise the first ready art
     * whose reach covers the target. Grip is checked here too, so a disarmed swordsman simply
     * stops throwing Sword Qi rather than trying and failing.
     */
    public static Optional<Art> choose(MartialArtistEntity artist, LivingEntity target) {
        long now = artist.level().getGameTime();
        List<Art> ready = knownArts(artist).stream()
                .filter(art -> artist.isArtReady(art.id(), now))
                .filter(art -> art.technique().hand().isSatisfiedBy(artist))
                .toList();

        if (artist.getHealth() < artist.getMaxHealth() * HEAL_BELOW) {
            Optional<Art> heal = ready.stream()
                    .filter(art -> art.technique().behaviour().equals(TechniqueBehaviours.INTERNAL_HEALING))
                    .findFirst();
            if (heal.isPresent()) {
                return heal;
            }
        }

        if (target == null || !target.isAlive() || !artist.hasLineOfSight(target)) {
            return Optional.empty();
        }
        double distance = artist.distanceTo(target);
        return ready.stream().filter(art -> inReach(art, distance)).findFirst();
    }

    private static boolean inReach(Art art, double distance) {
        ResourceLocation behaviour = art.technique().behaviour();
        if (behaviour.equals(TechniqueBehaviours.SWORD_QI)) {
            return distance <= art.technique().power().range();
        }
        if (behaviour.equals(TechniqueBehaviours.DIVINE_PALM)) {
            return distance <= art.technique().power().radius() + 1.0D;
        }
        if (behaviour.equals(TechniqueBehaviours.SHADOWLESS_STEP)) {
            return distance >= DASH_MIN_DISTANCE && distance <= art.technique().power().range() * 2.0D;
        }
        // Internal Healing is chosen by health above, never by reach.
        return false;
    }

    /**
     * Casts an art: faces the target so directional arts go where they should, fires the shared
     * behaviour, and starts the cooldown only if it did something — the same refund rule a
     * player's cast follows.
     */
    public static boolean cast(MartialArtistEntity artist, Art art, LivingEntity target) {
        if (target != null) {
            face(artist, target);
        }
        int mastery = masteryFor(artist.realmTier());
        boolean fired = TechniqueBehaviours.get(art.technique().behaviour())
                .map(behaviour -> behaviour.execute(artist, art.id(), art.technique(), mastery))
                .orElse(false);
        if (fired) {
            int cooldown = TechniqueMastery.cooldownTicks(art.technique().cooldownTicks(), mastery);
            long stretched = Math.round(cooldown * MurimConfig.npcCooldownMultiplier());
            artist.startArtCooldown(art.id(), artist.level().getGameTime() + Math.max(20L, stretched));
            artist.swing(InteractionHand.MAIN_HAND);
        }
        return fired;
    }

    /**
     * Turns head and body toward the target. Behaviours read the caster's look vector, which
     * for a mob comes from its head rotation, so both must be set, not just the body.
     */
    private static void face(MartialArtistEntity artist, LivingEntity target) {
        Vec3 delta = target.getEyePosition().subtract(artist.getEyePosition());
        float yaw = (float) Math.toDegrees(Mth.atan2(delta.z, delta.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Mth.atan2(delta.y, delta.horizontalDistance()));
        artist.setYRot(yaw);
        artist.setYHeadRot(yaw);
        artist.yBodyRot = yaw;
        artist.setXRot(pitch);
    }
}
