package com.andymods.murimcultivation.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the quest content the mod ships, and the schema rules that keep a datapack honest.
 *
 * <p>The graph-level checks — prerequisites exist, no cycles, gates agree — live in
 * {@code tools/verify_sources.py}, which can see all the files at once. What is tested here is
 * what the codec itself must reject, since that is the protection a third-party datapack gets.
 */
class QuestDefinitionsTest {

    private static final String QUEST_DIR = "/data/murimcultivation/murimcultivation/quest/";
    private static final String TITLE_DIR = "/data/murimcultivation/murimcultivation/title/";

    private static final List<String> EXPECTED_QUESTS = List.of(
            "first_breath", "clean_foundation", "first_art", "open_the_way", "second_rate",
            "practised_hand", "tempered", "first_rate",
            "daily_breathing", "daily_sparring", "daily_drilling",
            "alliance_trial", "cult_trial", "hermit_trial", "daily_bout");

    private static final List<String> EXPECTED_TITLES = List.of(
            "no_longer_nameless", "tempered_by_failure", "first_rate_martial_artist");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static <T> T load(String dir, String name, Codec<T> codec) {
        try (InputStream stream = QuestDefinitionsTest.class.getResourceAsStream(dir + name + ".json")) {
            assertNotNull(stream, "missing definition: " + dir + name + ".json");
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return codec.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(error -> new AssertionError("failed to parse " + name + ".json: " + error));
        } catch (Exception exception) {
            throw new AssertionError("failed to read " + name + ".json", exception);
        }
    }

    private static Map<String, SystemQuest> loadQuests() {
        Map<String, SystemQuest> quests = new LinkedHashMap<>();
        for (String name : EXPECTED_QUESTS) {
            quests.put(name, load(QUEST_DIR, name, SystemQuest.CODEC));
        }
        return quests;
    }

    @Test
    void everyShippedQuestParses() {
        assertEquals(EXPECTED_QUESTS.size(), loadQuests().size());
    }

    @Test
    void everyShippedTitleParses() {
        for (String name : EXPECTED_TITLES) {
            Title title = load(TITLE_DIR, name, Title.CODEC);
            assertTrue(title.translationKey().startsWith("murimcultivation.title."));
            assertTrue(!title.attributes().isEmpty(), name + " should grant something for wearing it");
        }
    }

    @Test
    void thereIsSomethingToDoImmediately() {
        // A new cultivator opening the System window to an empty list is the worst first
        // impression the mod could make.
        long openers = loadQuests().values().stream()
                .filter(quest -> quest.prerequisites().isEmpty())
                .filter(quest -> quest.requiredRealmTier() <= 1)
                .count();
        assertTrue(openers >= 2, "expected at least two quests available at the starting realm, "
                + "found " + openers);
    }

    @Test
    void dailiesAreModestComparedToStoryQuests() {
        Map<String, SystemQuest> quests = loadQuests();
        double bestDaily = quests.values().stream()
                .filter(quest -> quest.category() == QuestCategory.DAILY)
                .flatMap(quest -> quest.rewards().stream())
                .filter(reward -> reward.kind() == RewardKind.PROGRESS)
                .mapToDouble(QuestReward::amount).max().orElse(0.0D);
        double bestStory = quests.values().stream()
                .filter(quest -> quest.category() == QuestCategory.STORY)
                .flatMap(quest -> quest.rewards().stream())
                .filter(reward -> reward.kind() == RewardKind.PROGRESS)
                .mapToDouble(QuestReward::amount).max().orElse(0.0D);

        // Dailies reset every in-game day, which is twenty minutes. If they paid as well as
        // story quests they would be the fastest route through the realms.
        assertTrue(bestDaily < bestStory,
                "the best daily (" + bestDaily + ") should pay less than the best story quest ("
                        + bestStory + ")");
    }

    @Test
    void everySectHasAQuestOfItsOwn() {
        // Joining a sect should open something new in the System window, not only a teacher.
        Set<String> sectsWithQuests = loadQuests().values().stream()
                .flatMap(quest -> quest.requiredSect().stream())
                .map(ResourceLocation::getPath)
                .collect(Collectors.toSet());
        for (String sect : List.of("murim_alliance", "demonic_cult", "hermit_valley")) {
            assertTrue(sectsWithQuests.contains(sect), sect + " has no quest of its own");
        }
    }

        @Test
    void everyQuestHasAtLeastOneObjectiveAndAReward() {
        loadQuests().forEach((name, quest) -> {
            assertTrue(!quest.objectives().isEmpty(), name + " has no objectives");
            assertTrue(!quest.rewards().isEmpty(), name + " pays nothing, so there is no reason to do it");
        });
    }

    @Test
    void noDailyHasPrerequisites() {
        loadQuests().forEach((name, quest) -> {
            if (quest.category() == QuestCategory.DAILY) {
                assertTrue(quest.prerequisites().isEmpty(),
                        name + " is a daily with prerequisites; it would re-lock itself on reset");
            }
        });
    }

    @Test
    void theCodecRejectsATargetlessObjectiveThatNeedsOne() {
        var result = QuestObjective.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"kind\":\"learn_technique\",\"amount\":1}"));
        assertTrue(result.isError(),
                "learn_technique without a target should be rejected at load, not silently inert");
    }

    @Test
    void theCodecRejectsAStrayTargetOnAKindThatTakesNone() {
        var result = QuestObjective.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"kind\":\"meditate_seconds\",\"amount\":10,\"target\":\"murimcultivation:qinggong\"}"));
        assertTrue(result.isError(), "a target on meditate_seconds is a mistake worth reporting");
    }

    @Test
    void theCodecRejectsAQuestWithDuplicateObjectiveKeys() {
        // Two objectives with the same kind and target would share one counter.
        String json = "{\"category\":\"story\",\"translation_key\":\"a\",\"description_key\":\"b\","
                + "\"objectives\":[{\"kind\":\"kill_entities\",\"amount\":5},"
                + "{\"kind\":\"kill_entities\",\"amount\":9}]}";
        var result = SystemQuest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        assertTrue(result.isError(), "duplicate objective keys should be rejected");
    }

    @Test
    void theCodecRejectsAQuestWithNoObjectives() {
        String json = "{\"category\":\"story\",\"translation_key\":\"a\",\"description_key\":\"b\","
                + "\"objectives\":[]}";
        assertTrue(SystemQuest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError());
    }

    @Test
    void theCodecRejectsADailyWithPrerequisites() {
        String json = "{\"category\":\"daily\",\"translation_key\":\"a\",\"description_key\":\"b\","
                + "\"prerequisites\":[\"murimcultivation:x\"],"
                + "\"objectives\":[{\"kind\":\"kill_entities\",\"amount\":5}]}";
        assertTrue(SystemQuest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError());
    }

    @Test
    void objectiveDescriptionsDoNotThrowForAnyKind() {
        // Every kind is rendered in the quest list, so a missing argument would crash the screen.
        for (ObjectiveKind kind : ObjectiveKind.values()) {
            Optional<ResourceLocation> target = kind.needsTarget()
                    ? Optional.of(ResourceLocation
                            .fromNamespaceAndPath("murimcultivation", "qinggong"))
                    : Optional.empty();
            QuestObjective objective = new QuestObjective(kind, 3, target);
            assertNotNull(objective.description().getString(), kind + " description");
            assertNotNull(objective.progressKey());
        }
    }

    @Test
    void rewardDescriptionsDoNotThrowForAnyKind() {
        for (RewardKind kind : RewardKind.values()) {
            Optional<ResourceLocation> target = kind.needsTarget()
                    ? Optional.of(ResourceLocation
                            .fromNamespaceAndPath("murimcultivation", "thing"))
                    : Optional.empty();
            assertNotNull(new QuestReward(kind, 2.0D, target).description().getString(), kind + " description");
        }
    }
}
