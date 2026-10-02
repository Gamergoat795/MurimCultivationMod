package com.andymods.murimcultivation.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who gets hunted, how often, and by whom. */
class BountyServiceTest {

    private static final BountyService.Tuning TUNING = new BountyService.Tuning(
            true,   // enabled
            40,     // infamyThreshold
            6000,   // checkIntervalTicks
            0.35D,  // chancePerCheck
            80,     // infamyForStrongerHunter
            6);     // maxRealmTier

    @Test
    void theThresholdIsInclusive() {
        assertFalse(BountyService.isWanted(39, TUNING));
        assertTrue(BountyService.isWanted(40, TUNING));
    }

    @Test
    void aHunterIsSentOnlyWhenWantedAndTheDiceAgree() {
        assertTrue(BountyService.sendsHunter(50, 0.34D, TUNING));
        assertFalse(BountyService.sendsHunter(50, 0.35D, TUNING), "the chance is exclusive at its edge");
        assertFalse(BountyService.sendsHunter(39, 0.0D, TUNING), "nobody hunts the merely disliked");
    }

    @Test
    void turningItOffSendsNobodyWhateverTheirName() {
        BountyService.Tuning off = new BountyService.Tuning(false, 40, 6000, 1.0D, 80, 6);
        assertFalse(BountyService.isWanted(100, off));
        assertFalse(BountyService.sendsHunter(100, 0.0D, off));
    }

    @Test
    void theHunterMatchesYouUntilYouAreWorthMore() {
        assertEquals(3, BountyService.hunterRealmTier(3, 50, TUNING));
        assertEquals(4, BountyService.hunterRealmTier(3, 80, TUNING), "the notorious get someone better");
    }

    @Test
    void theHunterStaysInsideTheLadder() {
        assertEquals(1, BountyService.hunterRealmTier(0, 50, TUNING), "never below the first realm");
        assertEquals(6, BountyService.hunterRealmTier(9, 100, TUNING), "never above the cap");
        assertEquals(6, BountyService.hunterRealmTier(6, 100, TUNING));
    }
}
