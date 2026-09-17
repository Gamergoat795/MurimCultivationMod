package com.andymods.murimcultivation.cultivation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the parts of {@link QiDensity} that are pure arithmetic.
 *
 * <p>Tuning values are passed explicitly rather than read from config: a {@code ModConfigSpec}
 * value throws until a world has loaded it, so a formula that reads config inline cannot be
 * tested at all. Passing them in also lets a test pin an exact expected number.
 */
class QiDensityTest {

    /** The shipped defaults, mirrored here so the assertions below are about real values. */
    private static final double BONUS_PER_BLOCK = 0.0035D;
    private static final double BONUS_CAP = 0.45D;

    private static double altitude(int y) {
        return QiDensity.altitudeMultiplier(y, BONUS_PER_BLOCK, BONUS_CAP);
    }

    @Test
    void seaLevelGetsNoAltitudeBonus() {
        assertEquals(1.0D, altitude(63), 1.0e-9D);
    }

    @Test
    void heightAndDepthBothHelp() {
        assertTrue(altitude(200) > altitude(63), "a mountain peak should beat sea level");
        assertTrue(altitude(-40) > altitude(63), "a deep cave should beat sea level");
    }

    @Test
    void equalDistancesAboveAndBelowAreWorthTheSame() {
        assertEquals(altitude(63 + 50), altitude(63 - 50), 1.0e-9D,
                "altitude is measured as distance, not direction");
    }

    @Test
    void theBonusIsExactlyDistanceTimesRateBelowTheCap() {
        // 20 blocks up at 0.0035/block is 0.07, comfortably under the 0.45 cap.
        assertEquals(1.0D + 20 * BONUS_PER_BLOCK, altitude(83), 1.0e-9D);
    }

    @Test
    void altitudeBonusIsCapped() {
        assertEquals(1.0D + BONUS_CAP, altitude(100000), 1.0e-9D,
                "an absurd height must saturate at the cap, not run away");
        assertEquals(altitude(100000), altitude(5000), 1.0e-9D,
                "both should already be saturated");
    }

    @Test
    void altitudeMultiplierIsMonotonicWithDistance() {
        double previous = 0.0D;
        for (int y = 63; y <= 320; y += 16) {
            double current = altitude(y);
            assertTrue(current >= previous, "altitude bonus must never decrease as you climb");
            previous = current;
        }
    }

    @Test
    void aZeroRateDisablesTheAltitudeBonusEntirely() {
        // Server owners must be able to switch the mechanic off.
        assertEquals(1.0D, QiDensity.altitudeMultiplier(320, 0.0D, BONUS_CAP), 1.0e-9D);
    }

    @Test
    void qualityBandsAreOrderedAndCoverTheRange() {
        assertEquals(QiDensity.Quality.BARREN, QiDensity.qualityOf(0.5D));
        assertEquals(QiDensity.Quality.ORDINARY, QiDensity.qualityOf(1.0D));
        assertEquals(QiDensity.Quality.RICH, QiDensity.qualityOf(1.4D));
        assertEquals(QiDensity.Quality.SPIRIT_VEIN, QiDensity.qualityOf(2.0D));
    }

    @Test
    void qualityNeverRegressesAsDensityRises() {
        QiDensity.Quality previous = QiDensity.qualityOf(0.0D);
        for (double density = 0.0D; density <= 5.0D; density += 0.05D) {
            QiDensity.Quality current = QiDensity.qualityOf(density);
            assertTrue(current.ordinal() >= previous.ordinal(),
                    "quality dropped at density " + density + ": " + previous + " -> " + current);
            previous = current;
        }
    }

    @Test
    void everyQualityHasANamespacedTranslationKey() {
        for (QiDensity.Quality quality : QiDensity.Quality.values()) {
            assertTrue(quality.translationKey().startsWith("murimcultivation.qi_density."),
                    quality + " translation key: " + quality.translationKey());
        }
    }
}
