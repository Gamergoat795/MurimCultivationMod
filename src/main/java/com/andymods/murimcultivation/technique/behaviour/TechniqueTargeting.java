package com.andymods.murimcultivation.technique.behaviour;

import com.andymods.murimcultivation.config.MurimConfig;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
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
    public static boolean canHarm(ServerPlayer caster, Entity target) {
        if (target == caster || !(target instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (target instanceof Player && !MurimConfig.techniquesHarmPlayers()) {
            return false;
        }
        return !caster.isAlliedTo(target);
    }

    /** Living targets within a box around a point that the caster is allowed to hit. */
    public static List<LivingEntity> harmableNear(ServerPlayer caster, Vec3 centre, double radius) {
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

    /** Sends a player's own velocity to their client, for self-propelling techniques. */
    public static void syncSelfMotion(ServerPlayer player) {
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}
