package com.andymods.murimcultivation.cultivation;

import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.system.QuestTracker;
import com.andymods.murimcultivation.system.SystemNotification;
import com.andymods.murimcultivation.system.SystemNotifications;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Optional;

/**
 * Breaking through to the next realm: the decision the whole progression is built around.
 *
 * <p>A breakthrough is never automatic. The player must be at the peak of their realm with the
 * progress banked, and the realm above sets its own bar for foundation purity and for how open
 * the meridian network must be. Even then the attempt is a roll, weighted by how well prepared
 * they are — and failing it inflicts {@link DeviationService Qi Deviation}.
 *
 * <p>That asymmetry is the point. If attempting were free the correct play would be to mash the
 * key the moment it lights up; because it is not, a cultivator has a reason to keep refining a
 * foundation they could already technically advance on.
 */
public final class BreakthroughService {

    private BreakthroughService() {
    }

    /** Why a breakthrough attempt was refused, or that it may proceed. */
    public enum Eligibility {
        READY(null),
        NOT_AWAKENED("murimcultivation.breakthrough.not_awakened"),
        NO_REALM_DATA("murimcultivation.breakthrough.no_realm_data"),
        ALREADY_AT_PEAK_REALM("murimcultivation.breakthrough.already_at_peak_realm"),
        SUBSTAGE_NOT_PEAK("murimcultivation.breakthrough.substage_not_peak"),
        INSUFFICIENT_PROGRESS("murimcultivation.breakthrough.insufficient_progress"),
        INSUFFICIENT_PURITY("murimcultivation.breakthrough.insufficient_purity"),
        MERIDIANS_TOO_NARROW("murimcultivation.breakthrough.meridians_too_narrow"),
        SUFFERING_DEVIATION("murimcultivation.breakthrough.suffering_deviation");

        private final String translationKey;

        Eligibility(String translationKey) {
            this.translationKey = translationKey;
        }

        public boolean isReady() {
            return this == READY;
        }

        public Component message() {
            return translationKey == null ? Component.empty() : Component.translatable(translationKey);
        }
    }

    /** The result of an attempt that was actually allowed to roll. */
    public record Result(boolean succeeded, double chance, Realm realm, DeviationService.Outcome deviation) {

        public static Result success(double chance, Realm entered) {
            return new Result(true, chance, entered, null);
        }

        public static Result failure(double chance, Realm attempted, DeviationService.Outcome deviation) {
            return new Result(false, chance, attempted, deviation);
        }
    }

    /**
     * Whether the player may attempt a breakthrough right now, and if not, why.
     *
     * <p>Evaluated against the realm being broken <em>into</em>, not the current one: it is the
     * next realm that decides how clean a foundation and how open a network the body needs to
     * survive the transition.
     */
    public static Eligibility check(ServerPlayer player) {
        CultivationData data = CultivationService.data(player);

        if (!data.isAwakened()) {
            return Eligibility.NOT_AWAKENED;
        }
        if (data.deviation().isActive()) {
            return Eligibility.SUFFERING_DEVIATION;
        }

        Registry<Realm> registry = CultivationService.realmRegistry(player);
        Optional<Realm> current = CultivationService.realmOf(registry, data);
        if (current.isEmpty()) {
            return Eligibility.NO_REALM_DATA;
        }

        Optional<Holder.Reference<Realm>> next = RealmProgression.next(registry, current.get());
        if (next.isEmpty()) {
            return Eligibility.ALREADY_AT_PEAK_REALM;
        }

        if (!data.substage().isLast()) {
            return Eligibility.SUBSTAGE_NOT_PEAK;
        }
        if (data.progress() < current.get().progressToLeave(data.substage())) {
            return Eligibility.INSUFFICIENT_PROGRESS;
        }

        Realm target = next.get().value();
        if (data.purity() < target.purityFloor()) {
            return Eligibility.INSUFFICIENT_PURITY;
        }
        if (data.openMeridianCount() < target.requiredOpenMeridians()) {
            return Eligibility.MERIDIANS_TOO_NARROW;
        }

        return Eligibility.READY;
    }

    /**
     * The weights and bounds governing breakthrough odds.
     *
     * <p>Extracted into a record so {@link #successChance(Realm, CultivationData, double, Tuning)}
     * is a pure function of its inputs and can be unit tested. A formula that reads config
     * directly cannot be tested at all, because {@code ModConfigSpec} values throw until a world
     * has loaded them — and the odds curve is exactly the code most worth testing.
     */
    public record Tuning(double purityWeight,
                         double meridianWeight,
                         double densityWeight,
                         double minChance,
                         double maxChance,
                         double minorFailureChance,
                         double severeFailureChance) {

        public static Tuning fromConfig() {
            return new Tuning(
                    MurimConfig.breakthroughPurityWeight(),
                    MurimConfig.breakthroughMeridianWeight(),
                    MurimConfig.breakthroughDensityWeight(),
                    MurimConfig.breakthroughMinChance(),
                    MurimConfig.breakthroughMaxChance(),
                    MurimConfig.breakthroughMinorFailureChance(),
                    MurimConfig.breakthroughSevereFailureChance());
        }
    }

    /**
     * The odds of surviving the transition into {@code target}.
     *
     * <p>Starts from the target realm's own base chance and is improved by everything the player
     * could have chosen to do beforehand: refining purity past the minimum, opening more
     * meridians than strictly required, and attempting it somewhere with strong ambient Qi.
     * A cultivator who did all three has meaningfully better odds than one who scraped in.
     */
    public static double successChance(Realm target, CultivationData data, double density, Tuning tuning) {
        double chance = target.breakthroughBaseChance();

        // Purity above the realm's floor, as a fraction of the headroom that remains.
        double purityHeadroom = CultivationData.MAX_PURITY - target.purityFloor();
        if (purityHeadroom > 0.0D) {
            double surplus = Math.max(0.0D, data.purity() - target.purityFloor()) / purityHeadroom;
            chance += surplus * tuning.purityWeight();
        }

        // Meridians opened beyond the requirement, as a fraction of those still closed.
        int spareNodes = Math.max(0, data.openMeridianCount() - target.requiredOpenMeridians());
        int closedNodes = Math.max(1, Meridian.count() - target.requiredOpenMeridians());
        chance += (spareNodes / (double) closedNodes) * tuning.meridianWeight();

        // Attempting it in a strong place helps; a barren one hurts.
        chance += (density - 1.0D) * tuning.densityWeight();

        return Math.max(tuning.minChance(), Math.min(tuning.maxChance(), chance));
    }

    public static double successChance(ServerPlayer player, CultivationData data, Realm target) {
        return successChance(target, data, QiDensity.multiplierFor(player), Tuning.fromConfig());
    }

    /** The odds for the player's actual next realm, for display before they commit. */
    public static Optional<Double> successChanceForNextRealm(ServerPlayer player) {
        Registry<Realm> registry = CultivationService.realmRegistry(player);
        CultivationData data = CultivationService.data(player);
        return CultivationService.realmOf(registry, data)
                .flatMap(current -> RealmProgression.next(registry, current))
                .map(next -> successChance(player, data, next.value()));
    }

    /**
     * Attempts a breakthrough. Returns empty if the attempt was refused outright; otherwise the
     * roll happened and the result says what came of it.
     *
     * <p>The banked progress is spent either way. An attempt is a real commitment, not a free
     * lottery ticket you can re-buy until it comes up.
     */
    public static Optional<Result> attempt(ServerPlayer player) {
        if (!check(player).isReady()) {
            return Optional.empty();
        }

        Registry<Realm> registry = CultivationService.realmRegistry(player);
        CultivationData data = CultivationService.data(player);
        Realm current = CultivationService.realmOf(registry, data).orElseThrow();
        Holder.Reference<Realm> nextHolder = RealmProgression.next(registry, current).orElseThrow();
        Realm target = nextHolder.value();

        MeditationService.stop(player, MeditationService.Interruption.MANUAL);

        Tuning tuning = Tuning.fromConfig();
        double chance = successChance(target, data, QiDensity.multiplierFor(player), tuning);
        data.setProgress(data.progress() - current.progressToLeave(data.substage()));

        if (player.getRandom().nextDouble() < chance) {
            CultivationService.setRealm(player, nextHolder.key(), Substage.EARLY);
            data.systemProgress().grantPoints(MurimConfig.statPointsPerRealm());
            celebrate(player, target, chance);
            SystemNotifications.send(player, SystemNotification.realmAttained(target.fullDisplayName()));
            // A new realm can satisfy a realm-gated objective and unlock further quests.
            QuestTracker.evaluate(player);
            return Optional.of(Result.success(chance, target));
        }

        // Failure: the body could not hold the transition. Realm and substage are kept — the
        // player has not been demoted — but the Qi has turned on them.
        DeviationService.Outcome outcome = DeviationService.inflict(player, severityForFailedAttempt(chance, tuning));
        SystemNotifications.send(player, SystemNotification.warning(
                Component.translatable(outcome.severity().translationKey())));
        player.sendSystemMessage(Component.translatable("murimcultivation.breakthrough.failed",
                formatPercent(chance)));
        return Optional.of(Result.failure(chance, target, outcome));
    }

    /**
     * How badly a failure hurts, keyed off the odds the player accepted.
     *
     * <p>Failing a well-prepared attempt is bad luck and costs little; failing a desperate gamble
     * on poor odds tears something. This is what makes "I could attempt it, but I should refine
     * my foundation first" a real thought.
     */
    public static DeviationSeverity severityForFailedAttempt(double chance, Tuning tuning) {
        if (chance >= tuning.minorFailureChance()) {
            return DeviationSeverity.MINOR_BLOCKAGE;
        }
        if (chance >= tuning.severeFailureChance()) {
            return DeviationSeverity.REVERSE_FLOW;
        }
        return DeviationSeverity.SHATTERED_MERIDIAN;
    }

    public static DeviationSeverity severityForFailedAttempt(double chance) {
        return severityForFailedAttempt(chance, Tuning.fromConfig());
    }

    // --- Presentation -----------------------------------------------------------------

    /** The tribulation: the moment the whole server should notice. */
    private static void celebrate(ServerPlayer player, Realm entered, double chance) {
        player.sendSystemMessage(Component.translatable(
                "murimcultivation.breakthrough.success", entered.fullDisplayName()));

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);

        if (player.level() instanceof ServerLevel level) {
            spawnAscensionColumn(level, player.position(), entered);
            if (MurimConfig.breakthroughLightning()) {
                strikeVisualLightning(level, player.position());
            }
        }

        // Broadcast to everyone *else*: the player already got the personal message above.
        if (MurimConfig.announceBreakthroughs() && player.getServer() != null) {
            Component broadcast = Component.translatable("murimcultivation.breakthrough.broadcast",
                    player.getDisplayName(), entered.fullDisplayName());
            for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
                if (other != player) {
                    other.sendSystemMessage(broadcast);
                }
            }
        }
    }

    /** A rising column of light in the realm's own aura colour. */
    private static void spawnAscensionColumn(ServerLevel level, Vec3 origin, Realm entered) {
        for (int i = 0; i < 80; i++) {
            double height = i * 0.25D;
            double radius = 0.8D - Math.min(0.7D, height * 0.03D);
            double angle = i * 0.6D;
            level.sendParticles(ParticleTypes.END_ROD,
                    origin.x + Math.cos(angle) * radius,
                    origin.y + height,
                    origin.z + Math.sin(angle) * radius,
                    1, 0.0D, 0.05D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.FLASH, origin.x, origin.y + 1.0D, origin.z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.ENCHANT, origin.x, origin.y + 1.2D, origin.z,
                120, 1.0D, 1.5D, 1.0D, 0.6D);
    }

    /**
     * Heavenly tribulation, staged as visual-only lightning: it sells the moment without
     * setting the player's house on fire or hurting them for succeeding.
     */
    private static void strikeVisualLightning(ServerLevel level, Vec3 origin) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(origin);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    private static String formatPercent(double chance) {
        return String.format(Locale.ROOT, "%.0f%%", chance * 100.0D);
    }
}
