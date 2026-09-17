package com.andymods.murimcultivation.technique;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mastery economy.
 *
 * <p>The intent these pin down: practice must always be worth something, full mastery must be
 * actually reachable rather than an asymptote, and mastery must make an art cheaper and faster
 * without ever making it free or instant.
 */
class TechniqueMasteryTest {

    /** The shipped defaults. */
    private static final TechniqueMastery.Tuning TUNING =
            new TechniqueMastery.Tuning(1.5D, 0.12D, 0.40D, 0.35D);

    @Test
    void gainsDiminishAsMasteryRises() {
        double atStart = TechniqueMastery.gainFrom(0, 1.0D, TUNING);
        double halfway = TechniqueMastery.gainFrom(50, 1.0D, TUNING);
        double nearlyThere = TechniqueMastery.gainFrom(95, 1.0D, TUNING);

        assertTrue(atStart > halfway, "early practice should teach more than late practice");
        assertTrue(halfway > nearlyThere, "gains should keep shrinking");
        assertEquals(1.0D, atStart, 1.0e-9D, "at zero mastery the full base gain applies");
    }

    @Test
    void gainsNeverReachZeroBeforeTheMaximum() {
        // Without a floor, full mastery is an asymptote a player approaches but never touches.
        for (int mastery = 0; mastery < TechniqueMastery.MAX; mastery++) {
            double gain = TechniqueMastery.gainFrom(mastery, 1.0D, TUNING);
            assertTrue(gain > 0.0D, "gain hit zero at mastery " + mastery);
            assertTrue(gain >= TUNING.minGainFraction() - 1.0e-9D,
                    "gain fell below the floor at mastery " + mastery + ": " + gain);
        }
    }

    @Test
    void fullMasteryIsActuallyReachable() {
        // Simulate practising from nothing. This must terminate.
        double mastery = 0.0D;
        int uses = 0;
        while (mastery < TechniqueMastery.MAX && uses < 100000) {
            mastery = Math.min(TechniqueMastery.MAX,
                    mastery + TechniqueMastery.gainFrom((int) Math.floor(mastery), 1.0D, TUNING));
            uses++;
        }
        assertEquals(TechniqueMastery.MAX, mastery, 1.0e-9D,
                "full mastery was not reached in " + uses + " uses");
        assertTrue(uses < 100000, "reaching full mastery should be a grind, not impossible");
        assertTrue(uses > 100, "reaching full mastery should take real practice, took " + uses);
    }

    @Test
    void noGainAtMaximum() {
        assertEquals(0.0D, TechniqueMastery.gainFrom(TechniqueMastery.MAX, 1.0D, TUNING), 1.0e-9D);
    }

    @Test
    void aZeroBaseGainTeachesNothing() {
        assertEquals(0.0D, TechniqueMastery.gainFrom(0, 0.0D, TUNING), 1.0e-9D);
    }

    @Test
    void masteryMakesTechniquesCheaperButNeverFree() {
        double raw = 100.0D;
        double untrained = TechniqueMastery.qiCost(raw, 0, TUNING);
        double mastered = TechniqueMastery.qiCost(raw, TechniqueMastery.MAX, TUNING);

        assertEquals(raw, untrained, 1.0e-9D, "an untrained art costs its full price");
        assertEquals(raw * (1.0D - TUNING.qiCostReductionAtFull()), mastered, 1.0e-9D);
        assertTrue(mastered > 0.0D, "a technique must never become free");
    }

    @Test
    void masteryShortensCooldownsButNeverToNothing() {
        int raw = 100;
        assertEquals(raw, TechniqueMastery.cooldownTicks(raw, 0, TUNING));
        int mastered = TechniqueMastery.cooldownTicks(raw, TechniqueMastery.MAX, TUNING);
        assertTrue(mastered < raw, "mastery should shorten the cooldown");
        assertTrue(mastered >= 1, "a cooldown must never reach zero ticks");
    }

    @Test
    void aTechniqueWithNoCooldownStaysWithoutOne() {
        // Rounding up must not invent a cooldown for an art that declared none.
        assertEquals(0, TechniqueMastery.cooldownTicks(0, 0, TUNING));
        assertEquals(0, TechniqueMastery.cooldownTicks(0, TechniqueMastery.MAX, TUNING));
    }

    @Test
    void costAndCooldownFallMonotonicallyWithMastery() {
        double previousCost = Double.MAX_VALUE;
        int previousCooldown = Integer.MAX_VALUE;
        for (int mastery = 0; mastery <= TechniqueMastery.MAX; mastery += 5) {
            double cost = TechniqueMastery.qiCost(50.0D, mastery, TUNING);
            int cooldown = TechniqueMastery.cooldownTicks(80, mastery, TUNING);
            assertTrue(cost <= previousCost, "cost rose at mastery " + mastery);
            assertTrue(cooldown <= previousCooldown, "cooldown rose at mastery " + mastery);
            previousCost = cost;
            previousCooldown = cooldown;
        }
    }

    @Test
    void damageScalesFromTheTechniquesOwnCurve() {
        TechniquePower power = new TechniquePower(
                6.0D, 0.14D, 18.0D, 0.0D, 0, 0, 0.0D, 0.0D, 1.0D, 0.0D);

        assertEquals(6.0D, TechniqueMastery.damage(power, 0), 1.0e-9D);
        assertEquals(6.0D + 0.14D * 100, TechniqueMastery.damage(power, 100), 1.0e-9D);
        assertTrue(TechniqueMastery.damage(power, 100) > TechniqueMastery.damage(power, 50));
    }

    @Test
    void masteryIsClampedAtBothEnds() {
        assertEquals(TechniqueMastery.MIN, TechniqueMastery.clamp(-50));
        assertEquals(TechniqueMastery.MAX, TechniqueMastery.clamp(500));
        // Out-of-range mastery must not produce a negative cost or a cooldown below the floor.
        assertTrue(TechniqueMastery.qiCost(50.0D, 500, TUNING) > 0.0D);
        assertTrue(TechniqueMastery.cooldownTicks(80, 500, TUNING) >= 1);
    }

    @Test
    void starsSpanZeroToFour() {
        assertEquals(0, TechniqueMastery.stars(0));
        assertEquals(0, TechniqueMastery.stars(24));
        assertEquals(1, TechniqueMastery.stars(25));
        assertEquals(3, TechniqueMastery.stars(99));
        assertEquals(4, TechniqueMastery.stars(100));
    }
}
