package com.andymods.murimcultivation.technique.behaviour;

import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.npc.MartialArtistEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared targeting and knockback helpers, so the offensive behaviours agree on the rules. */
public final class TechniqueTargeting {

    private TechniqueTargeting() {
    }

    /**
     * Whether a technique may hurt this target.
     *
     * <p>Centralised so the PvP toggle cannot be honoured by some techniques and forgotten by
     * others — the usual way a config option ends up half-working.
     */
    public static boolean canHarm(LivingEntity caster, Entity target) {
        if (target == caster || !(target instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (target instanceof Player && !MurimConfig.techniquesHarmPlayers()) {
            return false;
        }
        // A sword art cast into a crowd must not cut down the caster's own sect-mates.
        if (caster instanceof MartialArtistEntity artist && target instanceof MartialArtistEntity other
                && artist.sectId().equals(other.sectId())) {
            return false;
        }
        return !caster.isAlliedTo(target);
    }

    /** Living targets within a box around a point that the caster is allowed to hit. */
    public static List<LivingEntity> harmableNear(LivingEntity caster, Vec3 centre, double radius) {
        AABB box = new AABB(centre.subtract(radius, radius, radius), centre.add(radius, radius, radius));
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : caster.level().getEntities(caster, box, e -> canHarm(caster, e))) {
            if (entity instanceof LivingEntity living && living.position().distanceToSqr(centre) <= radius * radius) {
                targets.add(living);
            }
        }
        return targets;
    }

    /**
     * Applies knockback, syncing it to the client when the target is a player.
     *
     * <p>A player's client is authoritative over their own movement, so a server-side velocity
     * change is ignored unless it is explicitly sent. Without this, knockback silently does
     * nothing in PvP while working fine on mobs.
     */
    public static void knockBack(LivingEntity target, Vec3 impulse) {
        target.push(impulse.x, impulse.y, impulse.z);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
        }
    }

    /**
     * Makes a self-propelling technique's velocity stick. A player's client owns their movement
     * and must be sent it explicitly; any other entity is moved by the server, and marking it is
     * enough for the tracker to broadcast the change.
     */
    public static void syncSelfMotion(LivingEntity caster) {
        caster.hurtMarked = true;
        if (caster instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    /** The damage source for a technique's hit: a player attack for players, a mob attack otherwise. */
    public static DamageSource damageFrom(LivingEntity caster) {
        return caster instanceof Player player
                ? caster.damageSources().playerAttack(player)
                : caster.damageSources().mobAttack(caster);
    }

    /**
     * Tells the caster why a technique did nothing, on their action bar. Only a player has one;
     * for anyone else the refusal is simply silent.
     */
    public static void tellCaster(LivingEntity caster, Component message) {
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(message, true);
        }
    }
}
