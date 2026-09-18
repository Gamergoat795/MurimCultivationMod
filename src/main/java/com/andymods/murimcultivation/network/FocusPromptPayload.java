package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.cultivation.focus.FocusPrompt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: a breath-rhythm prompt to draw.
 *
 * <p>The client is told the window only so it can render the bar. It never decides whether the
 * answer landed inside it — the server keeps the authoritative copy and judges the reply, because
 * a client trusted with the verdict could simply always claim a hit.
 */
public record FocusPromptPayload(FocusPrompt prompt) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FocusPromptPayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("focus_prompt"));

    public static final StreamCodec<FriendlyByteBuf, FocusPromptPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.prompt().id());
                        buf.writeVarInt(payload.prompt().sweepTicks());
                        buf.writeFloat((float) payload.prompt().windowStart());
                        buf.writeFloat((float) payload.prompt().windowEnd());
                    },
                    buf -> {
                        int id = buf.readVarInt();
                        // Clamp rather than trust, as every decoder here does. A sweep of zero
                        // would divide by zero in the renderer, and a window outside 0..1 would
                        // draw off the end of the bar.
                        int sweepTicks = Math.max(1, buf.readVarInt());
                        double start = clamp01(buf.readFloat());
                        double end = Math.max(start, clamp01(buf.readFloat()));
                        return new FocusPromptPayload(new FocusPrompt(id, sweepTicks, start, end));
                    });

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
