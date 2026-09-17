package com.andymods.murimcultivation.technique.behaviour;

import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.technique.TechniqueBuffs;
import com.andymods.murimcultivation.technique.TechniqueMastery;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** 철신공 Iron Body and 내공치료 Internal Healing: the arts turned inward. */
public final class SustainBehaviours {

    private SustainBehaviours() {
    }

    /**
     * 철신공 Iron Body — flood your own frame with Qi until blades struggle to bite.
     *
     * <p>Costs Qi every tick it is held, which {@link TechniqueBuffs} drains and which ends the
     * technique when it runs dry. Without upkeep a defensive buff is strictly better than a
     * timed one, and there is no reason ever to turn it off.
     */
    public static boolean ironBody(ServerPlayer player, ResourceLocation id,
                                  Technique technique, int mastery) {
        int duration = Math.max(1, technique.power().durationTicks());
        int amplifier = technique.power().amplifier();

        TechniqueBuffs.start(player, id, duration);
        player.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE, duration, amplifier, false, false, true));
        // Standing your ground is part of the art, so it also resists being moved.
        TechniqueBuffs.addModifier(player, id, Attributes.KNOCKBACK_RESISTANCE,
                0.4D + mastery / 250.0D, AttributeModifier.Operation.ADD_VALUE);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    30, 0.4D, 0.7D, 0.4D, 0.05D);
        }
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.6F);
        return true;
    }

    /**
     * 내공치료 Internal Healing — circulate Qi inward to knit yourself back together.
     *
     * <p>Refuses at full health, which is what makes the refund path in the cast pipeline worth
     * having: casting it topped up should cost nothing.
     */
    public static boolean internalHealing(ServerPlayer player, ResourceLocation id,
                                          Technique technique, int mastery) {
        if (player.getHealth() >= player.getMaxHealth()) {
            player.displayClientMessage(
                    Component.translatable("murimcultivation.technique.already_healthy"), true);
            return false;
        }

        double amount = technique.power().healAmount() + TechniqueMastery.damage(technique.power(), mastery);
        player.heal((float) amount);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART,
                    player.getX(), player.getY() + 1.4D, player.getZ(),
                    5, 0.3D, 0.2D, 0.3D, 0.0D);
        }
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.6F);
        return true;
    }
}
