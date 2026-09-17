package com.andymods.murimcultivation.cultivation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeridianTest {

    @Test
    void thereAreTwelvePrimaryAndEightExtraordinary() {
        assertEquals(12, Meridian.PRIMARY_MERIDIANS.size(), "twelve primary meridians");
        assertEquals(8, Meridian.EXTRAORDINARY_VESSELS.size(), "eight extraordinary vessels");
        assertEquals(20, Meridian.count(), "twenty nodes in total");
        assertEquals(Meridian.count(),
                Meridian.PRIMARY_MERIDIANS.size() + Meridian.EXTRAORDINARY_VESSELS.size(),
                "the two lists must together cover every meridian");
    }

    @Test
    void theListsAgreeWithEachMeridiansOwnKind() {
        for (Meridian meridian : Meridian.PRIMARY_MERIDIANS) {
            assertFalse(meridian.isExtraordinary(), meridian + " is listed as primary");
            assertEquals(Meridian.Kind.PRIMARY, meridian.kind());
        }
        for (Meridian meridian : Meridian.EXTRAORDINARY_VESSELS) {
            assertTrue(meridian.isExtraordinary(), meridian + " is listed as extraordinary");
            assertEquals(Meridian.Kind.EXTRAORDINARY, meridian.kind());
        }
    }

    @Test
    void extraordinaryVesselsCostMoreToOpen() {
        for (Meridian meridian : Meridian.values()) {
            double multiplier = meridian.openingCostMultiplier();
            assertTrue(multiplier >= 1.0D, meridian + " cost multiplier must be at least 1");
            if (meridian.isExtraordinary()) {
                assertTrue(multiplier > 1.0D, meridian + " should cost more than a primary meridian");
            }
        }
    }

    @Test
    void serializedNamesAreUniqueAndLowercase() {
        long distinct = Arrays.stream(Meridian.values()).map(Meridian::getSerializedName).distinct().count();
        assertEquals(Meridian.values().length, distinct, "serialized names must be unique");
        for (Meridian meridian : Meridian.values()) {
            String name = meridian.getSerializedName();
            assertEquals(name.toLowerCase(Locale.ROOT), name, "must be lowercase: " + name);
            assertTrue(meridian.translationKey().endsWith(name));
        }
    }
}
