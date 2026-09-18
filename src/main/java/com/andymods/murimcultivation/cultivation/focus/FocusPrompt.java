package com.andymods.murimcultivation.cultivation.focus;

import net.minecraft.util.RandomSource;

/**
 * One breath-rhythm challenge, as the server issued it.
 *
 * <p>The marker sweeps 0 to 1 over {@code sweepTicks}; answering while it sits between
 * {@code windowStart} and {@code windowEnd} is a hit. The server keeps the authoritative copy and
 * sends an identical one to the client to draw — but the client's copy is only ever used for
 * drawing. Every judgement happens server-side in {@link FocusJudge}, because a client that is
 * told the window could otherwise simply claim to have hit it.
 *
 * @param id         distinguishes this prompt from the one before it, so a stale or replayed
 *                   answer can be dropped rather than credited
 * @param sweepTicks how long the marker takes to cross
 */
public record FocusPrompt(int id, int sweepTicks, double windowStart, double windowEnd) {

    /**
     * Rolls a challenge.
     *
     * <p>The window is placed randomly rather than centred, so the rhythm cannot be learned as a
     * fixed beat and answered from muscle memory alone while looking elsewhere. It is kept clear
     * of both ends: a window flush against zero would be unanswerable at any latency, and one
     * flush against the end gives no warning at all.
     */
    public static FocusPrompt roll(RandomSource random, int id, int sweepTicks, double windowWidth) {
        double width = Math.max(0.02D, Math.min(1.0D, windowWidth));
        double margin = Math.min(0.15D, (1.0D - width) / 2.0D);
        double span = 1.0D - width - margin * 2.0D;
        double start = margin + (span > 0.0D ? random.nextDouble() * span : 0.0D);
        return new FocusPrompt(id, sweepTicks, start, start + width);
    }

    public double windowWidth() {
        return windowEnd() - windowStart();
    }
}
