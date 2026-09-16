package com.andymods.murimcultivation.cultivation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviationSeverityTest {

    @Test
    void noneIsTheOnlyInactiveSeverity() {
        assertFalse(DeviationSeverity.NONE.isActive());
        for (DeviationSeverity severity : DeviationSeverity.values()) {
            if (severity != DeviationSeverity.NONE) {
                assertTrue(severity.isActive(), severity + " should be an active deviation");
            }
        }
    }

    @Test
    void severityRanksProgressLossAndDurationAllRiseTogether() {
        DeviationSeverity[] ordered = {
                DeviationSeverity.NONE,
                DeviationSeverity.MINOR_BLOCKAGE,
                DeviationSeverity.REVERSE_FLOW,
                DeviationSeverity.SHATTERED_MERIDIAN,
        };
        assertEquals(DeviationSeverity.values().length, ordered.length,
                "a new severity was added without extending this test");

        for (int i = 1; i < ordered.length; i++) {
            DeviationSeverity worse = ordered[i];
            DeviationSeverity milder = ordered[i - 1];
            assertTrue(worse.rank() > milder.rank(), worse + " must outrank " + milder);
            assertTrue(worse.progressLossFraction() > milder.progressLossFraction(),
                    worse + " must cost more progress than " + milder);
            assertTrue(worse.durationTicks() > milder.durationTicks(),
                    worse + " must last longer than " + milder);
        }
    }

    @Test
    void progressLossNeverExceedsEverything() {
        for (DeviationSeverity severity : DeviationSeverity.values()) {
            assertTrue(severity.progressLossFraction() >= 0.0D);
            assertTrue(severity.progressLossFraction() <= 1.0D,
                    severity + " cannot destroy more than all banked progress");
        }
    }

    @Test
    void onlyTheWorstSeverityForcesAMeridianShut() {
        for (DeviationSeverity severity : DeviationSeverity.values()) {
            assertEquals(severity == DeviationSeverity.SHATTERED_MERIDIAN, severity.closesMeridian(),
                    severity + ".closesMeridian()");
        }
    }
}
