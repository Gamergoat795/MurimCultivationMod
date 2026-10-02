package com.andymods.murimcultivation.sect;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sect-politics table, one row per test, plus the edges the table does not spell out. */
class SectConductTest {

    private static final SectConduct.Tuning TUNING = new SectConduct.Tuning(
            2,    // minimal
            10,   // small
            20,   // small-medium
            35,   // medium
            60);  // large

    private static final ResourceLocation MOUNT_HUA = id("mount_hua");
    private static final ResourceLocation SHAOLIN = id("shaolin");
    private static final ResourceLocation CULT = id("heavenly_demon_cult");
    private static final ResourceLocation BEGGARS = id("beggars_sect");

    private static final List<SectConduct.SectEntry> SECTS = List.of(
            new SectConduct.SectEntry(MOUNT_HUA, SectAlignment.ORTHODOX),
            new SectConduct.SectEntry(SHAOLIN, SectAlignment.ORTHODOX),
            new SectConduct.SectEntry(CULT, SectAlignment.DEMONIC),
            new SectConduct.SectEntry(BEGGARS, SectAlignment.NEUTRAL));

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("murimcultivation", path);
    }

    @Test
    void sparingAFairOpponentEarnsTheirSectsRespectAndNobodyElsesNotice() {
        assertEquals(Map.of(MOUNT_HUA, 10),
                SectConduct.deltas(SectConduct.Outcome.DUEL_SPARED, MOUNT_HUA, SECTS, TUNING));
    }

    @Test
    void killingAFairOpponentAfterTheyYieldPleasesTheirRivals() {
        assertEquals(Map.of(MOUNT_HUA, -10, SHAOLIN, 10, BEGGARS, 10, CULT, 20),
                SectConduct.deltas(SectConduct.Outcome.DUEL_KILLED, MOUNT_HUA, SECTS, TUNING));
    }

    @Test
    void murderFromAmbushCostsEveryoneButTheCult() {
        assertEquals(Map.of(MOUNT_HUA, -60, SHAOLIN, -2, BEGGARS, -2, CULT, 35),
                SectConduct.deltas(SectConduct.Outcome.AMBUSH_KILLED, MOUNT_HUA, SECTS, TUNING));
    }

    @Test
    void sparingSomeoneYouJumpedEarnsNothing() {
        assertTrue(SectConduct.deltas(SectConduct.Outcome.AMBUSH_SPARED, MOUNT_HUA, SECTS, TUNING).isEmpty());
    }

    @Test
    void theCultDoesNotCelebrateTheMurderOfItsOwn() {
        Map<ResourceLocation, Integer> deltas =
                SectConduct.deltas(SectConduct.Outcome.AMBUSH_KILLED, CULT, SECTS, TUNING);
        assertEquals(-60, deltas.get(CULT), "the victim's column wins over the demonic column");
        assertEquals(-2, deltas.get(MOUNT_HUA));
        assertEquals(-2, deltas.get(BEGGARS));
    }

    @Test
    void aSecondDemonicSectStillGetsTheDemonicColumn() {
        ResourceLocation offshoot = id("blood_sect");
        List<SectConduct.SectEntry> withOffshoot = List.of(
                new SectConduct.SectEntry(CULT, SectAlignment.DEMONIC),
                new SectConduct.SectEntry(offshoot, SectAlignment.DEMONIC),
                new SectConduct.SectEntry(SHAOLIN, SectAlignment.ORTHODOX));

        assertEquals(Map.of(CULT, -10, offshoot, 20, SHAOLIN, 10),
                SectConduct.deltas(SectConduct.Outcome.DUEL_KILLED, CULT, withOffshoot, TUNING));
    }

    @Test
    void aZeroTuningMovesNothing() {
        SectConduct.Tuning off = new SectConduct.Tuning(0, 0, 0, 0, 0);
        for (SectConduct.Outcome outcome : SectConduct.Outcome.values()) {
            assertTrue(SectConduct.deltas(outcome, MOUNT_HUA, SECTS, off).isEmpty(), outcome.name());
        }
    }

    @Test
    void outcomesAreNamedByFairnessAndFate() {
        assertEquals(SectConduct.Outcome.DUEL_SPARED, SectConduct.Outcome.of(true, false));
        assertEquals(SectConduct.Outcome.DUEL_KILLED, SectConduct.Outcome.of(true, true));
        assertEquals(SectConduct.Outcome.AMBUSH_SPARED, SectConduct.Outcome.of(false, false));
        assertEquals(SectConduct.Outcome.AMBUSH_KILLED, SectConduct.Outcome.of(false, true));
    }
}
