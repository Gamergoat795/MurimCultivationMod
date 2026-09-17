package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.system.StatType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asking to commit stat points. The server checks the player can afford it and that the
 * stat is not capped, so a stale click from a UI cannot conjure points.
 */
public record SpendStatPointPayload(StatType stat, int points) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpendStatPointPayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("spend_stat_point"));

    public static final StreamCodec<FriendlyByteBuf, SpendStatPointPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.stat());
                buf.writeVarInt(payload.points());
            },
            buf -> new SpendStatPointPayload(buf.readEnum(StatType.class),
                    Math.max(1, Math.min(StatType.MAX_POINTS_PER_STAT, buf.readVarInt()))));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
