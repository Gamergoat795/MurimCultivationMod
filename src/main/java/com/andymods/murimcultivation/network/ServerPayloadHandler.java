package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.cultivation.BreakthroughService;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.MeditationService;
import com.andymods.murimcultivation.cultivation.MeridianService;
import com.andymods.murimcultivation.cultivation.focus.FocusService;
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

            CultivationData data = CultivationService.data(player);

            // Pressing the key again mid-circulation must not start a second attempt. The progress
            // is not deducted until the sweeps resolve, so without this guard the eligibility check
            // still passes and the attempts would stack.
            if (data.pendingBreakthrough() != null) {
                player.displayClientMessage(
                        Component.translatable("murimcultivation.focus.already_circulating"), true);
                return;
            }

            BreakthroughService.Eligibility eligibility = BreakthroughService.check(player);
            if (!eligibility.isReady()) {
                player.displayClientMessage(eligibility.message(), true);
                return;
            }

            // Breaking through breaks concentration either way.
            data.setMeditating(false);
            // Phase one: circulate the Qi. FocusService calls BreakthroughService.attempt once the
            // sweeps are answered, which is also when the banked progress is finally spent.
            FocusService.beginBreakthrough(player, data);
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

    public static void handleUseSelectedTechnique(UseSelectedTechniquePayload payload,
                                                  IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // The server reads its own selection rather than trusting a slot number, so
                // cycling and casting in quick succession cannot fire the previous art.
                TechniqueService.castSelected(player);
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

    public static void handleSpendStatPoint(SpendStatPointPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            CultivationData data = CultivationService.data(player);
            if (!data.systemProgress().spend(payload.stat(), payload.points())) {
                player.displayClientMessage(
                        Component.translatable("murimcultivation.stat.cannot_spend"), true);
                return;
            }
            // Stats feed attributes, so the single recalculation path has to run.
            CultivationService.applyAttributes(player);
            CultivationService.syncToClient(player);
        });
    }

    public static void handleEquipTitle(EquipTitlePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            CultivationData data = CultivationService.data(player);
            if (!data.systemProgress().equipTitle(payload.title().orElse(null))) {
                player.displayClientMessage(
                        Component.translatable("murimcultivation.title.not_owned"), true);
                return;
            }
            CultivationService.applyAttributes(player);
            CultivationService.syncToClient(player);
        });
    }

    /**
     * The player says they answered a breath-rhythm prompt at a given point in the sweep.
     *
     * <p>Everything that decides whether that counts happens in {@code FocusService.answer} — the
     * prompt id must match the one outstanding, the claimed position is checked against how long
     * the server actually waited, and the verdict is the server's. A client can lie about the
     * number in this packet; it cannot make the server believe it.
     */
    public static void handleFocusResponse(FocusResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                FocusService.answer(player, payload.promptId(), payload.position());
            }
        });
    }

    private ServerPayloadHandler() {
    }
}
