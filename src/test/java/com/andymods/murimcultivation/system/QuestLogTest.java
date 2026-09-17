package com.andymods.murimcultivation.system;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quest log: counters, completion, and the daily reset cadence the user chose.
 */
class QuestLogTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("murimcultivation", path);
    }

    private static QuestObjective meditate(int seconds) {
        return new QuestObjective(ObjectiveKind.MEDITATE_SECONDS, seconds, Optional.empty());
    }

    private static QuestObjective cast(String technique, int times) {
        return new QuestObjective(ObjectiveKind.CAST_TECHNIQUE, times, Optional.of(id(technique)));
    }

    @Test
    void progressAccumulatesAndStopsAtTheTarget() {
        QuestLog log = new QuestLog();
        QuestObjective objective = meditate(100);

        assertEquals(0, log.progressOf(id("q"), objective));
        assertEquals(40, log.advance(id("q"), objective, 40));
        assertEquals(100, log.advance(id("q"), objective, 500),
                "progress must not run past the objective's target");
        assertEquals(100, log.progressOf(id("q"), objective));
    }

    @Test
    void nonPositiveAdvancesAreIgnored() {
        QuestLog log = new QuestLog();
        QuestObjective objective = meditate(50);
        log.advance(id("q"), objective, 10);
        assertEquals(10, log.advance(id("q"), objective, 0));
        assertEquals(10, log.advance(id("q"), objective, -5));
    }

    @Test
    void objectivesAreKeyedByKindAndTargetRatherThanByIndex() {
        // Reordering a quest's objectives in a datapack must not reassign a player's counters.
        QuestLog log = new QuestLog();
        QuestObjective qinggong = cast("qinggong", 10);
        QuestObjective swordQi = cast("sword_qi", 10);

        log.advance(id("q"), qinggong, 7);

        assertEquals(7, log.progressOf(id("q"), qinggong));
        assertEquals(0, log.progressOf(id("q"), swordQi),
                "a different target must have its own counter");
    }

    @Test
    void progressKeysDistinguishTargetsAndKinds() {
        assertEquals(cast("qinggong", 1).progressKey(), cast("qinggong", 99).progressKey(),
                "the amount is not part of the identity of an objective");
        assertFalse(cast("qinggong", 1).progressKey().equals(cast("sword_qi", 1).progressKey()));
        assertFalse(meditate(1).progressKey().equals(cast("qinggong", 1).progressKey()));
    }

    @Test
    void completingAStoryQuestIsPermanentAndClearsItsCounters() {
        QuestLog log = new QuestLog();
        QuestObjective objective = meditate(10);
        log.advance(id("story"), objective, 10);

        assertTrue(log.complete(id("story"), QuestCategory.STORY));
        assertTrue(log.isCompleted(id("story")));
        assertEquals(0, log.progressOf(id("story"), objective), "completion should clear counters");
        assertFalse(log.complete(id("story"), QuestCategory.STORY), "completing twice reports nothing new");
    }

    @Test
    void aDailyIsClaimedRatherThanCompleted() {
        QuestLog log = new QuestLog();
        assertTrue(log.complete(id("daily"), QuestCategory.DAILY));
        assertTrue(log.isDailyClaimed(id("daily")));
        assertFalse(log.isCompleted(id("daily")),
                "a daily must not land in the permanent completed set, or it could never repeat");
    }

    @Test
    void theFirstResetOnlyEstablishesTheBaselineDay() {
        QuestLog log = new QuestLog();
        assertEquals(-1L, log.lastDailyResetDay());
        assertFalse(log.resetDailiesIfDue(5L, 1),
                "with nothing claimed there is nothing to reset, so it should not announce one");
        assertEquals(5L, log.lastDailyResetDay());
    }

    @Test
    void dailiesResetEachInGameDayAtTheDefaultCadence() {
        QuestLog log = new QuestLog();
        log.resetDailiesIfDue(10L, 1);
        log.complete(id("daily"), QuestCategory.DAILY);

        assertFalse(log.resetDailiesIfDue(10L, 1), "the same day is not a new period");
        assertTrue(log.isDailyClaimed(id("daily")));

        assertTrue(log.resetDailiesIfDue(11L, 1), "the next day is");
        assertFalse(log.isDailyClaimed(id("daily")));
        assertEquals(11L, log.lastDailyResetDay());
    }

    @Test
    void aLongerIntervalHoldsDailiesForThatManyDays() {
        // The cadence is configurable precisely because one in-game day is twenty minutes.
        QuestLog log = new QuestLog();
        log.resetDailiesIfDue(0L, 3);
        log.complete(id("daily"), QuestCategory.DAILY);

        assertFalse(log.resetDailiesIfDue(1L, 3));
        assertFalse(log.resetDailiesIfDue(2L, 3));
        assertTrue(log.resetDailiesIfDue(3L, 3));
    }

    @Test
    void anIntervalBelowOneIsTreatedAsOne() {
        QuestLog log = new QuestLog();
        log.resetDailiesIfDue(0L, 0);
        log.complete(id("daily"), QuestCategory.DAILY);
        assertTrue(log.resetDailiesIfDue(1L, 0), "a zero interval must not mean 'never' or divide by zero");
    }

    @Test
    void aResetClearsTheCountersOfClaimedDailiesOnly() {
        QuestLog log = new QuestLog();
        QuestObjective objective = meditate(100);
        log.resetDailiesIfDue(0L, 1);

        log.advance(id("daily"), objective, 50);
        log.advance(id("story"), objective, 50);
        log.complete(id("daily"), QuestCategory.DAILY);

        log.resetDailiesIfDue(1L, 1);

        assertEquals(0, log.progressOf(id("daily"), objective));
        assertEquals(50, log.progressOf(id("story"), objective),
                "a daily reset must not touch story progress");
    }

    @Test
    void theLogSurvivesACodecRoundTrip() {
        QuestLog original = new QuestLog();
        original.resetDailiesIfDue(42L, 1);
        original.advance(id("q"), meditate(100), 30);
        original.complete(id("story"), QuestCategory.STORY);
        original.complete(id("daily"), QuestCategory.DAILY);

        Tag encoded = QuestLog.CODEC.encodeStart(NbtOps.INSTANCE, original)
                .getOrThrow(error -> new AssertionError("encode failed: " + error));
        QuestLog restored = QuestLog.CODEC.parse(NbtOps.INSTANCE, encoded)
                .getOrThrow(error -> new AssertionError("decode failed: " + error));

        assertEquals(original.completed(), restored.completed());
        assertEquals(original.claimedDailies(), restored.claimedDailies());
        assertEquals(original.lastDailyResetDay(), restored.lastDailyResetDay());
        assertEquals(30, restored.progressOf(id("q"), meditate(100)));
    }

    @Test
    void thresholdObjectivesAreNotCumulative() {
        // Threshold progress is re-read from live state, so storing it would let a player keep
        // credit for a meridian they later lost.
        assertFalse(ObjectiveKind.REACH_REALM.isCumulative());
        assertFalse(ObjectiveKind.OPEN_MERIDIANS.isCumulative());
        assertFalse(ObjectiveKind.REACH_PURITY.isCumulative());
        assertFalse(ObjectiveKind.LEARN_TECHNIQUE.isCumulative());
        assertFalse(ObjectiveKind.MASTER_TECHNIQUE.isCumulative());

        assertTrue(ObjectiveKind.MEDITATE_SECONDS.isCumulative());
        assertTrue(ObjectiveKind.KILL_ENTITIES.isCumulative());
        assertTrue(ObjectiveKind.CAST_TECHNIQUE.isCumulative());
        assertTrue(ObjectiveKind.SURVIVE_DEVIATION.isCumulative());
    }
}
