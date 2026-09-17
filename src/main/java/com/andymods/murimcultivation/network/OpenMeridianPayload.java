package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.cultivation.Meridian;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asking to force a specific meridian open. The cost, the purity requirement and the
 * deviation roll are all decided server-side.
 *
 * <p>Sending the node by ordinal keeps the packet to a single byte. The decoder clamps rather
 * than trusting the value, so a malformed or hostile packet cannot index out of bounds.
 */
public record OpenMeridianPayload(Meridian meridian) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenMeridianPayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("open_meridian"));

    public static final StreamCodec<FriendlyByteBuf, OpenMeridianPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.meridian().ordinal()),
            buf -> {
                int ordinal = buf.readByte() & 0xFF;
                Meridian[] all = Meridian.values();
                return new OpenMeridianPayload(all[Math.min(ordinal, all.length - 1)]);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
