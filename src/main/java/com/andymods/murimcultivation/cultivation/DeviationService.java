package com.andymods.murimcultivation.cultivation;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;

/**
 * Qi Deviation: what it costs to force a breakthrough your foundation could not carry.
 *
 * <p>This is the system that makes a breakthrough a decision. Without a real penalty, the
 * correct play is always to mash the attempt key the instant it lights up. With one, a
 * cultivator weighs their odds — and the debuffs are deliberately the kind you feel
 * (weakness, sluggishness, no cultivating at all) rather than a number ticking down in a menu.
 */
public final class DeviationService {

    private DeviationService() {
    }

    /**
     * Inflicts a deviation: applies the state, burns progress, hands out the debuffs, and for
     * the worst tier slams a meridian shut.
     *
     * @return a description of what was lost, for messaging
     */
    public static Outcome inflict(ServerPlayer player, DeviationSeverity severity) {
        if (!severity.isActive()) {
            return new Outcome(severity, 0.0D, null);
        }

        CultivationData data = CultivationService.data(player);
        data.applyDeviation(severity);

        // Scrambled channels cannot gather Qi.
        MeditationService.stop(player, MeditationService.Interruption.DEVIATION);

        double progressLost = data.progress() * severity.progressLossFraction();
        data.setProgress(data.progress() - progressLost);

        Meridian closed = null;
        if (severity.closesMeridian()) {
            closed = slamShutARandomMeridian(player, data);
        }

        applyDebuffs(player, severity);
        playEffects(player, severity);

        player.sendSystemMessage(Component.translatable("murimcultivation.deviation.suffered",
                Component.translatable(severity.translationKey())));
        CultivationService.syncToClient(player);

        return new Outcome(severity, progressLost, closed);
    }

    /** Clears a deviation early, as a recovery pill or a healer will. */
    public static boolean cure(ServerPlayer player) {
        CultivationData data = CultivationService.data(player);
        if (!data.deviation().isActive()) {
            return false;
        }

        data.clearDeviation();
        removeDebuffs(player);
        player.displayClientMessage(
                Component.translatable("murimcultivation.message.deviation_cleared"), true);
        CultivationService.syncToClient(player);
        return true;
    }

    /**
     * Picks a meridian to force shut, preferring a primary one — losing an extraordinary vessel
     * would undo far more work than a single failed attempt should.
     */
    private static Meridian slamShutARandomMeridian(ServerPlayer player, CultivationData data) {
        List<Meridian> primary = new ArrayList<>();
        List<Meridian> extraordinary = new ArrayList<>();
        for (Meridian meridian : data.openMeridians()) {
            (meridian.isExtraordinary() ? extraordinary : primary).add(meridian);
        }

        List<Meridian> candidates = primary.isEmpty() ? extraordinary : primary;
        if (candidates.isEmpty()) {
            return null;
        }

        Meridian victim = candidates.get(player.getRandom().nextInt(candidates.size()));
        data.closeMeridian(victim);
        // The network just narrowed, so the Qi ceiling dropped with it.
        CultivationService.clampQiToCapacity(player, data);

        player.sendSystemMessage(Component.translatable("murimcultivation.deviation.meridian_shattered",
                Component.translatable(victim.translationKey())));
        return victim;
    }

    /** Vanilla effects, chosen so the severity is legible without reading a tooltip. */
    private static void applyDebuffs(ServerPlayer player, DeviationSeverity severity) {
        int duration = severity.durationTicks();

        switch (severity) {
            case MINOR_BLOCKAGE -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0));
            }
            case REVERSE_FLOW -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 1));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, 0));
            }
            case SHATTERED_MERIDIAN -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, 1));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, Math.min(duration, 600), 0));
            }
            default -> {
                // NONE: nothing to apply.
            }
        }
    }

    private static void removeDebuffs(ServerPlayer player) {
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.DIG_SLOWDOWN);
        player.removeEffect(MobEffects.CONFUSION);
    }

    private static void playEffects(ServerPlayer player, DeviationSeverity severity) {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.6F);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    20 * severity.rank(), 0.4D, 0.5D, 0.4D, 0.02D);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    5 * severity.rank(), 0.3D, 0.4D, 0.3D, 0.1D);
        }
    }

    /** What a deviation actually cost, so callers can report specifics. */
    public record Outcome(DeviationSeverity severity, double progressLost, Meridian meridianClosed) {

        public boolean shatteredAMeridian() {
            return meridianClosed != null;
        }
    }
}
