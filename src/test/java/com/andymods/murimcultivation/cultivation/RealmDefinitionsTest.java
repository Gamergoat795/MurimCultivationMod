package com.andymods.murimcultivation.cultivation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the realm ladder this mod actually ships.
 *
 * <p>Realms are datapack content, which means a typo in a JSON file is not a compile error — it
 * is a broken ladder discovered at runtime. This parses every shipped realm through the real
 * codec and asserts the ladder is coherent: nine rungs, unique tiers, and difficulty that only
 * ever rises as you climb.
 */
class RealmDefinitionsTest {

    /** Every realm the mod ships, in the order the ladder is meant to run. */
    private static final List<String> EXPECTED_REALMS = List.of(
            "third_rate", "second_rate", "first_rate", "peak", "transcendent",
            "flower_realm", "profound_realm", "life_and_death", "beyond_heaven");

    private static final String REALM_DIR = "/data/murimcultivation/murimcultivation/realm/";

    @BeforeAll
    static void bootstrapMinecraft() {
        // Realm.CODEC reaches BuiltInRegistries.ATTRIBUTE through AttributeGrant, so the
        // registries have to exist before the codec's static initialiser runs.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Realm load(String name) {
        try (InputStream stream = RealmDefinitionsTest.class.getResourceAsStream(REALM_DIR + name + ".json")) {
            assertNotNull(stream, "missing realm definition: " + name + ".json");
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return Realm.CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(error -> new AssertionError("failed to parse " + name + ".json: " + error));
        } catch (Exception exception) {
            throw new AssertionError("failed to read " + name + ".json", exception);
        }
    }

    private static List<Realm> loadLadder() {
        List<Realm> realms = new ArrayList<>();
        for (String name : EXPECTED_REALMS) {
            realms.add(load(name));
        }
        realms.sort(Comparator.comparingInt(Realm::tier));
        return realms;
    }

    @Test
    void everyShippedRealmParses() {
        List<Realm> realms = loadLadder();
        assertEquals(EXPECTED_REALMS.size(), realms.size());
    }

    @Test
    void tiersAreUniqueAndContiguous() {
        List<Realm> ladder = loadLadder();
        for (int i = 0; i < ladder.size(); i++) {
            assertEquals(i + 1, ladder.get(i).tier(),
                    "tiers should run 1.." + ladder.size() + " with no gaps or duplicates");
        }
    }

    @Test
    void theFileOrderMatchesTierOrder() {
        // Guards against a realm being renumbered without the expected list being updated.
        List<Realm> ladder = loadLadder();
        for (int i = 0; i < EXPECTED_REALMS.size(); i++) {
            Realm expected = load(EXPECTED_REALMS.get(i));
            assertEquals(expected.tier(), ladder.get(i).tier(),
                    EXPECTED_REALMS.get(i) + " is not at ladder position " + (i + 1));
        }
    }

    @Test
    void climbingIsAlwaysHarderAndAlwaysStronger() {
        List<Realm> ladder = loadLadder();
        for (int i = 1; i < ladder.size(); i++) {
            Realm higher = ladder.get(i);
            Realm lower = ladder.get(i - 1);
            String label = "tier " + higher.tier() + " vs " + lower.tier();

            assertTrue(higher.progressPerSubstage() > lower.progressPerSubstage(),
                    "each realm must take longer to cross: " + label);
            assertTrue(higher.qiCapacity() > lower.qiCapacity(),
                    "each realm must hold more Qi: " + label);
            assertTrue(higher.qiRegenPerSecond() > lower.qiRegenPerSecond(),
                    "each realm must circulate Qi faster: " + label);
            assertTrue(higher.purityFloor() >= lower.purityFloor(),
                    "purity demands must never relax as you climb: " + label);
            assertTrue(higher.requiredOpenMeridians() >= lower.requiredOpenMeridians(),
                    "meridian demands must never relax as you climb: " + label);
            assertTrue(higher.breakthroughBaseChance() <= lower.breakthroughBaseChance(),
                    "breakthroughs must never get easier as you climb: " + label);
            assertTrue(higher.techniqueTier() >= lower.techniqueTier(),
                    "technique tier must not regress: " + label);
        }
    }

    @Test
    void everyRealmIsIndividuallySane() {
        for (Realm realm : loadLadder()) {
            String label = realm.translationKey();

            assertTrue(realm.progressPerSubstage() > 0.0D, label + ": progress per substage must be positive");
            assertTrue(realm.qiCapacity() > 0.0D, label + ": Qi capacity must be positive");
            assertTrue(realm.purityFloor() >= CultivationData.MIN_PURITY
                            && realm.purityFloor() <= CultivationData.MAX_PURITY,
                    label + ": purity floor must be a reachable purity value");
            assertTrue(realm.requiredOpenMeridians() <= Meridian.count(),
                    label + ": cannot require more meridians than exist");
            assertTrue(realm.breakthroughBaseChance() > 0.0D && realm.breakthroughBaseChance() <= 1.0D,
                    label + ": breakthrough chance must be possible but not certain-by-default");
            assertTrue(realm.translationKey().startsWith("murimcultivation.realm."),
                    label + ": translation key should be namespaced");
            assertTrue(realm.hangul().isEmpty() || !realm.hangul().isBlank(),
                    label + ": hangul must be absent or meaningful");
        }
    }

    @Test
    void totalProgressIsTheSumOfItsSubstageSteps() {
        for (Realm realm : loadLadder()) {
            double summed = 0.0D;
            for (Substage substage : Substage.values()) {
                summed += realm.progressToLeave(substage);
            }
            assertEquals(summed, realm.totalProgressToClear(), 1.0e-9D,
                    realm.translationKey() + ": totalProgressToClear must equal the sum of its steps");
        }
    }

    @Test
    void substageCostsRiseWithinEachRealm() {
        for (Realm realm : loadLadder()) {
            for (int i = 1; i < Substage.values().length; i++) {
                Substage later = Substage.byIndex(i);
                Substage earlier = Substage.byIndex(i - 1);
                assertTrue(realm.progressToLeave(later) > realm.progressToLeave(earlier),
                        realm.translationKey() + ": leaving " + later + " should cost more than " + earlier);
            }
        }
    }

    @Test
    void attributeGrantsGrowWithTheLadder() {
        List<Realm> ladder = loadLadder();
        // The lowest realm grants nothing: being a Third-Rate nobody should feel ordinary.
        assertTrue(ladder.get(0).attributes().isEmpty(),
                "the first realm should grant no attribute bonuses");
        // Every realm above it grants something.
        for (int i = 1; i < ladder.size(); i++) {
            assertTrue(!ladder.get(i).attributes().isEmpty(),
                    ladder.get(i).translationKey() + " should grant at least one attribute bonus");
        }
    }
}
