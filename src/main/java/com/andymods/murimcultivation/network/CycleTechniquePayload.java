package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asking to advance the selected loadout slot. For players who would rather use one
 * cast key than bind four.
 */
public record CycleTechniquePayload() implements CustomPacketPayload {

    public static final CycleTechniquePayload INSTANCE = new CycleTechniquePayload();

    public static final CustomPacketPayload.Type<CycleTechniquePayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("cycle_technique"));

    public static final StreamCodec<FriendlyByteBuf, CycleTechniquePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
