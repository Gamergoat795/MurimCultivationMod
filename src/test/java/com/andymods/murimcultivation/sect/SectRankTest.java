package com.andymods.murimcultivation.sect;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sect rank ladder and the alignment rules that make taking a side mean something. */
class SectRankTest {

    @Test
    void thresholdsRiseWithRank() {
        SectRank[] ranks = SectRank.values();
        for (int i = 1; i < ranks.length; i++) {
            assertTrue(ranks[i].reputationRequired() > ranks[i - 1].reputationRequired(),
                    ranks[i] + " should demand more standing than " + ranks[i - 1]);
        }
    }

    @Test
    void onlyOutsiderIsANonMember() {
        assertFalse(SectRank.OUTSIDER.isMember());
        for (SectRank rank : SectRank.values()) {
            if (rank != SectRank.OUTSIDER) {
                assertTrue(rank.isMember(), rank + " should count as a member");
            }
        }
    }

    @Test
    void reputationMapsToTheHighestRankEarned() {
        assertSame(SectRank.OUTSIDER, SectRank.forReputation(0));
        assertSame(SectRank.OUTSIDER, SectRank.forReputation(99));
        assertSame(SectRank.OUTER_DISCIPLE, SectRank.forReputation(100));
        assertSame(SectRank.OUTER_DISCIPLE, SectRank.forReputation(399));
        assertSame(SectRank.INNER_DISCIPLE, SectRank.forReputation(400));
        assertSame(SectRank.PATRIARCH, SectRank.forReputation(1_000_000));
    }

    @Test
    void negativeReputationIsStillMerelyAnOutsider() {
        // Losing standing with an opposing sect can take reputation below zero; that must read
        // as "not a member" rather than wrapping to something unexpected.
        assertSame(SectRank.OUTSIDER, SectRank.forReputation(-5000));
    }

    @Test
    void rankNeverRegressesAsReputationRises() {
        int previous = -1;
        for (int reputation = 0; reputation <= 10000; reputation += 50) {
            int ordinal = SectRank.forReputation(reputation).ordinal();
            assertTrue(ordinal >= previous, "rank dropped at reputation " + reputation);
            previous = ordinal;
        }
    }

    @Test
    void nextWalksUpAndStopsAtTheTop() {
        assertSame(SectRank.OUTER_DISCIPLE, SectRank.OUTSIDER.next());
        assertSame(SectRank.PATRIARCH, SectRank.ELDER.next());
        assertNull(SectRank.PATRIARCH.next(), "there is nothing above the sect head");
    }

    @Test
    void joiningGrantsExactlyOuterDiscipleStanding() {
        // SectService.join adds OUTER_DISCIPLE.reputationRequired(); that must actually land on
        // the rank rather than one point short of it.
        assertSame(SectRank.OUTER_DISCIPLE,
                SectRank.forReputation(SectRank.OUTER_DISCIPLE.reputationRequired()));
    }

    @Test
    void orthodoxAndDemonicOpposeEachOtherAndNothingElse() {
        assertTrue(SectAlignment.ORTHODOX.opposes(SectAlignment.DEMONIC));
        assertTrue(SectAlignment.DEMONIC.opposes(SectAlignment.ORTHODOX));

        assertFalse(SectAlignment.ORTHODOX.opposes(SectAlignment.ORTHODOX));
        assertFalse(SectAlignment.ORTHODOX.opposes(SectAlignment.NEUTRAL));
        assertFalse(SectAlignment.NEUTRAL.opposes(SectAlignment.DEMONIC));
        assertFalse(SectAlignment.NEUTRAL.opposes(SectAlignment.NEUTRAL));
    }

    @Test
    void oppositionIsSymmetric() {
        // An asymmetric rule would mean joining in one order is allowed and the other is not.
        for (SectAlignment left : SectAlignment.values()) {
            for (SectAlignment right : SectAlignment.values()) {
                assertEquals(left.opposes(right), right.opposes(left),
                        left + " vs " + right + " must be symmetric");
            }
        }
    }

    @Test
    void serializedNamesAreUniqueAndLowercase() {
        for (Class<?> ignored : new Class<?>[]{SectRank.class}) {
            long distinct = Arrays.stream(SectRank.values())
                    .map(SectRank::getSerializedName).distinct().count();
            assertEquals(SectRank.values().length, distinct);
        }
        for (SectRank rank : SectRank.values()) {
            assertEquals(rank.getSerializedName().toLowerCase(Locale.ROOT), rank.getSerializedName());
            assertTrue(rank.translationKey().endsWith(rank.getSerializedName()));
        }
        for (SectAlignment alignment : SectAlignment.values()) {
            assertEquals(alignment.getSerializedName().toLowerCase(Locale.ROOT),
                    alignment.getSerializedName());
        }
    }
}
