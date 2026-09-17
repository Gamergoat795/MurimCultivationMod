package com.andymods.murimcultivation.cultivation;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The breakthrough odds curve — the single most balance-critical formula in the mod.
 *
 * <p>The design intent these tests pin down: preparation must always be rewarded, the odds must
 * stay bounded so a breakthrough is never a formality nor ever hopeless, and failing a
 * well-prepared attempt must never be punished as harshly as failing a reckless gamble. Break
 * any of those and the progression stops asking the player to make a decision.
 */
class BreakthroughOddsTest {

    /** The shipped defaults, so these assertions concern real values. */
    private static final BreakthroughService.Tuning TUNING = new BreakthroughService.Tuning(
            0.35D, 0.20D, 0.15D, 0.05D, 0.95D, 0.60D, 0.30D);

    @BeforeAll
    static void bootstrapMinecraft() {
        // Realm's codec reaches BuiltInRegistries.ATTRIBUTE through AttributeGrant.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A target realm demanding 50 purity and 5 open meridians, with a 70% base chance. */
    private static Realm target() {
        return new Realm(4, "murimcultivation.realm.peak", "절정",
                1000.0D, 560.0D, 5.0D, 50.0D, 5, 0.70D, 3, 0x7E57C2, List.of());
    }

    private static CultivationData cultivator(double purity, int openMeridians) {
        CultivationData data = new CultivationData();
        data.setAwakened(true);
        data.setPurity(purity);
        for (int i = 0; i < openMeridians; i++) {
            data.openMeridian(Meridian.values()[i]);
        }
        return data;
    }

    private static double chance(double purity, int openMeridians, double density) {
        return BreakthroughService.successChance(target(), cultivator(purity, openMeridians), density, TUNING);
    }

    @Test
    void scrapingInAtTheMinimumGivesTheRealmsBaseChance() {
        // Exactly at the purity floor, exactly the required meridians, ordinary location:
        // no bonuses apply, so the odds are the realm's own.
        assertEquals(0.70D, chance(50.0D, 5, 1.0D), 1.0e-9D);
    }

    @Test
    void refiningPurityBeyondTheFloorImprovesTheOdds() {
        double scraped = chance(50.0D, 5, 1.0D);
        double refined = chance(75.0D, 5, 1.0D);
        double perfect = chance(100.0D, 5, 1.0D);

        assertTrue(refined > scraped, "purity above the floor must help");
        assertTrue(perfect > refined, "more purity must keep helping");
        // At full purity the whole purity weight is earned.
        assertEquals(0.70D + 0.35D, perfect, 1.0e-9D);
    }

    @Test
    void openingSpareMeridiansImprovesTheOdds() {
        double required = chance(50.0D, 5, 1.0D);
        double extra = chance(50.0D, 12, 1.0D);
        double everything = chance(50.0D, Meridian.count(), 1.0D);

        assertTrue(extra > required, "meridians beyond the requirement must help");
        assertTrue(everything > extra, "opening the whole network must help more");
        // With every node open, the full meridian weight is earned.
        assertEquals(0.70D + 0.20D, everything, 1.0e-9D);
    }

    @Test
    void ambientQiShiftsTheOddsBothWays() {
        double ordinary = chance(50.0D, 5, 1.0D);
        double spiritVein = chance(50.0D, 5, 2.0D);
        double barren = chance(50.0D, 5, 0.5D);

        assertTrue(spiritVein > ordinary, "a strong location must help");
        assertTrue(barren < ordinary, "a barren location must hurt");
        assertEquals(0.70D + 0.15D, spiritVein, 1.0e-9D, "density 2.0 earns the full density weight");
    }

    @Test
    void theOddsAreClampedAtBothEnds() {
        // Everything maxed would exceed 1.0 without the ceiling.
        assertEquals(0.95D, chance(100.0D, Meridian.count(), 3.0D), 1.0e-9D,
                "a breakthrough must never be a formality");
        // A hopeless attempt still has the floor.
        assertEquals(0.05D, chance(0.0D, 0, 0.0D), 1.0e-9D,
                "a desperate attempt must never be truly impossible");
    }

    @Test
    void oddsNeverDecreaseAsPreparationImproves() {
        double previous = 0.0D;
        for (double purity = 50.0D; purity <= 100.0D; purity += 2.5D) {
            double current = chance(purity, 5, 1.0D);
            assertTrue(current >= previous, "odds regressed at purity " + purity);
            previous = current;
        }
    }

    @Test
    void purityBelowTheFloorIsNeverAPenaltyBeyondLosingTheBonus() {
        // Eligibility already blocks attempting below the floor; the formula must still not
        // produce a negative contribution if it is ever called that way.
        assertTrue(chance(0.0D, 5, 1.0D) <= chance(50.0D, 5, 1.0D));
        assertEquals(chance(0.0D, 5, 1.0D), chance(50.0D, 5, 1.0D), 1.0e-9D,
                "below the floor the purity term should clamp to zero, not go negative");
    }

    @Test
    void aRealmRequiringEveryMeridianStillComputesAnHonestChance() {
        // requiredOpenMeridians == 20 leaves no spare nodes; the formula must not divide by zero.
        Realm demanding = new Realm(9, "murimcultivation.realm.beyond_heaven", "천외천",
                100000.0D, 7500.0D, 55.0D, 92.0D, Meridian.count(), 0.10D, 8, 0xFFFFFF, List.of());
        double result = BreakthroughService.successChance(
                demanding, cultivator(92.0D, Meridian.count()), 1.0D, TUNING);
        assertTrue(Double.isFinite(result), "chance must be finite, got " + result);
        assertTrue(result >= TUNING.minChance() && result <= TUNING.maxChance());
    }

    @Test
    void failingAWellPreparedAttemptIsGentlerThanFailingAGamble() {
        assertEquals(DeviationSeverity.MINOR_BLOCKAGE,
                BreakthroughService.severityForFailedAttempt(0.90D, TUNING));
        assertEquals(DeviationSeverity.MINOR_BLOCKAGE,
                BreakthroughService.severityForFailedAttempt(0.60D, TUNING));
        assertEquals(DeviationSeverity.REVERSE_FLOW,
                BreakthroughService.severityForFailedAttempt(0.45D, TUNING));
        assertEquals(DeviationSeverity.SHATTERED_MERIDIAN,
                BreakthroughService.severityForFailedAttempt(0.10D, TUNING));
    }

    @Test
    void failureSeverityNeverEasesAsTheOddsWorsen() {
        int previousRank = 0;
        for (double chance = 1.0D; chance >= 0.0D; chance -= 0.05D) {
            int rank = BreakthroughService.severityForFailedAttempt(chance, TUNING).rank();
            assertTrue(rank >= previousRank,
                    "severity eased at chance " + chance + " (" + rank + " < " + previousRank + ")");
            previousRank = rank;
        }
        assertEquals(DeviationSeverity.SHATTERED_MERIDIAN.rank(), previousRank,
                "the worst odds should end at the worst severity");
    }
}
