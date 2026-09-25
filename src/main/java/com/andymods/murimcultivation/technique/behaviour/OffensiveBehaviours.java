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
    public static boolean swordQi(LivingEntity caster, ResourceLocation id,
                                  Technique technique, int mastery) {
        double range = Math.max(1.0D, technique.power().range());
        double damage = TechniqueMastery.damage(technique.power(), mastery);

        Vec3 origin = caster.getEyePosition();
        Vec3 direction = caster.getLookAngle().normalize();

        Set<LivingEntity> struck = new HashSet<>();
        int steps = (int) Math.ceil(range * 2);

        for (int step = 0; step <= steps; step++) {
            Vec3 point = origin.add(direction.scale(range * step / steps));

            // Stop at the first solid block so Sword Qi does not cut through walls.
            BlockPos blockPos = BlockPos.containing(point);
            if (!caster.level().getBlockState(blockPos).isAir()
                    && caster.level().getBlockState(blockPos)
                    .isSolidRender(caster.level(), blockPos)) {
                break;
            }

            spawnSlashParticles(caster, point, direction);
            struck.addAll(TechniqueTargeting.harmableNear(caster, point, 1.2D));
        }

        for (LivingEntity target : struck) {
            target.hurt(TechniqueTargeting.damageFrom(caster), (float) damage);
        }

        caster.level().playSound(null, caster.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, caster.getSoundSource(), 1.0F, 0.8F);

        // Always fires: the slash happens whether or not it connects, so a miss still costs Qi.
        return true;
    }

    /**
     * 장법 Divine Palm — a cone of compressed Qi that throws everything in front of you back.
     *
     * <p>Refuses when nothing is in reach, so a palm strike into empty air is refunded rather
     * than wasted.
     */
    public static boolean divinePalm(LivingEntity caster, ResourceLocation id,
                                     Technique technique, int mastery) {
        double radius = Math.max(1.0D, technique.power().radius());
        double damage = TechniqueMastery.damage(technique.power(), mastery);
        double knockback = technique.power().knockback();

        Vec3 look = caster.getLookAngle().normalize();
        Vec3 centre = caster.position().add(look.scale(radius * 0.5D)).add(0.0D, 1.0D, 0.0D);

        List<LivingEntity> targets = TechniqueTargeting.harmableNear(caster, centre, radius);
        // Keep only what is actually in front: a palm strike is a cone, not an aura.
        targets.removeIf(target -> {
            Vec3 toTarget = target.position().subtract(caster.position()).normalize();
            return look.dot(toTarget) < 0.35D;
        });

        if (targets.isEmpty()) {
            TechniqueTargeting.tellCaster(caster,
                    Component.translatable("murimcultivation.technique.nothing_in_reach"));
            return false;
        }

        for (LivingEntity target : targets) {
            target.hurt(TechniqueTargeting.damageFrom(caster), (float) damage);
            Vec3 away = target.position().subtract(caster.position()).normalize();
            TechniqueTargeting.knockBack(target, away.scale(knockback).add(0.0D, knockback * 0.35D, 0.0D));
        }

        if (caster.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y, centre.z,
                    30, radius * 0.4D, 0.3D, radius * 0.4D, 0.08D);
        }
        caster.level().playSound(null, caster.blockPosition(),
                SoundEvents.PLAYER_ATTACK_STRONG, caster.getSoundSource(), 1.0F, 0.7F);
        return true;
    }

    /**
     * 검강 Sword Force — Qi condensed onto the blade itself. A sustained buff to damage and
     * reach rather than a strike, so it changes how you fight for its duration.
     */
    public static boolean swordForce(LivingEntity caster, ResourceLocation id,
                                     Technique technique, int mastery) {
        // Sustained arts keep their state and Qi upkeep in the caster's CultivationData, which
        // only a player carries. Refused for anyone else, and not offered to NPCs at all.
        if (!(caster instanceof ServerPlayer player)) {
            return false;
        }
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

    private static void spawnSlashParticles(LivingEntity caster, Vec3 point, Vec3 direction) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z,
                2, 0.1D, 0.1D, 0.1D, 0.02D);
    }

}
