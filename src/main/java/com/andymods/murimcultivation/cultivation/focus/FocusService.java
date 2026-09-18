package com.andymods.murimcultivation.cultivation.focus;

import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.network.FocusPromptPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Issues breath-rhythm prompts during meditation and scores the answers.
 *
 * <p>The problem this solves: before it existed, the most profitable way to play was to sit down
 * somewhere with good ambient Qi and walk away, because the session ramp climbs to its maximum
 * after a minute and nothing ever checked whether anyone was still at the keyboard. A cultivator's
 * discipline is attention, so attention is what the mechanic asks for.
 *
 * <p>Missing a prompt never subtracts anything. It only lowers {@code focus}, which multiplies
 * into what meditation produces — so an absent player earns nothing while a player who steps away
 * for a moment loses nothing they had already earned. That asymmetry is the whole design: the
 * prompt reads as a bonus being kept rather than a toll being paid.
 *
 * <p>Everything authoritative lives here. The server picks when a prompt fires, where its window
 * sits, and what the answer was worth; see {@link FocusJudge} for why the client is never asked
 * for a verdict.
 */
public final class FocusService {

    private static final double TICKS_PER_SECOND = 20.0D;

    private FocusService() {
    }

    /**
     * Advances the prompt clock for a meditating player, firing a new prompt when one is due and
     * scoring the outstanding one as a miss when it runs out of time.
     *
     * <p>Called every tick rather than on the once-a-second cultivation cadence, because a two
     * second sweep judged to the nearest second would be unanswerable.
     */
    public static void tick(ServerPlayer player, CultivationData data) {
        if (!data.isMeditating()) {
            return;
        }
        // A penalty of zero is the documented way to switch the mechanic off; skip the packets
        // entirely in that case rather than sending prompts that cannot matter.
        if (MurimConfig.focusMissPenalty() <= 0.0D) {
            return;
        }

        if (data.tickFocusPrompt()) {
            score(player, data, FocusJudge.Verdict.MISS);
            return;
        }
        if (data.focusPrompt() == null && data.focusNextPromptTicks() <= 0) {
            issue(player, data);
        }
    }

    /** Rolls and sends a prompt. */
    private static void issue(ServerPlayer player, CultivationData data) {
        int sweepTicks = Math.max(1, (int) Math.round(MurimConfig.focusSweepSeconds() * TICKS_PER_SECOND));
        FocusPrompt prompt = FocusPrompt.roll(player.getRandom(), data.nextFocusPromptId(),
                sweepTicks, MurimConfig.focusWindowWidth());

        // The prompt stays answerable a little past the end of its sweep, so an answer sent on the
        // last tick still arrives in time to be judged rather than racing its own deadline.
        data.setFocusPrompt(prompt, sweepTicks + graceTicks());

        PacketDistributor.sendToPlayer(player, new FocusPromptPayload(prompt));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.4F, 1.4F);
    }

    /**
     * Judges an answer the client sent.
     *
     * <p>An answer naming a prompt that is not outstanding is dropped silently: that covers a
     * replay, a duplicate, an answer to the previous prompt arriving late, and key-spam, all of
     * which would otherwise be ways to fish for a hit.
     */
    public static void answer(ServerPlayer player, int promptId, double clientPosition) {
        CultivationData data = CultivationService.data(player);
        FocusPrompt prompt = data.focusPrompt();
        if (prompt == null || prompt.id() != promptId) {
            return;
        }

        FocusJudge.Verdict verdict = FocusJudge.judge(clientPosition,
                data.focusPromptElapsedTicks(), prompt, FocusJudge.Tuning.fromConfig());
        score(player, data, verdict);
    }

    /** Applies a verdict: updates focus, pays any bonus, schedules the next prompt, tells the player. */
    private static void score(ServerPlayer player, CultivationData data, FocusJudge.Verdict verdict) {
        FocusJudge.Tuning tuning = FocusJudge.Tuning.fromConfig();
        data.setFocus(FocusJudge.applyVerdict(data.focus(), verdict, tuning));
        data.clearFocusPrompt();
        scheduleNext(player, data);

        if (verdict == FocusJudge.Verdict.PERFECT) {
            // Attention is rewarded outright, not merely spared a penalty. Denominated in seconds
            // of meditation so it scales with the same config every other gain reads.
            data.addProgress(MurimConfig.meditationProgressPerSecond()
                    * MurimConfig.focusPerfectBonusSeconds());
        }

        player.displayClientMessage(messageFor(verdict), true);
        if (verdict.success()) {
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F,
                    verdict == FocusJudge.Verdict.PERFECT ? 1.4F : 1.1F);
        } else {
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.3F, 0.6F);
        }
        CultivationService.syncValuesToClient(player);
    }

    /** Rolls the gap to the next prompt, so the rhythm cannot be anticipated. */
    public static void scheduleNext(ServerPlayer player, CultivationData data) {
        int min = MurimConfig.focusPromptMinSeconds();
        int max = Math.max(min, MurimConfig.focusPromptMaxSeconds());
        int seconds = min + (max > min ? player.getRandom().nextInt(max - min + 1) : 0);
        data.setFocusNextPromptTicks((int) Math.round(seconds * TICKS_PER_SECOND));
    }

    /**
     * How long past the end of the sweep an answer is still accepted.
     *
     * <p>Sized from the latency tolerance rather than picked arbitrarily, so the two numbers cannot
     * drift apart: an answer the judge would forgive for lateness must still find its prompt alive.
     */
    private static int graceTicks() {
        double sweepTicks = MurimConfig.focusSweepSeconds() * TICKS_PER_SECOND;
        return Math.max(2, (int) Math.ceil(sweepTicks * MurimConfig.focusLatencyTolerance()));
    }

    private static Component messageFor(FocusJudge.Verdict verdict) {
        return switch (verdict) {
            case PERFECT -> Component.translatable("murimcultivation.focus.perfect");
            case HIT -> Component.translatable("murimcultivation.focus.hit");
            case MISS -> Component.translatable("murimcultivation.focus.miss");
            // Both of these are indistinguishable from a bad connection, so the player is told
            // their breath slipped rather than accused of anything.
            case INCONSISTENT, TOO_FAST -> Component.translatable("murimcultivation.focus.rejected");
        };
    }
}
