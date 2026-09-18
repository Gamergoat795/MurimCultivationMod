package com.andymods.murimcultivation.cultivation;

import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.cultivation.focus.FocusService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Meditation: the act of sitting still to convert time and place into cultivation.
 *
 * <p><strong>Meditation does not immobilise the player.</strong> The previous implementation
 * froze movement and forced {@code setOnGround(true)} every tick, which let a player meditate
 * in mid-air to cancel fall damage and hover. Here, moving simply <em>interrupts</em> the
 * session. That removes the exploit entirely rather than patching it, and it is better design:
 * you sit still because you want the gains, not because the game has taken the controls away.
 *
 * <p>The ramp is the other half of that. Gains climb the longer a session is sustained, so an
 * unbroken stretch is worth more than the same minutes chopped up — which is what makes
 * choosing a safe, Qi-rich spot to settle into an actual decision.
 */
public final class MeditationService {

    private static final double TICKS_PER_SECOND = 20.0D;

    private MeditationService() {
    }

    /** Why a meditation session ended, so the player can be told something useful. */
    public enum Interruption {
        MANUAL("murimcultivation.meditation.stopped"),
        MOVED("murimcultivation.meditation.interrupted.moved"),
        DAMAGED("murimcultivation.meditation.interrupted.damaged"),
        ATTACKED("murimcultivation.meditation.interrupted.attacked"),
        AIRBORNE("murimcultivation.meditation.interrupted.airborne"),
        MOUNTED("murimcultivation.meditation.interrupted.mounted"),
        DEVIATION("murimcultivation.meditation.interrupted.deviation");

        private final String translationKey;

        Interruption(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component message() {
            return Component.translatable(translationKey);
        }
    }

    /** Why a player cannot begin meditating, or that they may. */
    public enum StartResult {
        STARTED(null),
        NOT_AWAKENED("murimcultivation.message.not_awakened"),
        SUFFERING_DEVIATION("murimcultivation.message.deviation_blocks_meditation"),
        NOT_ON_GROUND("murimcultivation.meditation.refused.not_on_ground"),
        IN_LIQUID("murimcultivation.meditation.refused.in_liquid"),
        MOUNTED("murimcultivation.meditation.refused.mounted");

        private final String translationKey;

        StartResult(String translationKey) {
            this.translationKey = translationKey;
        }

        public boolean started() {
            return this == STARTED;
        }

        public Component message() {
            return translationKey == null ? Component.empty() : Component.translatable(translationKey);
        }
    }

    /** Whether the player's situation permits settling into meditation. */
    public static StartResult canStart(ServerPlayer player) {
        CultivationData data = CultivationService.data(player);
        if (!data.isAwakened()) {
            return StartResult.NOT_AWAKENED;
        }
        if (data.deviation().isActive()) {
            return StartResult.SUFFERING_DEVIATION;
        }
        if (player.isPassenger()) {
            return StartResult.MOUNTED;
        }
        // No cultivating in mid-air. This is a start condition, not an immobilisation:
        // the player is never held in place, they just cannot begin while falling.
        if (!player.onGround()) {
            return StartResult.NOT_ON_GROUND;
        }
        if (player.isInWater() || player.isInLava()) {
            return StartResult.IN_LIQUID;
        }
        return StartResult.STARTED;
    }

    /** Begins a session, recording where the player settled so movement can be detected. */
    public static StartResult start(ServerPlayer player) {
        StartResult result = canStart(player);
        if (!result.started()) {
            return result;
        }

        CultivationData data = CultivationService.data(player);
        data.setMeditating(true);
        data.setMeditationAnchor(player.position());
        // Schedule the first breath-rhythm prompt. Without this the gap would still be zero from
        // resetFocus() and a prompt would fire on the very first tick of the session.
        FocusService.scheduleNext(player, data);

        player.displayClientMessage(Component.translatable("murimcultivation.meditation.started"), true);
        announceLocationQuality(player);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, 0.6F);
        CultivationService.syncToClient(player);
        return result;
    }

    /** Ends a session. Silent when the player was not meditating, so callers can be careless. */
    public static void stop(ServerPlayer player, Interruption reason) {
        CultivationData data = CultivationService.data(player);
        if (!data.isMeditating()) {
            return;
        }

        data.setMeditating(false);
        data.clearMeditationAnchor();
        player.displayClientMessage(reason.message(), true);
        CultivationService.syncToClient(player);
    }

    /**
     * Called each tick for a meditating player. Returns false if the session was interrupted,
     * which is how movement, mounting and leaving the ground end a session without any of them
     * needing to know about each other.
     */
    public static boolean tickAndCheckInterruptions(ServerPlayer player) {
        CultivationData data = CultivationService.data(player);
        if (!data.isMeditating()) {
            return false;
        }

        if (player.isPassenger()) {
            stop(player, Interruption.MOUNTED);
            return false;
        }

        // The ground can disappear from under a meditating player — someone mines the block,
        // or the block falls. Losing your seat ends the session.
        if (!player.onGround()) {
            stop(player, Interruption.AIRBORNE);
            return false;
        }

        // Drifting off the anchor point ends the session. A small tolerance keeps tiny
        // server-side position corrections from cancelling a legitimate sit-down.
        Vec3 anchor = data.meditationAnchor();
        if (anchor != null) {
            double tolerance = MurimConfig.meditationMoveTolerance();
            if (player.position().distanceToSqr(anchor) > tolerance * tolerance) {
                stop(player, Interruption.MOVED);
                return false;
            }
        }

        data.incrementMeditationTicks();
        emitAmbientEffects(player, data);
        return true;
    }

    /**
     * Converts a slice of sustained meditation into cultivation progress and foundation purity.
     *
     * @param seconds real seconds this slice represents
     * @return the progress gained, so the caller can report or react to it
     */
    public static double cultivate(ServerPlayer player, CultivationData data, double seconds) {
        double density = QiDensity.multiplierFor(player);
        double focus = data.focus();
        double gained = MurimConfig.meditationProgressPerSecond()
                * rampMultiplier(data)
                * density
                * CultivationService.cultivationRateMultiplier(data)
                * focus
                * seconds;

        data.addProgress(gained);
        // Purity is not scaled by the ramp: a clean foundation comes from patience, and
        // should not be something a single very long session can rush. It *is* scaled by focus,
        // which is a different axis — the ramp measures how long you have sat, focus measures
        // whether you are actually there. An empty chair should not quietly refine a foundation.
        data.addPurity(MurimConfig.meditationPurityPerMinute() * density * focus * (seconds / 60.0D));
        return gained;
    }

    /** Scales from 1.0 at the start of a session up to the configured maximum at full ramp. */
    public static double rampMultiplier(CultivationData data) {
        double rampTicks = MurimConfig.meditationRampSeconds() * TICKS_PER_SECOND;
        if (rampTicks <= 0.0D) {
            return MurimConfig.meditationRampMultiplier();
        }
        double fraction = Math.min(1.0D, data.meditationTicks() / rampTicks);
        return 1.0D + (MurimConfig.meditationRampMultiplier() - 1.0D) * fraction;
    }

    /** Swirling particles that intensify as the session ramps up, so progress is visible. */
    private static void emitAmbientEffects(ServerPlayer player, CultivationData data) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Once every half second is enough to read as a steady aura without flooding clients.
        if (player.tickCount % 10 != 0) {
            return;
        }

        double ramp = rampMultiplier(data);
        int count = 1 + (int) Math.round(ramp);
        double radius = 0.6D;
        double angle = (player.tickCount % 80) / 80.0D * Math.PI * 2.0D;

        for (int i = 0; i < count; i++) {
            double offset = angle + (i * Math.PI * 2.0D / count);
            level.sendParticles(ParticleTypes.END_ROD,
                    player.getX() + Math.cos(offset) * radius,
                    player.getY() + 0.4D,
                    player.getZ() + Math.sin(offset) * radius,
                    1, 0.0D, 0.02D, 0.0D, 0.0D);
        }
    }

    private static void announceLocationQuality(ServerPlayer player) {
        double density = QiDensity.multiplierFor(player);
        QiDensity.Quality quality = QiDensity.qualityOf(density);
        player.sendSystemMessage(Component.translatable("murimcultivation.meditation.location_quality",
                Component.translatable(quality.translationKey())));
    }
}
