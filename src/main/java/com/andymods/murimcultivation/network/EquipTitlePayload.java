package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Client asking to wear a title, or to take one off. An empty id clears the slot. The server
 * only honours titles the player actually owns.
 */
public record EquipTitlePayload(Optional<ResourceLocation> title) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EquipTitlePayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("equip_title"));

    public static final StreamCodec<FriendlyByteBuf, EquipTitlePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.title().isPresent());
                payload.title().ifPresent(buf::writeResourceLocation);
            },
            buf -> new EquipTitlePayload(
                    buf.readBoolean() ? Optional.of(buf.readResourceLocation()) : Optional.empty()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
