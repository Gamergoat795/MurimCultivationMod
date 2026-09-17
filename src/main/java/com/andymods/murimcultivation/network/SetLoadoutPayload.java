package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.cultivation.CultivationData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client submitting a new loadout order. The server filters it to techniques the player has
 * actually learned, so this cannot be used to bind an art they never trained.
 */
public record SetLoadoutPayload(List<ResourceLocation> techniques) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetLoadoutPayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("set_loadout"));

    public static final StreamCodec<FriendlyByteBuf, SetLoadoutPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                List<ResourceLocation> techniques = payload.techniques();
                int count = Math.min(techniques.size(), CultivationData.LOADOUT_SIZE);
                buf.writeByte(count);
                for (int i = 0; i < count; i++) {
                    buf.writeResourceLocation(techniques.get(i));
                }
            },
            buf -> {
                int count = Math.max(0, Math.min(CultivationData.LOADOUT_SIZE, buf.readByte()));
                List<ResourceLocation> techniques = new java.util.ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    techniques.add(buf.readResourceLocation());
                }
                return new SetLoadoutPayload(List.copyOf(techniques));
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
