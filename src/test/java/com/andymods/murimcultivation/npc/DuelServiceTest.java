package com.andymods.murimcultivation.npc;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who will fight whom, and the guarantee that a duel cannot kill anyone.
 *
 * <p>The yield maths is the half worth testing hardest. A duel that occasionally killed someone
 * would undo the whole design — fixed-difficulty warriors are only a fair idea because losing to one
 * is survivable — and it is exactly the kind of off-by-one that never shows up until the one fight
 * where a big hit lands at low health.
 */
class DuelServiceTest {

    private static final DuelService.Tuning TUNING = new DuelService.Tuning(
            2,     // maxRealmGap
            60,    // infamyThatProvokes
            0.2D); // yieldHealthFraction

    // --- Who accepts ------------------------------------------------------------------

    @Test
    void anEvenMatchIsAccepted() {
        assertEquals(DuelService.Verdict.ACCEPTED, DuelService.judge(1, 1, 0, TUNING));
        assertEquals(DuelService.Verdict.ACCEPTED, DuelService.judge(3, 5, 0, TUNING));
    }

    @Test
    void theGapIsInclusiveAtItsEdge() {
        assertEquals(DuelService.Verdict.ACCEPTED, DuelService.judge(1, 3, 0, TUNING),
                "two realms apart is the most that is still a duel");
        assertEquals(DuelService.Verdict.REFUSED_TOO_STRONG, DuelService.judge(1, 4, 0, TUNING));
    }

    @Test
    void aMasterRefusesSomeoneFarBeneathIt() {
        assertEquals(DuelService.Verdict.REFUSED_TOO_STRONG, DuelService.judge(1, 5, 0, TUNING));
        assertEquals(DuelService.Verdict.REFUSED_TOO_STRONG, DuelService.judge(2, 5, 0, TUNING));
    }

    @Test
    void aThugRefusesSomeoneFarAboveIt() {
        // The half that closes the honour farm. Without it a Transcendent could collect honour off
        // Third-Rate thugs indefinitely, and honour won by beating people who cannot fight back is
        // the exact thing the word is supposed to exclude.
        assertEquals(DuelService.Verdict.REFUSED_TOO_WEAK, DuelService.judge(5, 1, 0, TUNING));
        assertEquals(DuelService.Verdict.ACCEPTED, DuelService.judge(5, 3, 0, TUNING),
                "but two realms down is still worth fighting");
    }

    @Test
    void theGapIsSymmetric() {
        for (int a = 1; a <= 9; a++) {
            for (int b = 1; b <= 9; b++) {
                boolean acceptedOneWay = DuelService.judge(a, b, 0, TUNING).accepted();
                boolean acceptedTheOther = DuelService.judge(b, a, 0, TUNING).accepted();
                assertEquals(acceptedOneWay, acceptedTheOther,
                        "tiers " + a + " and " + b + " disagree depending on who asks");
            }
        }
    }

    @Test
    void notorietyOverridesTheQuestionEntirely() {
        assertEquals(DuelService.Verdict.ACCEPTED, DuelService.judge(1, 1, 59, TUNING));
        assertEquals(DuelService.Verdict.ATTACKS_INSTEAD, DuelService.judge(1, 1, 60, TUNING),
                "the threshold is inclusive");
        // Checked before the realm gap, so someone notorious is attacked rather than dismissed.
        assertEquals(DuelService.Verdict.ATTACKS_INSTEAD, DuelService.judge(1, 9, 100, TUNING));
        assertEquals(DuelService.Verdict.ATTACKS_INSTEAD, DuelService.judge(9, 1, 100, TUNING));
    }

    @Test
    void aZeroGapTuningOnlyAcceptsExactPeers() {
        DuelService.Tuning strict = new DuelService.Tuning(0, 60, 0.2D);
        assertEquals(DuelService.Verdict.ACCEPTED, DuelService.judge(3, 3, 0, strict));
        assertEquals(DuelService.Verdict.REFUSED_TOO_STRONG, DuelService.judge(3, 4, 0, strict));
        assertEquals(DuelService.Verdict.REFUSED_TOO_WEAK, DuelService.judge(4, 3, 0, strict));
    }

    @Test
    void everyVerdictHasItsOwnNamespacedMessage() {
        for (DuelService.Verdict verdict : DuelService.Verdict.values()) {
            assertTrue(verdict.translationKey().startsWith("murimcultivation.duel."));
            assertEquals(verdict.getSerializedName().toLowerCase(Locale.ROOT),
                    verdict.getSerializedName());
        }
        assertEquals(DuelService.Verdict.values().length,
                Arrays.stream(DuelService.Verdict.values())
                        .map(DuelService.Verdict::translationKey).distinct().count());
    }

    // --- Yielding ---------------------------------------------------------------------

    @Test
    void theThresholdIsAShareOfMaximumHealthButNeverBelowOne() {
        assertEquals(4.0F, DuelService.yieldThreshold(20.0F, TUNING), 1.0e-6F);
        assertEquals(20.0F, DuelService.yieldThreshold(100.0F, TUNING), 1.0e-6F);
        // A tiny maximum would otherwise put the threshold below one, i.e. "yields at death",
        // which is the single outcome the whole rule exists to prevent.
        assertEquals(1.0F, DuelService.yieldThreshold(2.0F, TUNING), 1.0e-6F);
        assertEquals(1.0F, DuelService.yieldThreshold(1.0F, TUNING), 1.0e-6F);
    }

    @Test
    void aBlowThatDoesNotReachTheThresholdPassesThroughUntouched() {
        assertFalse(DuelService.wouldYield(20.0F, 20.0F, 5.0F, TUNING));
        assertEquals(5.0F, DuelService.damageBeforeYielding(20.0F, 20.0F, 5.0F, TUNING), 1.0e-6F);
    }

    @Test
    void aBlowThatReachesTheThresholdIsTrimmedToLandOnIt() {
        assertTrue(DuelService.wouldYield(6.0F, 20.0F, 5.0F, TUNING));
        assertEquals(2.0F, DuelService.damageBeforeYielding(6.0F, 20.0F, 5.0F, TUNING), 1.0e-6F,
                "six health less two leaves exactly the threshold");
    }

    @Test
    void theThresholdItselfCountsAsYielding() {
        assertTrue(DuelService.wouldYield(8.0F, 20.0F, 4.0F, TUNING), "landing exactly on it yields");
        assertFalse(DuelService.wouldYield(9.0F, 20.0F, 4.0F, TUNING));
    }

    @Test
    void someoneAlreadyAtOrBelowTheThresholdTakesNothing() {
        assertEquals(0.0F, DuelService.damageBeforeYielding(4.0F, 20.0F, 5.0F, TUNING), 1.0e-6F);
        assertEquals(0.0F, DuelService.damageBeforeYielding(3.0F, 20.0F, 5.0F, TUNING), 1.0e-6F,
                "never negative, which would heal them");
    }

    @Test
    void anEnormousBlowIsTrimmedRatherThanKilling() {
        // The case that would otherwise kill: a Master hitting a Third-Rate for far more than their
        // whole health bar.
        assertEquals(16.0F, DuelService.damageBeforeYielding(20.0F, 20.0F, 1000.0F, TUNING), 1.0e-6F);
    }

    @Test
    void noSequenceOfBlowsCanKillADuellist() {
        // The guarantee the whole design leans on, checked the way it actually happens: blow after
        // blow, each trimmed, never stepping past the threshold.
        for (float maxHealth : new float[]{1.0F, 2.0F, 20.0F, 40.0F, 100.0F}) {
            for (float blow : new float[]{0.5F, 1.0F, 7.0F, 999.0F}) {
                float health = maxHealth;
                // Enough swings for even the gentlest blow to walk the highest health down to the
                // threshold: eighty points of health at half a point a swing is a hundred and sixty.
                for (int swing = 0; swing < 400; swing++) {
                    health -= DuelService.damageBeforeYielding(health, maxHealth, blow, TUNING);
                }
                assertTrue(health > 0.0F,
                        "max " + maxHealth + " under blows of " + blow + " reached " + health);
                assertEquals(DuelService.yieldThreshold(maxHealth, TUNING), health, 1.0e-4F,
                        "a duellist should settle exactly on the threshold, not below it");
            }
        }
    }
}
