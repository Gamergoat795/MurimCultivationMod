package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asking to start or stop meditating. Carries no state: the server owns whether
 * meditation is currently active and simply flips it, so a desynced client cannot force
 * a particular outcome.
 */
public record ToggleMeditationPayload() implements CustomPacketPayload {

    public static final ToggleMeditationPayload INSTANCE = new ToggleMeditationPayload();

    public static final CustomPacketPayload.Type<ToggleMeditationPayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("toggle_meditation"));

    public static final StreamCodec<FriendlyByteBuf, ToggleMeditationPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
