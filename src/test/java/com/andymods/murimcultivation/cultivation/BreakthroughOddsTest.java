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
        return target(0.70D);
    }

    /**
     * The same realm with a chosen base chance. Needed because the odds are clamped at 0.95:
     * a term's full contribution is only observable if the sum stays under the ceiling.
     */
    private static Realm target(double baseChance) {
        return new Realm(4, "murimcultivation.realm.peak", "절정",
                1000.0D, 560.0D, 5.0D, 50.0D, 5, baseChance, 3, 0x7E57C2, List.of());
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
        return chance(target(), purity, openMeridians, density);
    }

    private static double chance(Realm target, double purity, int openMeridians, double density) {
        return BreakthroughService.successChance(target, cultivator(purity, openMeridians), density, TUNING);
    }

    @Test
    void scrapingInAtTheMinimumGivesTheRealmsBaseChance() {
        // Exactly at the purity floor, exactly the required meridians, ordinary location:
        // no bonuses apply, so the odds are the realm's own.
        assertEquals(0.70D, chance(50.0D, 5, 1.0D), 1.0e-9D);
    }

    @Test
    void refiningPurityBeyondTheFloorImprovesTheOdds() {
        // A low base chance keeps the sum under the 0.95 ceiling, so the purity term's full
        // contribution is actually observable rather than clamped away.
        Realm modest = target(0.40D);
        double scraped = chance(modest, 50.0D, 5, 1.0D);
        double refined = chance(modest, 75.0D, 5, 1.0D);
        double perfect = chance(modest, 100.0D, 5, 1.0D);

        assertTrue(refined > scraped, "purity above the floor must help");
        assertTrue(perfect > refined, "more purity must keep helping");
        // At full purity the whole purity weight is earned: 0.40 + 0.35.
        assertEquals(0.75D, perfect, 1.0e-9D);
        // Halfway up the headroom earns half of it.
        assertEquals(0.40D + 0.175D, refined, 1.0e-9D);
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
    void theCeilingStopsABreakthroughEverBecomingAFormality() {
        // Perfect preparation on the default realm sums to 0.70 + 0.35 + 0.20 + 0.30 = 1.55.
        assertEquals(0.95D, chance(100.0D, Meridian.count(), 3.0D), 1.0e-9D,
                "a breakthrough must never be certain, however well prepared");
    }

    @Test
    void theFloorStopsADesperateAttemptEverBeingImpossible() {
        // The floor only bites where the terms actually drive the chance below it, which needs
        // a realm whose base odds are already long. Beyond Heaven's are 0.10; a barren location
        // subtracts 0.15, taking the raw value negative.
        Realm desperate = target(0.10D);
        assertEquals(0.05D, chance(desperate, 0.0D, 0, 0.0D), 1.0e-9D,
                "a desperate attempt must never be truly hopeless");
    }

    @Test
    void anOrdinaryAttemptSitsWellInsideBothBounds() {
        // Guards against a tuning change that accidentally pins normal play to a clamp.
        double ordinary = chance(60.0D, 7, 1.0D);
        assertTrue(ordinary > TUNING.minChance() && ordinary < TUNING.maxChance(),
                "a typical prepared attempt should be decided by the formula, not by a clamp: "
                        + ordinary);
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

    // --- the circulation bonus ---------------------------------------------------------

    @Test
    void circulatingWellImprovesTheOdds() {
        Realm realm = target(0.40D);
        CultivationData data = cultivator(50.0D, 5);
        double without = BreakthroughService.successChance(realm, data, 1.0D, 0.0D, TUNING);
        double with = BreakthroughService.successChance(realm, data, 1.0D, 0.15D, TUNING);
        assertEquals(without + 0.15D, with, 1.0e-9D);
    }

    @Test
    void theBonusCannotPushAHopelessAttemptPastTheCeiling() {
        // The whole reason the bonus is added inside the formula rather than at the call site: a
        // flawless circulation must reward preparation, not replace it. A realm with a 0.94 base
        // chance plus a full bonus still lands on the 0.95 ceiling, not above it.
        double result = BreakthroughService.successChance(
                target(0.94D), cultivator(100.0D, 20), 3.0D, 1.0D, TUNING);
        assertEquals(TUNING.maxChance(), result, 1.0e-9D);
    }

    @Test
    void aBotchedCirculationLeavesTheOddsExactlyAsTheyWere() {
        // Scoring zero must be identical to the old single-phase behaviour, so a player who
        // ignores the sweeps is no worse off than before the mechanic existed.
        Realm realm = target(0.55D);
        CultivationData data = cultivator(70.0D, 8);
        assertEquals(BreakthroughService.successChance(realm, data, 1.2D, TUNING),
                BreakthroughService.successChance(realm, data, 1.2D, 0.0D, TUNING), 1.0e-9D);
    }

    @Test
    void theDisplayedOddsAreTheUnbonusedOnes() {
        // successChanceForNextRealm feeds the System window and /murim chance, and is read before
        // any circulation has happened — so it must not promise a bonus the player has not earned.
        // The four-argument overload is what that path uses, and it passes zero.
        Realm realm = target(0.50D);
        CultivationData data = cultivator(60.0D, 6);
        double displayed = BreakthroughService.successChance(realm, data, 1.0D, TUNING);
        double earned = BreakthroughService.successChance(realm, data, 1.0D, 0.15D, TUNING);
        assertTrue(earned > displayed,
                "the bonus must be invisible to the display path but real at resolution");
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
