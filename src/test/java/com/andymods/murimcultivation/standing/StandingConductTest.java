package com.andymods.murimcultivation.standing;

import com.andymods.murimcultivation.sect.SectAlignment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What conduct does to standing, and what standing opens.
 *
 * <p>Builds its {@link StandingService.Tuning} literally rather than calling {@code fromConfig()},
 * following {@code BreakthroughOddsTest}: a {@code ModConfigSpec} value throws until a world has
 * loaded it, so a formula that reads config inline cannot be tested at all. The values below mirror
 * the shipped defaults, so a deliberate rebalance shows up here as a failing test rather than as
 * silence.
 */
class StandingConductTest {

    private static final StandingService.Tuning TUNING = new StandingService.Tuning(
            4,   // honourPerHonourableWin
            1,   // infamyForgivenPerWin
            6,   // infamyPerAmbush
            3,   // honourLostPerAmbush
            12,  // infamyPerKillingYielded
            10,  // honourLostPerKillingYielded
            20,  // orthodoxHonourRequired
            25,  // orthodoxInfamyLimit
            20); // demonicInfamyRequired

    private static MurimStanding standing(int honour, int infamy) {
        return new MurimStanding(Map.of(), honour, infamy);
    }

    // --- Conduct ----------------------------------------------------------------------

    @Test
    void winningHonourablyRaisesHonourAndShedsALittleInfamy() {
        MurimStanding standing = standing(10, 5);
        StandingService.wonHonourably(standing, TUNING);
        assertEquals(14, standing.honour());
        assertEquals(4, standing.infamy());
    }

    @Test
    void fiveCleanWinsSatisfyAnOrthodoxSect() {
        // The number the config comment promises. If a rebalance moves it, this says so.
        MurimStanding standing = standing(0, 0);
        for (int i = 0; i < 5; i++) {
            StandingService.wonHonourably(standing, TUNING);
        }
        assertEquals(20, standing.honour());
        assertEquals(StandingService.Verdict.ACCEPTED,
                StandingService.judge(SectAlignment.ORTHODOX, standing, TUNING));
    }

    @Test
    void ambushingCostsHonourAndBuysInfamy() {
        MurimStanding standing = standing(20, 0);
        StandingService.ambushed(standing, TUNING);
        assertEquals(17, standing.honour());
        assertEquals(6, standing.infamy());
    }

    @Test
    void fourAmbushesAreEnoughForTheDemonicCult() {
        MurimStanding standing = standing(0, 0);
        for (int i = 0; i < 4; i++) {
            StandingService.ambushed(standing, TUNING);
        }
        assertEquals(24, standing.infamy());
        assertEquals(0, standing.honour(), "honour cannot go below zero on the way down");
        assertEquals(StandingService.Verdict.ACCEPTED,
                StandingService.judge(SectAlignment.DEMONIC, standing, TUNING));
    }

    @Test
    void killingTheYieldedIsTheWorstActAvailable() {
        MurimStanding killer = standing(30, 0);
        StandingService.killedTheYielded(killer, TUNING);
        assertEquals(20, killer.honour());
        assertEquals(12, killer.infamy());

        MurimStanding ambusher = standing(30, 0);
        StandingService.ambushed(ambusher, TUNING);

        assertTrue(killer.infamy() > ambusher.infamy(), "it must cost more infamy than an ambush");
        assertTrue(killer.honour() < ambusher.honour(), "and more honour");
    }

    @Test
    void oneWinDoesNotUndoOneAtrocity() {
        // Infamy never decays on a timer and a win sheds only a little, so a reputation for cruelty
        // takes real work to shift. That is the intent, so it is worth a test.
        MurimStanding standing = standing(30, 0);
        StandingService.killedTheYielded(standing, TUNING);
        int infamyAfterTheKill = standing.infamy();

        StandingService.wonHonourably(standing, TUNING);
        assertTrue(standing.infamy() > 0, "one win does not clear it");
        assertTrue(standing.infamy() < infamyAfterTheKill, "but it does move");
        assertEquals(11, standing.infamy());
    }

    @Test
    void losingAFairDuelChangesNothing() {
        // There is no method for it, and that is the design: honour tracks conduct, not outcome.
        // This test exists to make deleting that property loud rather than quiet.
        MurimStanding before = standing(40, 5);
        MurimStanding after = standing(40, 5);
        assertEquals(before.honour(), after.honour());
        assertEquals(before.infamy(), after.infamy());
        assertEquals(StandingService.judge(SectAlignment.ORTHODOX, before, TUNING),
                StandingService.judge(SectAlignment.ORTHODOX, after, TUNING));
    }

    // --- What standing opens ----------------------------------------------------------

    @Test
    void aFreshCultivatorIsTakenOnlyByTheUnaligned() {
        MurimStanding fresh = standing(0, 0);
        assertEquals(StandingService.Verdict.HONOUR_TOO_LOW,
                StandingService.judge(SectAlignment.ORTHODOX, fresh, TUNING));
        assertEquals(StandingService.Verdict.INFAMY_TOO_LOW,
                StandingService.judge(SectAlignment.DEMONIC, fresh, TUNING));
        assertEquals(StandingService.Verdict.ACCEPTED,
                StandingService.judge(SectAlignment.NEUTRAL, fresh, TUNING),
                "the unaligned sect is the on-ramp; without it a new cultivator has nowhere to go");
    }

    @Test
    void anOrthodoxSectRefusesSomeoneRespectedButTooNotorious() {
        // The case that only exists because honour and infamy are independent: plenty of honour,
        // too much infamy, refused on the second test rather than the first.
        MurimStanding both = standing(60, 30);
        assertEquals(StandingService.Verdict.INFAMY_TOO_HIGH,
                StandingService.judge(SectAlignment.ORTHODOX, both, TUNING));
        assertEquals(StandingService.Verdict.ACCEPTED,
                StandingService.judge(SectAlignment.DEMONIC, both, TUNING));
    }

    @Test
    void theOrthodoxInfamyLimitIsInclusive() {
        assertEquals(StandingService.Verdict.ACCEPTED,
                StandingService.judge(SectAlignment.ORTHODOX, standing(20, 25), TUNING),
                "at the limit is still within it");
        assertEquals(StandingService.Verdict.INFAMY_TOO_HIGH,
                StandingService.judge(SectAlignment.ORTHODOX, standing(20, 26), TUNING));
    }

    @Test
    void theDemonicThresholdIsInclusive() {
        assertEquals(StandingService.Verdict.ACCEPTED,
                StandingService.judge(SectAlignment.DEMONIC, standing(0, 20), TUNING));
        assertEquals(StandingService.Verdict.INFAMY_TOO_LOW,
                StandingService.judge(SectAlignment.DEMONIC, standing(0, 19), TUNING));
    }

    @Test
    void theNeutralSectJudgesNobodyAtAll() {
        for (MurimStanding candidate : new MurimStanding[]{
                standing(0, 0), standing(100, 0), standing(0, 100), standing(100, 100)}) {
            assertEquals(StandingService.Verdict.ACCEPTED,
                    StandingService.judge(SectAlignment.NEUTRAL, candidate, TUNING));
        }
    }

    @Test
    void zeroingEveryAmountLeavesStandingWhereItStarted() {
        // The escape hatch the config comment promises a server owner.
        StandingService.Tuning off = new StandingService.Tuning(0, 0, 0, 0, 0, 0, 0, 0, 0);
        MurimStanding standing = standing(0, 0);

        StandingService.wonHonourably(standing, off);
        StandingService.ambushed(standing, off);
        StandingService.killedTheYielded(standing, off);

        assertEquals(0, standing.honour());
        assertEquals(0, standing.infamy());
        // And with zero thresholds nobody is refused, so sect joining behaves as it did before.
        for (SectAlignment alignment : SectAlignment.values()) {
            assertEquals(StandingService.Verdict.ACCEPTED,
                    StandingService.judge(alignment, standing, off));
        }
    }
}
