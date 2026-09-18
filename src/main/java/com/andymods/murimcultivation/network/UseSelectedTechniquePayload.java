package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asking to cast whatever the cycle key has selected.
 *
 * <p>Carries no slot index on purpose. The client does hold a synced copy of the selection, so
 * it could send {@link UseTechniquePayload} with that number — but the two copies are one
 * round-trip apart, and cycling then immediately casting would fire the previous art. Letting
 * the server read its own selection removes the window entirely, and keeps this consistent with
 * the rest of the mod, where the client reports the press and never the decision.
 */
public record UseSelectedTechniquePayload() implements CustomPacketPayload {

    public static final UseSelectedTechniquePayload INSTANCE = new UseSelectedTechniquePayload();

    public static final CustomPacketPayload.Type<UseSelectedTechniquePayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("use_selected_technique"));

    public static final StreamCodec<FriendlyByteBuf, UseSelectedTechniquePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
