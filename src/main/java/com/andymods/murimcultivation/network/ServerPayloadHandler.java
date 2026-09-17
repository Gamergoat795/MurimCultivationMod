package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.cultivation.BreakthroughService;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.MeditationService;
import com.andymods.murimcultivation.cultivation.MeridianService;
import com.andymods.murimcultivation.item.MartialManualItem;
import com.andymods.murimcultivation.technique.TechniqueService;
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

    public static void handleOpenMeridian(OpenMeridianPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // MeridianService owns the cost, the purity gate and the deviation roll.
                MeridianService.open(player, payload.meridian());
            }
        });
    }

    public static void handleUseTechnique(UseTechniquePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // TechniqueService re-validates every gate; the slot index is all the client
                // gets to decide.
                TechniqueService.castSlot(player, payload.slot());
            }
        });
    }

    public static void handleCycleTechnique(CycleTechniquePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            CultivationData data = CultivationService.data(player);
            int slot = data.cycleSelectedSlot();
            data.techniqueInSlot(slot).ifPresentOrElse(
                    id -> player.displayClientMessage(Component.translatable(
                            "murimcultivation.technique.selected",
                            Component.translatable(MartialManualItem.translationKeyFor(id))), true),
                    () -> player.displayClientMessage(
                            Component.translatable("murimcultivation.technique.none_learned"), true));
            CultivationService.syncToClient(player);
        });
    }

    public static void handleSetLoadout(SetLoadoutPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // setLoadout keeps only techniques the player has actually learned.
            CultivationService.data(player).setLoadout(payload.techniques());
            CultivationService.syncToClient(player);
        });
    }

    private ServerPayloadHandler() {
    }
}
