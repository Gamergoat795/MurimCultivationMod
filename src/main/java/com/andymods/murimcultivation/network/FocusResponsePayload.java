package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player answered a breath-rhythm prompt.
 *
 * <p>Carries <em>where the marker was</em>, not whether that counted. The server already knows the
 * window it issued and how long it has been waiting, so it needs only this one number — and
 * checking the claim against its own elapsed-time measurement is what catches a client that
 * reports a perfect position every time. See {@code FocusJudge}.
 *
 * <p>The {@code promptId} is what makes a replayed or duplicated answer worthless: the server drops
 * anything that does not name the prompt currently outstanding.
 */
public record FocusResponsePayload(int promptId, double position) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FocusResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("focus_response"));

    public static final StreamCodec<FriendlyByteBuf, FocusResponsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.promptId());
                        buf.writeFloat((float) payload.position());
                    },
                    buf -> {
                        int promptId = buf.readVarInt();
                        // A position outside 0..1 is meaningless; clamping it turns a malformed
                        // packet into an ordinary miss rather than something the judge has to
                        // reason about.
                        double position = Math.max(0.0D, Math.min(1.0D, buf.readFloat()));
                        return new FocusResponsePayload(promptId, position);
                    });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
