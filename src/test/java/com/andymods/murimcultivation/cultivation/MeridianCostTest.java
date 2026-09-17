package com.andymods.murimcultivation.cultivation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The meridian network's cost and risk curves.
 *
 * <p>These pin down the intent that opening the network is a series of escalating decisions:
 * each node costs more than the last, extraordinary vessels cost and risk far more than primary
 * meridians, and a refined foundation is the thing that makes the whole project survivable.
 */
class MeridianCostTest {

    /** The shipped defaults. */
    private static final MeridianService.Tuning TUNING =
            new MeridianService.Tuning(0.75D, 0.12D, 0.12D, 0.30D, 0.85D, 25.0D);

    private static final double REALM_SCALE = 400.0D;

    private static double cost(int openNodes, Meridian meridian) {
        return MeridianService.costToOpen(REALM_SCALE, openNodes, meridian, TUNING);
    }

    @Test
    void theFirstNodeCostsTheBaseAmount() {
        // 400 progress-per-substage * 0.75 * 1.0 (primary) * 1.0 (nothing open yet)
        assertEquals(400.0D * 0.75D, cost(0, Meridian.LUNG), 1.0e-9D);
    }

    @Test
    void eachAlreadyOpenNodeMakesTheNextMoreExpensive() {
        double previous = 0.0D;
        for (int open = 0; open < Meridian.count(); open++) {
            double current = cost(open, Meridian.LUNG);
            assertTrue(current > previous,
                    "cost must rise with " + open + " nodes already open");
            previous = current;
        }
    }

    @Test
    void theTwentiethNodeIsAProjectComparedToTheFirst() {
        double first = cost(0, Meridian.LUNG);
        double last = cost(19, Meridian.LUNG);
        assertTrue(last > first * 3.0D,
                "finishing the network should cost several times the first step, got "
                        + last + " vs " + first);
    }

    @Test
    void extraordinaryVesselsCostMoreThanPrimaryMeridians() {
        double primary = cost(6, Meridian.HEART);
        double vessel = cost(6, Meridian.GOVERNING_VESSEL);
        assertTrue(vessel > primary, "vessels are the hard part of the network");
        assertEquals(primary * Meridian.GOVERNING_VESSEL.openingCostMultiplier(), vessel, 1.0e-9D);
    }

    @Test
    void costScalesWithTheRealmSoItStaysProportionate() {
        double atThirdRate = MeridianService.costToOpen(40.0D, 3, Meridian.LUNG, TUNING);
        double atFlowerRealm = MeridianService.costToOpen(6000.0D, 3, Meridian.LUNG, TUNING);
        assertTrue(atFlowerRealm > atThirdRate,
                "a higher realm's nodes should cost more in absolute progress");
        // The ratio should track the realm scale exactly: the cost is denominated in substages.
        assertEquals(6000.0D / 40.0D, atFlowerRealm / atThirdRate, 1.0e-9D);
    }

    @Test
    void everyMeridianHasAFiniteAndPositiveCost() {
        for (Meridian meridian : Meridian.values()) {
            double value = cost(5, meridian);
            assertTrue(Double.isFinite(value) && value > 0.0D, meridian + " cost: " + value);
        }
    }

    @Test
    void purityIsTheDefenceAgainstDeviation() {
        double reckless = MeridianService.deviationChance(0.0D, Meridian.LUNG, TUNING);
        double careful = MeridianService.deviationChance(100.0D, Meridian.LUNG, TUNING);

        assertEquals(0.12D, reckless, 1.0e-9D, "zero purity means the full base risk");
        assertTrue(careful < reckless, "a refined foundation must reduce the risk");
        // 85% protection at full purity leaves 15% of the base risk.
        assertEquals(0.12D * 0.15D, careful, 1.0e-9D);
    }

    @Test
    void vesselsAreRiskierThanMeridiansAtEveryPurity() {
        for (double purity = 0.0D; purity <= 100.0D; purity += 10.0D) {
            double primary = MeridianService.deviationChance(purity, Meridian.LIVER, TUNING);
            double vessel = MeridianService.deviationChance(purity, Meridian.THRUSTING_VESSEL, TUNING);
            assertTrue(vessel > primary, "vessel risk should exceed meridian risk at purity " + purity);
        }
    }

    @Test
    void deviationChanceFallsMonotonicallyWithPurityAndNeverGoesNegative() {
        double previous = Double.MAX_VALUE;
        for (double purity = 0.0D; purity <= 100.0D; purity += 5.0D) {
            double current = MeridianService.deviationChance(purity, Meridian.LUNG, TUNING);
            assertTrue(current <= previous, "risk rose at purity " + purity);
            assertTrue(current >= 0.0D, "risk must never be negative, got " + current);
            previous = current;
        }
    }

    @Test
    void fullProtectionCanBeConfiguredToRemoveRiskEntirely() {
        MeridianService.Tuning safe =
                new MeridianService.Tuning(0.75D, 0.12D, 0.12D, 0.30D, 1.0D, 25.0D);
        assertEquals(0.0D, MeridianService.deviationChance(100.0D, Meridian.LUNG, safe), 1.0e-9D,
                "purityProtection 1.0 should make a perfect foundation completely safe");
    }
}
