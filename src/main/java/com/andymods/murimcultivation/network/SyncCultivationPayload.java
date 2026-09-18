package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.cultivation.CultivationData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Full cultivation state, server to client. Sent on login, respawn and dimension change —
 * the three moments where the client's copy is either absent or stale.
 *
 * <p>Routine per-second changes use the narrower {@link SyncCultivationValuesPayload} instead,
 * so the common case is not a full-state blast.
 *
 * <p>Meditation state and the selected loadout slot ride alongside the encoded data rather than
 * inside it: both are transient and so deliberately excluded from the persistence codec, but the
 * HUD still needs them. The selected slot in particular has nowhere else to go — the codec is at
 * 14 of the 16 fields {@code RecordCodecBuilder.group} allows, and spending one of the two
 * remaining on state that is not even persisted would be a poor trade.
 */
public record SyncCultivationPayload(CultivationData data, boolean meditating, int selectedSlot)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncCultivationPayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("sync_cultivation"));

    /**
     * Encoded by running the persistence codec into NBT. Reusing the codec means the wire
     * format cannot drift from the save format.
     */
    public static final StreamCodec<FriendlyByteBuf, SyncCultivationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                CompoundTag tag = (CompoundTag) CultivationData.CODEC
                        .encodeStart(NbtOps.INSTANCE, payload.data())
                        .getOrThrow();
                buf.writeNbt(tag);
                buf.writeBoolean(payload.meditating());
                buf.writeVarInt(payload.selectedSlot());
            },
            buf -> {
                CompoundTag tag = buf.readNbt();
                CultivationData data = tag == null
                        ? new CultivationData()
                        : CultivationData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
                return new SyncCultivationPayload(data, buf.readBoolean(), buf.readVarInt());
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
