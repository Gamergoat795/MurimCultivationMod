package com.andymods.murimcultivation.client;

import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.network.SyncCultivationPayload;
import com.andymods.murimcultivation.network.SyncCultivationValuesPayload;
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
        });
    }

    private ClientPayloadHandler() {
    }
}
