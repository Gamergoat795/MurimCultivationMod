package com.andymods.murimcultivation.event;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.Realm;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The per-player cultivation heartbeat: Qi regeneration, meditation gains, and the Qi Deviation
 * countdown.
 *
 * <p>All state here is read from the ticking player's own attachment, and the tick phase comes
 * from that player's own {@code tickCount}. Holding a counter field on the handler instead makes
 * the accumulator shared across every player on the server, so gains scale with the player count
 * and land on whoever happens to tick last.
 */
@EventBusSubscriber(modid = MurimCultivationMod.MODID)
public final class CultivationTickEvents {

    private static final double TICKS_PER_SECOND = 20.0D;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CultivationData data = CultivationService.data(player);
        if (!data.isAwakened()) {
            return;
        }

        // Qi Deviation is counted down every tick so its duration is honest.
        if (data.deviation().isActive()) {
            if (data.tickDeviation()) {
                player.displayClientMessage(
                        Component.translatable("murimcultivation.message.deviation_cleared"), true);
                CultivationService.syncToClient(player);
            }
            // A deviated cultivator cannot gather Qi; the channels are not cooperating.
            if (data.isMeditating()) {
                data.setMeditating(false);
            }
            return;
        }

        if (data.isMeditating()) {
            data.incrementMeditationTicks();
        }

        int interval = MurimConfig.cultivationTickInterval();
        if (player.tickCount % interval != 0) {
            return;
        }

        double seconds = interval / TICKS_PER_SECOND;
        Registry<Realm> registry = CultivationService.realmRegistry(player);

        boolean changed = regenerateQi(player, data, registry, seconds);
        if (data.isMeditating()) {
            cultivate(data, seconds);
            changed = true;
        }

        if (changed) {
            CultivationService.advanceSubstages(player);
            CultivationService.syncValuesToClient(player);
        }
    }

    /** Passive Qi regeneration. Returns whether anything actually changed. */
    private static boolean regenerateQi(ServerPlayer player, CultivationData data,
                                        Registry<Realm> registry, double seconds) {
        double capacity = CultivationService.qiCapacity(registry, data);
        if (data.qi() >= capacity) {
            return false;
        }
        data.addQi(CultivationService.qiRegenPerSecond(registry, data) * seconds, capacity);
        return true;
    }

    /**
     * Converts sustained meditation into cultivation progress and foundation purity.
     *
     * <p>The ramp is the point: an unbroken session is worth more per second than the same
     * total time in fragments, which is what makes sitting down to cultivate feel like a
     * deliberate act rather than an idle tax.
     */
    private static void cultivate(CultivationData data, double seconds) {
        data.addProgress(MurimConfig.meditationProgressPerSecond() * rampMultiplier(data) * seconds);
        data.addPurity(MurimConfig.meditationPurityPerMinute() * (seconds / 60.0D));
    }

    /** Scales from 1.0 at the start of a session up to the configured maximum at full ramp. */
    private static double rampMultiplier(CultivationData data) {
        double rampTicks = MurimConfig.meditationRampSeconds() * TICKS_PER_SECOND;
        double fraction = Math.min(1.0D, data.meditationTicks() / rampTicks);
        return 1.0D + (MurimConfig.meditationRampMultiplier() - 1.0D) * fraction;
    }

    private CultivationTickEvents() {
    }
}
