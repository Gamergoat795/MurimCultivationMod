package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The three numbers that change constantly — Qi, progress, purity — plus meditation state.
 *
 * <p>Sent once per cultivation tick to a player who is actively cultivating. Keeping this
 * separate from {@link SyncCultivationPayload} means the frequent update is a handful of bytes
 * rather than the player's entire technique list and meridian network.
 */
public record SyncCultivationValuesPayload(double qi, double progress, double purity, boolean meditating)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncCultivationValuesPayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("sync_cultivation_values"));

    public static final StreamCodec<FriendlyByteBuf, SyncCultivationValuesPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.qi());
                buf.writeDouble(payload.progress());
                buf.writeDouble(payload.purity());
                buf.writeBoolean(payload.meditating());
            },
            buf -> new SyncCultivationValuesPayload(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
