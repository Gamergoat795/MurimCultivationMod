package com.andymods.murimcultivation.technique;

import com.andymods.murimcultivation.cultivation.Meridian;
import com.andymods.murimcultivation.cultivation.Realm;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the martial arts this mod ships, and — more importantly — that they agree with the
 * realm ladder.
 *
 * <p>The cross-file check earns its place: a technique is gated twice, once by its own
 * {@code required_realm_tier} and once by the cap the realm puts on what a body can channel.
 * Those two numbers live in different files and nothing but a test makes them agree. Before
 * this test existed they did not, and the consequence was that the realm every player awakens
 * into could cast nothing at all.
 */
class TechniqueDefinitionsTest {

    private static final String TECHNIQUE_DIR = "/data/murimcultivation/murimcultivation/technique/";
    private static final String REALM_DIR = "/data/murimcultivation/murimcultivation/realm/";

    /** Every technique the mod ships. */
    private static final List<String> EXPECTED_TECHNIQUES = List.of(
            "internal_healing", "qinggong", "divine_palm", "water_walking",
            "shadowless_step", "sword_qi", "iron_body", "sword_force");

    private static final List<String> EXPECTED_REALMS = List.of(
            "third_rate", "second_rate", "first_rate", "peak", "transcendent",
            "flower_realm", "profound_realm", "life_and_death", "beyond_heaven");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static <T> T load(String dir, String name, com.mojang.serialization.Codec<T> codec) {
        try (InputStream stream = TechniqueDefinitionsTest.class.getResourceAsStream(dir + name + ".json")) {
            assertNotNull(stream, "missing definition: " + dir + name + ".json");
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return codec.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(error -> new AssertionError("failed to parse " + name + ".json: " + error));
        } catch (Exception exception) {
            throw new AssertionError("failed to read " + name + ".json", exception);
        }
    }

    private static Map<String, Technique> loadTechniques() {
        Map<String, Technique> techniques = new LinkedHashMap<>();
        for (String name : EXPECTED_TECHNIQUES) {
            techniques.put(name, load(TECHNIQUE_DIR, name, Technique.CODEC));
        }
        return techniques;
    }

    private static List<Realm> loadRealms() {
        List<Realm> realms = new ArrayList<>();
        for (String name : EXPECTED_REALMS) {
            realms.add(load(REALM_DIR, name, Realm.CODEC));
        }
        realms.sort(Comparator.comparingInt(Realm::tier));
        return realms;
    }

    @Test
    void everyShippedTechniqueParses() {
        assertEquals(EXPECTED_TECHNIQUES.size(), loadTechniques().size());
    }

    @Test
    void everyTechniqueNamesARegisteredBehaviour() {
        // A datapack typo in "behaviour" should be caught here for our own content, since at
        // runtime it only surfaces when someone tries to cast the technique.
        loadTechniques().forEach((name, technique) -> assertTrue(
                TechniqueBehaviours.isRegistered(technique.behaviour()),
                name + " names an unregistered behaviour: " + technique.behaviour()));
    }

    @Test
    void theTwoRealmGatesAgree() {
        List<Realm> realms = loadRealms();

        loadTechniques().forEach((name, technique) -> {
            Realm firstEligible = realms.stream()
                    .filter(realm -> realm.tier() >= technique.requiredRealmTier())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            name + " requires realm tier " + technique.requiredRealmTier()
                                    + ", which no shipped realm reaches"));

            // The realm that first satisfies required_realm_tier must also be able to channel
            // this technique's tier, or required_realm_tier is a lie and the real gate is higher.
            assertTrue(firstEligible.techniqueTier() >= technique.tier(),
                    name + " claims required_realm_tier " + technique.requiredRealmTier()
                            + ", but the first realm meeting that (tier " + firstEligible.tier()
                            + ") caps technique tier at " + firstEligible.techniqueTier()
                            + " and the technique is tier " + technique.tier());
        });
    }

    @Test
    void theStartingRealmCanCastSomething() {
        // A player awakens into the lowest realm. If nothing is castable there, the first
        // thing the mod teaches them is that their techniques do not work.
        Realm starting = loadRealms().get(0);
        long castable = loadTechniques().values().stream()
                .filter(technique -> technique.requiredRealmTier() <= starting.tier())
                .filter(technique -> technique.tier() <= starting.techniqueTier())
                .count();
        assertTrue(castable >= 2,
                "the starting realm should grant at least two usable techniques, found " + castable);
    }

    @Test
    void everyTechniqueIsReachableByRealmAndMeridians() {
        List<Realm> realms = loadRealms();
        loadTechniques().forEach((name, technique) -> {
            assertTrue(technique.requiredOpenMeridians() <= Meridian.count(),
                    name + " requires more meridians than exist");
            boolean reachable = realms.stream().anyMatch(realm ->
                    realm.tier() >= technique.requiredRealmTier()
                            && realm.techniqueTier() >= technique.tier());
            assertTrue(reachable, name + " can never be cast by any shipped realm");
        });
    }

    @Test
    void harderTechniquesCostMoreAndDemandMore() {
        List<Technique> byTier = new ArrayList<>(loadTechniques().values());
        byTier.sort(Comparator.comparingInt(Technique::tier));

        for (int i = 1; i < byTier.size(); i++) {
            Technique higher = byTier.get(i);
            Technique lower = byTier.get(i - 1);
            if (higher.tier() == lower.tier()) {
                continue;
            }
            assertTrue(higher.qiCost() >= lower.qiCost(),
                    "tier " + higher.tier() + " should not cost less Qi than tier " + lower.tier());
            assertTrue(higher.requiredRealmTier() >= lower.requiredRealmTier(),
                    "realm requirements should not regress with tier");
            assertTrue(higher.requiredOpenMeridians() >= lower.requiredOpenMeridians(),
                    "meridian requirements should not regress with tier");
        }
    }

    @Test
    void masteryGainFallsAsTechniquesGetHarder() {
        // A tier-5 art should be slower to master than a tier-1 one.
        Map<String, Technique> techniques = loadTechniques();
        Technique starter = techniques.get("internal_healing");
        Technique advanced = techniques.get("sword_force");
        assertTrue(advanced.masteryGainPerUse() < starter.masteryGainPerUse(),
                "an advanced art should take more practice per point of mastery");
    }

    @Test
    void swordArtsRequireASwordAndPalmArtsRequireFreeHands() {
        Map<String, Technique> techniques = loadTechniques();
        assertEquals(HandRequirement.SWORD, techniques.get("sword_qi").hand());
        assertEquals(HandRequirement.SWORD, techniques.get("sword_force").hand());
        assertEquals(HandRequirement.FREE_HAND, techniques.get("divine_palm").hand());
        assertEquals(HandRequirement.ANY, techniques.get("qinggong").hand());
    }

    @Test
    void everyTechniqueIsIndividuallySane() {
        loadTechniques().forEach((name, technique) -> {
            assertTrue(technique.translationKey().startsWith("murimcultivation.technique."),
                    name + ": translation key should be namespaced");
            assertTrue(technique.qiCost() >= 0.0D, name + ": Qi cost cannot be negative");
            assertTrue(technique.cooldownTicks() >= 0, name + ": cooldown cannot be negative");
            assertTrue(technique.masteryGainPerUse() > 0.0D,
                    name + ": an art you cannot improve at is a dead end");
            assertTrue(!technique.hangul().isEmpty(), name + ": shipped arts should carry their Hangul");
        });
    }

    @Test
    void theTranslationKeyConventionMatchesWhatTooltipsAssume() {
        // MartialManualItem derives a technique's name from its id for tooltips, because the
        // registry is not reachable without a level. The shipped JSONs must follow that.
        loadTechniques().forEach((name, technique) -> {
            String derived = com.andymods.murimcultivation.item.MartialManualItem.translationKeyFor(
                    ResourceLocation.fromNamespaceAndPath("murimcultivation", name));
            assertEquals(technique.translationKey(), derived,
                    name + ": translation_key must match the convention tooltips derive");
        });
    }
}
