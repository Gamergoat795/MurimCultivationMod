package com.andymods.murimcultivation.cultivation;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the parts of {@link QiDensity} that are pure arithmetic.
 *
 * <p>The biome, time and weather factors need a live {@code Level} and are exercised in game
 * instead; altitude and the quality banding are testable here, and they are the two most
 * likely to be quietly broken by a tuning change.
 */
class QiDensityTest {

    @Test
    void seaLevelGetsNoAltitudeBonus() {
        assertEquals(1.0D, QiDensity.altitudeMultiplier(new BlockPos(0, 63, 0)), 1.0e-9D);
    }

    @Test
    void heightAndDepthBothHelp() {
        double peak = QiDensity.altitudeMultiplier(new BlockPos(0, 200, 0));
        double cave = QiDensity.altitudeMultiplier(new BlockPos(0, -40, 0));
        double plain = QiDensity.altitudeMultiplier(new BlockPos(0, 63, 0));

        assertTrue(peak > plain, "a mountain peak should beat sea level");
        assertTrue(cave > plain, "a deep cave should beat sea level");
    }

    @Test
    void equalDistancesAboveAndBelowAreWorthTheSame() {
        double above = QiDensity.altitudeMultiplier(new BlockPos(0, 63 + 50, 0));
        double below = QiDensity.altitudeMultiplier(new BlockPos(0, 63 - 50, 0));
        assertEquals(above, below, 1.0e-9D, "altitude is measured as distance, not direction");
    }

    @Test
    void altitudeBonusIsCapped() {
        // Absurd heights must not produce an unbounded multiplier.
        double extreme = QiDensity.altitudeMultiplier(new BlockPos(0, 100000, 0));
        double high = QiDensity.altitudeMultiplier(new BlockPos(0, 5000, 0));
        assertEquals(extreme, high, 1.0e-9D, "the bonus should have flattened out well before this");
        assertTrue(extreme < 2.0D, "a capped altitude bonus should stay modest, got " + extreme);
    }

    @Test
    void altitudeMultiplierIsMonotonicWithDistance() {
        double previous = 0.0D;
        for (int y = 63; y <= 320; y += 16) {
            double current = QiDensity.altitudeMultiplier(new BlockPos(0, y, 0));
            assertTrue(current >= previous, "altitude bonus must never decrease as you climb");
            previous = current;
        }
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
