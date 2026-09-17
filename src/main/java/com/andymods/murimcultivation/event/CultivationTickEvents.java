package com.andymods.murimcultivation.event;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.EnlightenmentService;
import com.andymods.murimcultivation.cultivation.MeditationService;
import com.andymods.murimcultivation.cultivation.Realm;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The per-player cultivation heartbeat: Qi regeneration, meditation gains, insight rolls, and
 * the Qi Deviation countdown.
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

        if (tickDeviation(player, data)) {
            return;
        }

        // Interruption checks run every tick so movement is caught promptly, not up to a
        // second later when the slower cultivation interval next comes round.
        if (data.isMeditating() && !MeditationService.tickAndCheckInterruptions(player)) {
            return;
        }

        int interval = MurimConfig.cultivationTickInterval();
        if (player.tickCount % interval != 0) {
            return;
        }

        double seconds = interval / TICKS_PER_SECOND;
        Registry<Realm> registry = CultivationService.realmRegistry(player);

        boolean changed = regenerateQi(player, data, registry, seconds);

        if (data.isMeditating()) {
            MeditationService.cultivate(player, data, seconds);
            EnlightenmentService.rollDuringMeditation(player, data, seconds);
            changed = true;
        }

        if (changed) {
            CultivationService.advanceSubstages(player);
            CultivationService.syncValuesToClient(player);
        }
    }

    /**
     * Counts down an active Qi Deviation.
     *
     * @return true if the player is deviated and should skip cultivating entirely
     */
    private static boolean tickDeviation(ServerPlayer player, CultivationData data) {
        if (!data.deviation().isActive()) {
            return false;
        }

        if (data.tickDeviation()) {
            player.displayClientMessage(
                    Component.translatable("murimcultivation.message.deviation_cleared"), true);
            CultivationService.syncToClient(player);
        }

        // Scrambled channels will not gather Qi.
        if (data.isMeditating()) {
            MeditationService.stop(player, MeditationService.Interruption.DEVIATION);
        }
        return true;
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

    private CultivationTickEvents() {
    }
}
