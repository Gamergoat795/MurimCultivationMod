package com.andymods.murimcultivation.cultivation.focus;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The breath-rhythm judgement, which is the one piece of this mod a cheating client has a reason
 * to probe. Tested without Minecraft bootstrapped at all, which is the payoff for keeping
 * {@link FocusJudge} pure.
 */
class FocusJudgeTest {

    /** 50-tick sweep, window from 0.40 to 0.60, so the centre is 0.50. */
    private static final FocusPrompt PROMPT = new FocusPrompt(1, 50, 0.40D, 0.60D);

    private static final FocusJudge.Tuning TUNING =
            new FocusJudge.Tuning(3, 0.18D, 0.34D, 0.5D, 0.34D);

    /** The server tick count that corresponds to a marker position, for an honest client. */
    private static int ticksAt(double position) {
        return (int) Math.round(position * PROMPT.sweepTicks());
    }

    private static FocusJudge.Verdict judgeHonestly(double position) {
        return FocusJudge.judge(position, ticksAt(position), PROMPT, TUNING);
    }

    @Test
    void deadCentreIsPerfect() {
        assertEquals(FocusJudge.Verdict.PERFECT, judgeHonestly(0.50D));
    }

    @Test
    void insideTheWindowButOffCentreIsAMereHit() {
        // The window is 0.20 wide and the perfect band is the middle 34% of it, so ±0.034 of
        // centre. 0.45 is inside the window and outside that band.
        assertEquals(FocusJudge.Verdict.HIT, judgeHonestly(0.45D));
        assertEquals(FocusJudge.Verdict.HIT, judgeHonestly(0.57D));
    }

    @Test
    void theWindowEdgesThemselvesCount() {
        // Inclusive on both ends: a player who lands exactly on the boundary earned it, and an
        // exclusive comparison here would read as a bug to anyone watching the bar.
        assertEquals(FocusJudge.Verdict.HIT, judgeHonestly(0.40D));
        assertEquals(FocusJudge.Verdict.HIT, judgeHonestly(0.60D));
    }

    @Test
    void outsideTheWindowMisses() {
        assertEquals(FocusJudge.Verdict.MISS, judgeHonestly(0.30D));
        assertEquals(FocusJudge.Verdict.MISS, judgeHonestly(0.75D));
    }

    @Test
    void aClientClaimingAPerfectHitLateIsInconsistent() {
        // The cheat this check exists for: the client says the marker was dead centre, but the
        // server waited long enough to know it was almost at the end.
        assertEquals(FocusJudge.Verdict.INCONSISTENT,
                FocusJudge.judge(0.50D, ticksAt(0.90D), PROMPT, TUNING));
    }

    @Test
    void anHonestPlayerOnABadConnectionStillPasses() {
        // The tolerance has to be generous enough that real latency does not read as cheating.
        // Here the client pressed at 0.50 and the answer reached the server 7 ticks (350ms) later,
        // which is 0.14 of a 50-tick sweep — inside the 0.18 tolerance.
        assertEquals(FocusJudge.Verdict.PERFECT,
                FocusJudge.judge(0.50D, ticksAt(0.50D) + 7, PROMPT, TUNING));
    }

    @Test
    void latencyBeyondTheToleranceIsRejected() {
        // 0.19 of the sweep is outside the 0.18 tolerance. This is the honest cost of the check:
        // a player on a genuinely dreadful connection is indistinguishable from a liar, which is
        // why the penalty for INCONSISTENT is no worse than for simply missing.
        assertEquals(FocusJudge.Verdict.INCONSISTENT,
                FocusJudge.judge(0.50D, ticksAt(0.50D) + 10, PROMPT, TUNING));
    }

    @Test
    void answeringInstantlyIsRejectedAsInhuman() {
        assertEquals(FocusJudge.Verdict.TOO_FAST, FocusJudge.judge(0.0D, 0, PROMPT, TUNING));
        assertEquals(FocusJudge.Verdict.TOO_FAST, FocusJudge.judge(0.04D, 2, PROMPT, TUNING));
    }

    @Test
    void theReactionFloorIsInclusiveAtItsEdge() {
        // Exactly minReactionTicks is allowed; it is a floor, not a threshold to clear.
        assertFalse(FocusJudge.judge(0.06D, 3, PROMPT, TUNING) == FocusJudge.Verdict.TOO_FAST);
    }

    @Test
    void aZeroLengthSweepCannotDivideByZero() {
        // Guards a config of sweepSeconds so small it rounds to zero ticks.
        FocusPrompt degenerate = new FocusPrompt(1, 0, 0.4D, 0.6D);
        assertEquals(FocusJudge.Verdict.INCONSISTENT,
                FocusJudge.judge(0.5D, 10, degenerate, TUNING));
    }

    // --- focus arithmetic -------------------------------------------------------------

    @Test
    void threeMissesReachExactlyZeroAndStopThere() {
        double focus = 1.0D;
        for (int i = 0; i < 3; i++) {
            focus = FocusJudge.applyVerdict(focus, FocusJudge.Verdict.MISS, TUNING);
        }
        assertEquals(0.0D, focus, 1.0e-9D, "three misses should zero out an absent player");

        focus = FocusJudge.applyVerdict(focus, FocusJudge.Verdict.MISS, TUNING);
        assertEquals(0.0D, focus, 1.0e-9D, "focus must never go negative");
    }

    @Test
    void focusNeverExceedsOne() {
        double focus = 1.0D;
        for (int i = 0; i < 5; i++) {
            focus = FocusJudge.applyVerdict(focus, FocusJudge.Verdict.HIT, TUNING);
        }
        assertEquals(1.0D, focus, 1.0e-9D);
    }

    @Test
    void aPerfectAnswerRestoresFocusOutright() {
        assertEquals(1.0D, FocusJudge.applyVerdict(0.0D, FocusJudge.Verdict.PERFECT, TUNING),
                1.0e-9D, "one perfect answer should pull a lapsed player all the way back");
    }

    @Test
    void oneHitRecoversMostOfOneMiss() {
        double afterMiss = FocusJudge.applyVerdict(1.0D, FocusJudge.Verdict.MISS, TUNING);
        double afterHit = FocusJudge.applyVerdict(afterMiss, FocusJudge.Verdict.HIT, TUNING);
        assertTrue(afterHit > afterMiss, "a hit must move focus upward");
        assertEquals(1.0D, afterHit, 1.0e-9D, "0.66 + 0.5 clamps back to full");
    }

    @Test
    void aRejectedAnswerCostsNoMoreThanIgnoringThePrompt() {
        // Deliberate: the latency check has false positives, and punishing a laggy honest player
        // harder than an absent one would be the wrong trade.
        double miss = FocusJudge.applyVerdict(1.0D, FocusJudge.Verdict.MISS, TUNING);
        assertEquals(miss, FocusJudge.applyVerdict(1.0D, FocusJudge.Verdict.INCONSISTENT, TUNING));
        assertEquals(miss, FocusJudge.applyVerdict(1.0D, FocusJudge.Verdict.TOO_FAST, TUNING));
    }

    @Test
    void aZeroPenaltyConfigDisablesTheMechanicEntirely() {
        // The documented escape hatch for a server that does not want prompts: focus stays pinned
        // at full, so meditation behaves exactly as it did before this feature existed.
        FocusJudge.Tuning off = new FocusJudge.Tuning(3, 0.18D, 0.34D, 0.5D, 0.0D);
        double focus = 1.0D;
        for (int i = 0; i < 10; i++) {
            focus = FocusJudge.applyVerdict(focus, FocusJudge.Verdict.MISS, off);
        }
        assertEquals(1.0D, focus, 1.0e-9D);
    }

    @Test
    void onlyHitsAndPerfectsCountAsSuccess() {
        assertTrue(FocusJudge.Verdict.PERFECT.success());
        assertTrue(FocusJudge.Verdict.HIT.success());
        assertFalse(FocusJudge.Verdict.MISS.success());
        assertFalse(FocusJudge.Verdict.INCONSISTENT.success());
        assertFalse(FocusJudge.Verdict.TOO_FAST.success());
    }

    // --- prompt generation ------------------------------------------------------------

    @Test
    void aRolledWindowAlwaysSitsInsideTheSweepWithRoomToReact() {
        // A window flush against zero is unanswerable at any latency; one flush against the end
        // gives no warning. Both margins must hold for every roll, not just typical ones.
        Random seeded = new Random(1234L);
        for (int i = 0; i < 2000; i++) {
            double width = 0.02D + seeded.nextDouble() * 0.9D;
            FocusPrompt prompt = FocusPrompt.roll(
                    RandomSource.create(seeded.nextLong()), i, 50, width);
            assertTrue(prompt.windowStart() > 0.0D,
                    "window starts at " + prompt.windowStart() + " for width " + width);
            assertTrue(prompt.windowEnd() < 1.0D,
                    "window ends at " + prompt.windowEnd() + " for width " + width);
            assertEquals(width, prompt.windowWidth(), 1.0e-9D);
        }
    }

    @Test
    void aWindowWiderThanTheSweepIsClampedRatherThanInverted() {
        FocusPrompt prompt = FocusPrompt.roll(RandomSource.create(1L), 1, 50, 5.0D);
        assertTrue(prompt.windowWidth() <= 1.0D);
        assertTrue(prompt.windowStart() <= prompt.windowEnd());
    }
}
