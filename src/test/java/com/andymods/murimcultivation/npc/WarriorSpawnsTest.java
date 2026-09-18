package com.andymods.murimcultivation.npc;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spawn distribution: that every roll lands somewhere, and that the curve claims what it does.
 *
 * <p>This is the whole reason the roll was written as a function of a pressure value and a number
 * from 0 to 99 rather than reaching for a {@code RandomSource} and a {@code Level}. The interesting
 * properties are distributional — the wilds hold fewer thugs and more masters — and there is no way
 * to check that by spawning things in a world.
 */
class WarriorSpawnsTest {

    private static final WarriorSpawns.Tuning TUNING = new WarriorSpawns.Tuning(
            35, // qiRichPressure
            20, // neutralPressure
            5,  // qiBarrenPressure
            1,  // pressurePerHundredBlocks
            45, // maxDistancePressure
            5); // maxNaturalRealmTier

    /** How many of the hundred rolls fall into each band at a given pressure. */
    private static Map<WarriorTier, Integer> distribution(int pressure) {
        Map<WarriorTier, Integer> counts = new EnumMap<>(WarriorTier.class);
        for (WarriorTier tier : WarriorTier.values()) {
            counts.put(tier, 0);
        }
        for (int roll = 0; roll < 100; roll++) {
            counts.merge(WarriorSpawns.bandFor(pressure, roll), 1, Integer::sum);
        }
        return counts;
    }

    // --- The distribution -------------------------------------------------------------

    @Test
    void everyRollLandsInExactlyOneBandAtEveryPressure() {
        // The bug this guards against is a sliver of the range assigned to nothing, which would
        // show up in play as a band that never spawns.
        for (int pressure = 0; pressure <= 100; pressure++) {
            int total = distribution(pressure).values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(100, total, "pressure " + pressure + " does not account for all 100 rolls");
        }
    }

    @Test
    void theWildsHoldFewerThugs() {
        // Monotonic, not merely lower at the ends: a dip in the middle would mean some band of
        // distances was quietly safer than the one before it.
        int previous = Integer.MAX_VALUE;
        for (int pressure = 0; pressure <= 100; pressure++) {
            int thugs = distribution(pressure).get(WarriorTier.THUG);
            assertTrue(thugs <= previous,
                    "thug share rose from " + previous + " to " + thugs + " at pressure " + pressure);
            previous = thugs;
        }
    }

    @Test
    void theWildsHoldMoreMasters() {
        int previous = Integer.MIN_VALUE;
        for (int pressure = 0; pressure <= 100; pressure++) {
            int masters = distribution(pressure).get(WarriorTier.MASTER);
            assertTrue(masters >= previous,
                    "master share fell from " + previous + " to " + masters + " at pressure " + pressure);
            previous = masters;
        }
    }

    @Test
    void theCalmEndIsMostlyThugsAndTheWildEndIsNot() {
        Map<WarriorTier, Integer> calm = distribution(0);
        assertEquals(60, calm.get(WarriorTier.THUG));
        assertEquals(1, calm.get(WarriorTier.MASTER), "a Master should be a once-in-a-hundred sight");

        Map<WarriorTier, Integer> wild = distribution(100);
        assertEquals(10, wild.get(WarriorTier.THUG));
        assertEquals(20, wild.get(WarriorTier.MASTER));
    }

    @Test
    void aMasterIsNeverTheCommonCase() {
        // However remote, most of what you meet should still be beneath you. A curve that inverted
        // would make the far reaches unplayable rather than dangerous.
        for (int pressure = 0; pressure <= 100; pressure++) {
            Map<WarriorTier, Integer> counts = distribution(pressure);
            assertTrue(counts.get(WarriorTier.MASTER) < 50,
                    "masters are the majority at pressure " + pressure);
        }
    }

    @Test
    void rollsOutsideTheRangeAreClampedRatherThanThrowing() {
        // The roll and the pressure both come from callers that could hand over anything.
        assertEquals(WarriorTier.THUG, WarriorSpawns.bandFor(0, -50));
        assertEquals(WarriorTier.MASTER, WarriorSpawns.bandFor(0, 5000));
        assertEquals(distribution(0), distribution(-20));
        assertEquals(distribution(100), distribution(900));
    }

    // --- Pressure ---------------------------------------------------------------------

    @Test
    void biomeSetsTheFloorAndQiRichIsTheDangerousOne() {
        assertEquals(35, WarriorSpawns.pressure(true, false, 0.0D, TUNING));
        assertEquals(20, WarriorSpawns.pressure(false, false, 0.0D, TUNING));
        assertEquals(5, WarriorSpawns.pressure(false, true, 0.0D, TUNING));
    }

    @Test
    void remotenessRaisesTheStakesUpToACeiling() {
        assertEquals(20, WarriorSpawns.pressure(false, false, 0.0D, TUNING));
        assertEquals(40, WarriorSpawns.pressure(false, false, 2000.0D, TUNING));
        assertEquals(65, WarriorSpawns.pressure(false, false, 4500.0D, TUNING));
        assertEquals(65, WarriorSpawns.pressure(false, false, 100000.0D, TUNING),
                "distance must stop contributing once the allowance is spent");
    }

    @Test
    void pressureStaysInRangeForHostileInputs() {
        // A world's spawn point can move and a distance can arrive as anything at all; a NaN here
        // would silently collapse the curve rather than fail loudly, so it is pinned.
        assertEquals(20, WarriorSpawns.pressure(false, false, Double.NaN, TUNING));
        assertEquals(20, WarriorSpawns.pressure(false, false, -5000.0D, TUNING));
        assertEquals(65, WarriorSpawns.pressure(false, false, Double.POSITIVE_INFINITY, TUNING));

        WarriorSpawns.Tuning extreme = new WarriorSpawns.Tuning(100, 100, 100, 100, 100, 5);
        assertEquals(100, WarriorSpawns.pressure(true, false, 9999.0D, extreme));
    }

    @Test
    void qiRichWinsWhenBothTagsSomehowApply() {
        // Nothing stops a datapack putting a biome in both lists. Picking the dangerous reading is
        // the safe default, and it should be a decision rather than whichever branch came first.
        assertEquals(35, WarriorSpawns.pressure(true, true, 0.0D, TUNING));
    }

    // --- Bands and realms -------------------------------------------------------------

    @Test
    void eachBandRollsOnlyRealmsInsideItself() {
        for (WarriorTier band : WarriorTier.values()) {
            for (int roll = 0; roll < 64; roll++) {
                int realmTier = WarriorSpawns.realmTierFor(band, roll, TUNING);
                assertTrue(band.covers(realmTier),
                        band + " rolled realm tier " + realmTier + ", outside its own band");
            }
        }
    }

    @Test
    void theWandererBandUsesItsWholeSpan() {
        boolean sawSecondRate = false;
        boolean sawFirstRate = false;
        for (int roll = 0; roll < 64; roll++) {
            int realmTier = WarriorSpawns.realmTierFor(WarriorTier.WANDERER, roll, TUNING);
            sawSecondRate |= realmTier == 2;
            sawFirstRate |= realmTier == 3;
        }
        assertTrue(sawSecondRate && sawFirstRate, "a two-realm band should produce both of them");
    }

    @Test
    void theCapKeepsWildSpawnsOffTheTopOfTheLadder() {
        // The shipped ladder reaches tier 9, whose grants are +80 health and +30 attack damage.
        WarriorSpawns.Tuning capped = new WarriorSpawns.Tuning(35, 20, 5, 1, 45, 3);
        for (WarriorTier band : WarriorTier.values()) {
            for (int roll = 0; roll < 32; roll++) {
                assertTrue(WarriorSpawns.realmTierFor(band, roll, capped) <= 3,
                        band + " exceeded the configured cap");
            }
        }
    }

    @Test
    void aNegativeRollStillYieldsARealmInsideTheBand() {
        for (WarriorTier band : WarriorTier.values()) {
            int realmTier = WarriorSpawns.realmTierFor(band, -7, TUNING);
            assertTrue(band.covers(realmTier), band + " mishandled a negative roll");
        }
    }

    // --- The tier labels --------------------------------------------------------------

    @Test
    void theBandsCoverTheLadderWithoutOverlapping() {
        for (int realmTier = 1; realmTier <= 5; realmTier++) {
            int covering = 0;
            for (WarriorTier band : WarriorTier.values()) {
                if (band.covers(realmTier)) {
                    covering++;
                }
            }
            assertEquals(1, covering, "realm tier " + realmTier + " is covered by " + covering + " bands");
        }
    }

    @Test
    void realmTiersAboveTheLadderSaturateAtMaster() {
        // A datapack may add realms above the nine shipped here, and a warrior placed at one still
        // has to be describable rather than throwing.
        assertEquals(WarriorTier.MASTER, WarriorTier.forRealmTier(9));
        assertEquals(WarriorTier.MASTER, WarriorTier.forRealmTier(500));
        assertEquals(WarriorTier.THUG, WarriorTier.forRealmTier(0),
                "and below the ladder too, rather than falling through");
        assertEquals(WarriorTier.THUG, WarriorTier.forRealmTier(-3));
    }

    @Test
    void tierNamesAreUniqueLowercaseAndNamespaced() {
        for (WarriorTier tier : WarriorTier.values()) {
            assertEquals(tier.getSerializedName().toLowerCase(Locale.ROOT),
                    tier.getSerializedName());
            assertTrue(tier.translationKey().startsWith("murimcultivation.warrior_tier."));
            assertFalse(tier.getSerializedName().isEmpty());
        }
        assertEquals(WarriorTier.values().length,
                Arrays.stream(WarriorTier.values())
                        .map(WarriorTier::getSerializedName).distinct().count());
    }
}
