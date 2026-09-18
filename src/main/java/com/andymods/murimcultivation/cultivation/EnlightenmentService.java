package com.andymods.murimcultivation.cultivation;

import com.andymods.murimcultivation.config.MurimConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Sudden insight — the moment a cultivator understands something and lurches forward.
 *
 * <p>This is the beat the genre runs on and the reason cultivation is not just a progress bar.
 * It fires on its own schedule, so a long session has a story to it rather than a constant
 * trickle, and it fires hardest for players who did the unglamorous work: a clean foundation
 * and a good location both raise the odds.
 *
 * <p>It can also strike in combat, on the edge of death — the classic breakthrough-mid-fight
 * moment. That path is deliberately rarer and worth more.
 */
public final class EnlightenmentService {

    private EnlightenmentService() {
    }

    public enum Source {
        /** Quiet realisation during meditation. */
        MEDITATION("murimcultivation.enlightenment.meditation"),
        /** Understanding forced out by nearly dying. */
        NEAR_DEATH("murimcultivation.enlightenment.near_death");

        private final String translationKey;

        Source(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component message() {
            return Component.translatable(translationKey);
        }
    }

    /**
     * Rolls for insight during a slice of meditation.
     *
     * <p>The chance is per second and scaled by purity and ambient Qi, so the player who sought
     * out a spirit vein and refined their foundation is genuinely more likely to have a
     * breakthrough moment than one grinding in a dirt hut.
     *
     * @return the progress granted, or 0 if no insight came
     */
    public static double rollDuringMeditation(ServerPlayer player, CultivationData data, double seconds) {
        double purityFactor = 0.5D + (data.purity() / CultivationData.MAX_PURITY);
        double density = QiDensity.multiplierFor(player);
        // Scaled by focus so insight cannot be farmed by an empty chair. Without this, the one
        // reward left to an absent player would be the rarest and largest one in the mod.
        double chance = MurimConfig.enlightenmentChancePerSecond()
                * seconds * purityFactor * density * data.focus();

        if (player.getRandom().nextDouble() >= chance) {
            return 0.0D;
        }
        return grant(player, data, Source.MEDITATION, MurimConfig.enlightenmentProgressSeconds());
    }

    /**
     * Rolls for insight after surviving a blow that nearly killed the player.
     *
     * @return the progress granted, or 0 if no insight came
     */
    public static double rollAfterNearDeath(ServerPlayer player, CultivationData data) {
        if (player.getRandom().nextDouble() >= MurimConfig.enlightenmentNearDeathChance()) {
            return 0.0D;
        }
        // A brush with death teaches more than an hour of sitting still.
        return grant(player, data, Source.NEAR_DEATH, MurimConfig.enlightenmentProgressSeconds() * 3.0D);
    }

    /**
     * Applies an insight.
     *
     * <p>The reward is expressed in "seconds of meditation worth" rather than a flat number, so
     * it stays meaningful at every realm instead of being enormous early and trivial later.
     */
    private static double grant(ServerPlayer player, CultivationData data,
                                Source source, double equivalentSeconds) {
        double gained = MurimConfig.meditationProgressPerSecond() * equivalentSeconds;
        data.addProgress(gained);
        data.addPurity(MurimConfig.enlightenmentPurityGain());

        player.sendSystemMessage(source.message());
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 1.2D, player.getZ(),
                    40, 0.5D, 0.6D, 0.5D, 0.4D);
        }
        return gained;
    }
}
