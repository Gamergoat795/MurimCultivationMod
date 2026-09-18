package com.andymods.murimcultivation.client;

import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.network.FocusPromptPayload;
import com.andymods.murimcultivation.network.SyncCultivationPayload;
import com.andymods.murimcultivation.client.hud.FocusPromptLayer;
import com.andymods.murimcultivation.client.hud.SystemToastLayer;
import com.andymods.murimcultivation.network.SyncCultivationValuesPayload;
import com.andymods.murimcultivation.network.SystemNotificationPayload;
import com.andymods.murimcultivation.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side application of synced cultivation state.
 *
 * <p>This class touches {@code net.minecraft.client} and therefore must never be loaded on a
 * dedicated server. {@code ModPayloads} wraps every reference to it in a lambda for exactly
 * that reason — see the comment there before changing how these are registered.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPayloadHandler {

    public static void handleSyncCultivation(SyncCultivationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            // Copy into the existing attachment rather than replacing it, so anything already
            // holding a reference to the client's data object keeps seeing current values.
            CultivationData local = player.getData(ModAttachments.CULTIVATION);
            local.copyFrom(payload.data());
            local.setMeditating(payload.meditating());
            // Transient, so it is not in the codec and copyFrom does not carry it. Without this
            // the client's selection sat at zero forever and the loadout bar's highlight never
            // moved, which is most of why the cycle key read as doing nothing.
            local.setSelectedSlot(payload.selectedSlot());

            // A full resync means login, respawn or a dimension change. Anything still on screen
            // belongs to a session that no longer exists, and a stale bar would invite an answer
            // the server has already forgotten about. This is also SystemToastLayer.clear()'s
            // first caller — its javadoc has promised exactly this behaviour since M4.
            FocusPromptLayer.clear();
            SystemToastLayer.clear();
        });
    }

    public static void handleSyncValues(SyncCultivationValuesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            CultivationData local = player.getData(ModAttachments.CULTIVATION);
            // Qi is clamped against the client's own derived capacity, which is computed from
            // the realm and meridians it already has — there is no separately-synced maximum
            // that could be stale relative to this value.
            local.setQi(payload.qi(), Double.MAX_VALUE);
            local.setProgress(payload.progress());
            local.setPurity(payload.purity());
            local.setMeditating(payload.meditating());

            // Deliberately does NOT clear the prompt bar. A breakthrough circulates with
            // meditation already stopped, and this payload fires about once a second because Qi
            // regen marks the tick as changed — so clearing here wiped the breakthrough prompt
            // within a second of it appearing, every time. The bar retires itself once it can no
            // longer be answered (FocusPromptLayer.LAPSE_GRACE_TICKS) and the server drops an
            // answer naming a prompt it has already retired, so the clear bought nothing.
        });
    }

    public static void handleSystemNotification(SystemNotificationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SystemToastLayer.push(payload.notification()));
    }

    public static void handleFocusPrompt(FocusPromptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> FocusPromptLayer.begin(payload.prompt()));
    }

    private ClientPayloadHandler() {
    }
}
