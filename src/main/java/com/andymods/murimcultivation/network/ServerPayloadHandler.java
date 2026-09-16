package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.cultivation.BreakthroughService;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import net.minecraft.network.chat.Component;
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
            if (!data.isAwakened()) {
                player.displayClientMessage(Component.translatable("murimcultivation.message.not_awakened"), true);
                return;
            }
            if (data.deviation().isActive()) {
                player.displayClientMessage(
                        Component.translatable("murimcultivation.message.deviation_blocks_meditation"), true);
                return;
            }

            boolean nowMeditating = !data.isMeditating();
            data.setMeditating(nowMeditating);
            player.displayClientMessage(Component.translatable(nowMeditating
                    ? "murimcultivation.message.meditation_start"
                    : "murimcultivation.message.meditation_stop"), true);

            CultivationService.syncToClient(player);
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
