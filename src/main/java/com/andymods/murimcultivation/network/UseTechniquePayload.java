package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.cultivation.CultivationData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client pressed a loadout slot key. Carries only the slot index — the server looks up what
 * is bound there and decides everything else, so a modified client cannot name an arbitrary
 * technique it has not learned.
 */
public record UseTechniquePayload(int slot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UseTechniquePayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("use_technique"));

    public static final StreamCodec<FriendlyByteBuf, UseTechniquePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.slot()),
            buf -> {
                // Clamp rather than trust: a malformed packet must not index out of bounds.
                int slot = buf.readByte();
                return new UseTechniquePayload(
                        Math.max(0, Math.min(CultivationData.LOADOUT_SIZE - 1, slot)));
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
