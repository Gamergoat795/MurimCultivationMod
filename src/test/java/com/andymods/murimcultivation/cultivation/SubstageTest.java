package com.andymods.murimcultivation.cultivation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubstageTest {

    @Test
    void indicesAreContiguousFromZero() {
        Substage[] values = Substage.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].index(), values[i] + " should have index " + i);
        }
    }

    @Test
    void onlyPeakIsLast() {
        for (Substage substage : Substage.values()) {
            assertEquals(substage == Substage.PEAK, substage.isLast(), substage + ".isLast()");
        }
    }

    @Test
    void nextWalksTheLadderAndStopsAtPeak() {
        assertSame(Substage.MID, Substage.EARLY.next());
        assertSame(Substage.LATE, Substage.MID.next());
        assertSame(Substage.PEAK, Substage.LATE.next());
        // Peak has no next substage: the step beyond it is a breakthrough, not a substage.
        assertNull(Substage.PEAK.next());
    }

    @Test
    void byIndexClampsRatherThanThrowing() {
        assertSame(Substage.EARLY, Substage.byIndex(-5));
        assertSame(Substage.EARLY, Substage.byIndex(0));
        assertSame(Substage.PEAK, Substage.byIndex(Substage.count() - 1));
        assertSame(Substage.PEAK, Substage.byIndex(999));
    }

    @Test
    void serializedNamesAreUniqueAndLowercase() {
        long distinct = Arrays.stream(Substage.values())
                .map(Substage::getSerializedName)
                .distinct()
                .count();
        assertEquals(Substage.values().length, distinct, "serialized names must be unique");
        for (Substage substage : Substage.values()) {
            String name = substage.getSerializedName();
            assertEquals(name.toLowerCase(Locale.ROOT), name, "must be lowercase: " + name);
            assertFalse(name.isBlank());
            assertTrue(substage.translationKey().endsWith(name));
        }
    }
}
