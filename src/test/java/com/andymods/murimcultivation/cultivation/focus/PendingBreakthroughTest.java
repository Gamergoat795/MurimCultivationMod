package com.andymods.murimcultivation.cultivation.focus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Circulation scoring. Pure arithmetic, so the relationship between how well you played and what
 * it was worth is pinned here rather than discovered in play — and so a balance change has to break
 * a test before it reaches a player.
 */
class PendingBreakthroughTest {

    private static PendingBreakthrough after(FocusJudge.Verdict... verdicts) {
        PendingBreakthrough pending = new PendingBreakthrough(3);
        for (FocusJudge.Verdict verdict : verdicts) {
            pending.recordIssued();
            pending.record(verdict);
        }
        return pending;
    }

    @Test
    void aFlawlessCirculationScoresFull() {
        assertEquals(1.0D, after(FocusJudge.Verdict.PERFECT, FocusJudge.Verdict.PERFECT,
                FocusJudge.Verdict.PERFECT).score(), 1.0e-9D);
    }

    @Test
    void missingEverythingScoresNothing() {
        assertEquals(0.0D, after(FocusJudge.Verdict.MISS, FocusJudge.Verdict.MISS,
                FocusJudge.Verdict.MISS).score(), 1.0e-9D);
    }

    @Test
    void threeScrapedHitsAreWorthAboutTwoThirds() {
        // (3 + 0) / 4.5. Deliberately short of full: clean sweeps have to be worth more than
        // barely-landed ones, or the perfect band is decoration.
        assertEquals(2.0D / 3.0D, after(FocusJudge.Verdict.HIT, FocusJudge.Verdict.HIT,
                FocusJudge.Verdict.HIT).score(), 1.0e-9D);
    }

    @Test
    void aPerfectIsWorthHalfAgainAsMuchAsAHit() {
        double hits = after(FocusJudge.Verdict.HIT, FocusJudge.Verdict.HIT,
                FocusJudge.Verdict.HIT).score();
        double perfects = after(FocusJudge.Verdict.PERFECT, FocusJudge.Verdict.PERFECT,
                FocusJudge.Verdict.PERFECT).score();
        assertEquals(1.5D, perfects / hits, 1.0e-9D);
    }

    @Test
    void scoreRisesMonotonicallyWithHits() {
        double none = after(FocusJudge.Verdict.MISS, FocusJudge.Verdict.MISS,
                FocusJudge.Verdict.MISS).score();
        double one = after(FocusJudge.Verdict.HIT, FocusJudge.Verdict.MISS,
                FocusJudge.Verdict.MISS).score();
        double two = after(FocusJudge.Verdict.HIT, FocusJudge.Verdict.HIT,
                FocusJudge.Verdict.MISS).score();
        double three = after(FocusJudge.Verdict.HIT, FocusJudge.Verdict.HIT,
                FocusJudge.Verdict.HIT).score();
        assertTrue(none < one && one < two && two < three,
                "more hits must always be worth more: " + none + " " + one + " " + two + " " + three);
    }

    @Test
    void aRejectedSweepCountsAsAMiss() {
        // INCONSISTENT and TOO_FAST are indistinguishable from a bad connection, and are treated
        // exactly as not answering would be — never worse.
        assertEquals(after(FocusJudge.Verdict.MISS, FocusJudge.Verdict.MISS, FocusJudge.Verdict.MISS)
                        .score(),
                after(FocusJudge.Verdict.INCONSISTENT, FocusJudge.Verdict.TOO_FAST,
                        FocusJudge.Verdict.MISS).score(), 1.0e-9D);
    }

    @Test
    void scoreIsNeverAboveOne() {
        // The clamp matters because the perfect weighting could otherwise overshoot if the
        // weighting constant were ever raised without revisiting the divisor.
        PendingBreakthrough single = new PendingBreakthrough(1);
        single.recordIssued();
        single.record(FocusJudge.Verdict.PERFECT);
        assertTrue(single.score() <= 1.0D);
    }

    @Test
    void sweepsRunOutAfterTheConfiguredCount() {
        PendingBreakthrough pending = new PendingBreakthrough(3);
        assertTrue(pending.hasSweepsLeft());
        pending.recordIssued();
        pending.recordIssued();
        assertTrue(pending.hasSweepsLeft(), "two of three issued, one still owed");
        pending.recordIssued();
        assertFalse(pending.hasSweepsLeft(), "all three issued");
    }

    @Test
    void aZeroOrNegativeSweepCountStillYieldsOneSweep() {
        // Guards a config of 0, which would otherwise resolve instantly with no circulation at all
        // and silently skip the mechanic.
        assertEquals(1, new PendingBreakthrough(0).totalSweeps());
        assertEquals(1, new PendingBreakthrough(-5).totalSweeps());
    }

    @Test
    void hitsAreCountedSeparatelyFromSweepsIssued() {
        PendingBreakthrough pending = after(FocusJudge.Verdict.HIT, FocusJudge.Verdict.MISS,
                FocusJudge.Verdict.PERFECT);
        assertEquals(3, pending.sweepsIssued());
        assertEquals(2, pending.hits(), "a perfect is also a hit");
    }
}
