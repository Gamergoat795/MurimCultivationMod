package com.andymods.murimcultivation.technique.behaviour;

import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.technique.TechniqueBuffs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** 무영보 Shadowless Step, 경공 Qinggong and 수상보행 Water Walking: the arts of not being there. */
public final class MovementBehaviours {

    private MovementBehaviours() {
    }

    /**
     * 무영보 Shadowless Step — a hard dash in the direction you are looking, with a moment of
     * invulnerability on the way through.
     *
     * <p>The i-frames are the point: this is an escape, so it has to actually get you out of
     * something rather than merely moving you while you keep taking hits.
     */
    public static boolean shadowlessStep(ServerPlayer player, ResourceLocation id,
                                        Technique technique, int mastery) {
        double distance = Math.max(1.0D, technique.power().range());
        Vec3 look = player.getLookAngle().normalize();

        // Flatten the vertical component so a dash is a dash, not a launch or a dive.
        Vec3 impulse = new Vec3(look.x, Math.max(-0.1D, Math.min(0.35D, look.y)), look.z)
                .normalize()
                .scale(distance * 0.18D);

        Vec3 from = player.position();
        player.setDeltaMovement(impulse);
        TechniqueTargeting.syncSelfMotion(player);

        // A brief window of immunity, in ticks, from the technique's own duration.
        player.invulnerableTime = Math.max(player.invulnerableTime, technique.power().durationTicks());
        // Reset fall distance so dashing off a ledge is not punished by the landing.
        player.resetFallDistance();

        if (player.level() instanceof ServerLevel level) {
            // An after-image trail along the path taken.
            for (int i = 0; i < 12; i++) {
                Vec3 point = from.add(impulse.scale(i / 12.0D * 5.0D));
                level.sendParticles(ParticleTypes.SMOKE, point.x, point.y + 1.0D, point.z,
                        2, 0.1D, 0.2D, 0.1D, 0.01D);
            }
        }
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4F, 1.8F);
        return true;
    }

    /**
     * 경공 Qinggong — the light-body art. Leaping across rooftops and drifting down from
     * heights, expressed through vanilla effects so it composes with everything else.
     */
    public static boolean qinggong(ServerPlayer player, ResourceLocation id,
                                   Technique technique, int mastery) {
        int duration = Math.max(1, technique.power().durationTicks());
        int amplifier = technique.power().amplifier();

        TechniqueBuffs.start(player, id, duration);
        // Hidden icons and no ambient particles: this should read as the player's own skill,
        // not as a potion they drank.
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, duration, amplifier, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration, 0, false, false, true));

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.1D, player.getZ(),
                    20, 0.3D, 0.1D, 0.3D, 0.02D);
        }
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.5F, 1.4F);
        return true;
    }

    /**
     * 수상보행 Water Walking — cross a river without swimming it.
     *
     * <p>Held by {@link #tickWaterWalking} rather than by changing collision, because a real
     * collision change would also stop boats, fishing and anything else that expects water to
     * behave like water. Sneaking lets you sink deliberately, which matters: without an escape
     * hatch, a buff that keeps you on the surface can trap you out of your own base.
     */
    public static boolean waterWalking(ServerPlayer player, ResourceLocation id,
                                       Technique technique, int mastery) {
        TechniqueBuffs.start(player, id, Math.max(1, technique.power().durationTicks()));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 0.9F);
        return true;
    }

    /**
     * Keeps a water-walking cultivator on the surface. Called once per tick while the technique
     * is active.
     */
    public static void tickWaterWalking(ServerPlayer player) {
        if (player.isShiftKeyDown()) {
            return;
        }

        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.1D, player.getZ());
        boolean overLiquid = !player.level().getFluidState(below).isEmpty();
        if (!overLiquid || player.isInLava()) {
            return;
        }

        // Cancel the sink and hold the player at the surface. Only downward motion is touched,
        // so jumping and swimming upward still work.
        //
        // This does call setOnGround(true), which meditation was deliberately stripped of
        // because it let a player hover mid-air and cancel fall damage for free and forever.
        // The difference is that every one of those conditions is absent here: it only applies
        // while standing over a liquid, only while a Qi-costing technique with a fixed duration
        // is running, and sneaking cancels it. Without it the player counts as airborne and
        // loses ground friction and control, which makes walking on water feel broken.
        Vec3 motion = player.getDeltaMovement();
        if (motion.y < 0.0D) {
            player.setDeltaMovement(motion.x, 0.0D, motion.z);
            player.resetFallDistance();
            player.setOnGround(true);
        }

        if (player.level() instanceof ServerLevel level && player.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.SPLASH,
                    player.getX(), player.getY(), player.getZ(),
                    2, 0.2D, 0.0D, 0.2D, 0.0D);
        }
    }
}
