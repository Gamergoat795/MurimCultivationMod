package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.cultivation.BreakthroughService;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.MeditationService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handling of client intent. Every check lives here rather than on the client,
 * so a modified or lagging client cannot meditate without having awakened or break through
 * without having earned it.
 */
public final class ServerPayloadHandler {

    public static void handleToggleMeditation(ToggleMeditationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            CultivationData data = CultivationService.data(player);
            if (data.isMeditating()) {
                MeditationService.stop(player, MeditationService.Interruption.MANUAL);
                return;
            }

            // Every precondition is decided here, not on the client: awakening, deviation,
            // being airborne, in liquid or mounted all refuse with a specific reason.
            MeditationService.StartResult result = MeditationService.start(player);
            if (!result.started()) {
                player.displayClientMessage(result.message(), true);
            }
        });
    }

    public static void handleAttemptBreakthrough(AttemptBreakthroughPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            BreakthroughService.Eligibility eligibility = BreakthroughService.check(player);
            if (!eligibility.isReady()) {
                player.displayClientMessage(eligibility.message(), true);
                return;
            }

            // Breaking through breaks concentration either way.
            CultivationService.data(player).setMeditating(false);
            BreakthroughService.attempt(player);
        });
    }

    private ServerPayloadHandler() {
    }
}
