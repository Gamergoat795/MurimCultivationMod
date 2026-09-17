package com.andymods.murimcultivation.technique.behaviour;

import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.technique.TechniqueBuffs;
import com.andymods.murimcultivation.technique.TechniqueMastery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sword Qi, Divine Palm and Sword Force: the arts that hurt people. */
public final class OffensiveBehaviours {

    private OffensiveBehaviours() {
    }

    /**
     * 검기 Sword Qi — a crescent of edged Qi thrown from the blade.
     *
     * <p>Implemented as a swept line rather than a projectile entity: it cuts everything along
     * its path, which is what a slash should do, and it needs no entity registration or
     * renderer to look right.
     */
    public static boolean swordQi(ServerPlayer player, ResourceLocation id,
                                  Technique technique, int mastery) {
        double range = Math.max(1.0D, technique.power().range());
        double damage = TechniqueMastery.damage(technique.power(), mastery);

        Vec3 origin = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();

        Set<LivingEntity> struck = new HashSet<>();
        int steps = (int) Math.ceil(range * 2);

        for (int step = 0; step <= steps; step++) {
            Vec3 point = origin.add(direction.scale(range * step / steps));

            // Stop at the first solid block so Sword Qi does not cut through walls.
            BlockPos blockPos = BlockPos.containing(point);
            if (!player.level().getBlockState(blockPos).isAir()
                    && player.level().getBlockState(blockPos)
                    .isSolidRender(player.level(), blockPos)) {
                break;
            }

            spawnSlashParticles(player, point, direction);
            struck.addAll(TechniqueTargeting.harmableNear(player, point, 1.2D));
        }

        for (LivingEntity target : struck) {
            target.hurt(player.damageSources().playerAttack(player), (float) damage);
        }

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);

        // Always fires: the slash happens whether or not it connects, so a miss still costs Qi.
        return true;
    }

    /**
     * 장법 Divine Palm — a cone of compressed Qi that throws everything in front of you back.
     *
     * <p>Refuses when nothing is in reach, so a palm strike into empty air is refunded rather
     * than wasted.
     */
    public static boolean divinePalm(ServerPlayer player, ResourceLocation id,
                                     Technique technique, int mastery) {
        double radius = Math.max(1.0D, technique.power().radius());
        double damage = TechniqueMastery.damage(technique.power(), mastery);
        double knockback = technique.power().knockback();

        Vec3 look = player.getLookAngle().normalize();
        Vec3 centre = player.position().add(look.scale(radius * 0.5D)).add(0.0D, 1.0D, 0.0D);

        List<LivingEntity> targets = TechniqueTargeting.harmableNear(player, centre, radius);
        // Keep only what is actually in front: a palm strike is a cone, not an aura.
        targets.removeIf(target -> {
            Vec3 toTarget = target.position().subtract(player.position()).normalize();
            return look.dot(toTarget) < 0.35D;
        });

        if (targets.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("murimcultivation.technique.nothing_in_reach"), true);
            return false;
        }

        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), (float) damage);
            Vec3 away = target.position().subtract(player.position()).normalize();
            TechniqueTargeting.knockBack(target, away.scale(knockback).add(0.0D, knockback * 0.35D, 0.0D));
        }

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y, centre.z,
                    30, radius * 0.4D, 0.3D, radius * 0.4D, 0.08D);
        }
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.7F);
        return true;
    }

    /**
     * 검강 Sword Force — Qi condensed onto the blade itself. A sustained buff to damage and
     * reach rather than a strike, so it changes how you fight for its duration.
     */
    public static boolean swordForce(ServerPlayer player, ResourceLocation id,
                                     Technique technique, int mastery) {
        int duration = Math.max(1, technique.power().durationTicks());
        double bonusDamage = TechniqueMastery.damage(technique.power(), mastery);
        double bonusReach = technique.power().radius();

        TechniqueBuffs.start(player, id, duration);
        TechniqueBuffs.addModifier(player, id, Attributes.ATTACK_DAMAGE,
                bonusDamage, AttributeModifier.Operation.ADD_VALUE);
        TechniqueBuffs.addModifier(player, id, Attributes.ENTITY_INTERACTION_RANGE,
                bonusReach, AttributeModifier.Operation.ADD_VALUE);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.2D, player.getZ(),
                    40, 0.5D, 0.5D, 0.5D, 0.3D);
        }
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.8F, 0.6F);
        return true;
    }

    private static void spawnSlashParticles(ServerPlayer player, Vec3 point, Vec3 direction) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z,
                2, 0.1D, 0.1D, 0.1D, 0.02D);
    }

}
