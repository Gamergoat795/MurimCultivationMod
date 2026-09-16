package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client requesting a breakthrough attempt. Every eligibility check and every roll happens
 * server-side; this is only the intent.
 */
public record AttemptBreakthroughPayload() implements CustomPacketPayload {

    public static final AttemptBreakthroughPayload INSTANCE = new AttemptBreakthroughPayload();

    public static final CustomPacketPayload.Type<AttemptBreakthroughPayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("attempt_breakthrough"));

    public static final StreamCodec<FriendlyByteBuf, AttemptBreakthroughPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
