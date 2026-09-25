package com.andymods.murimcultivation.npc;

import com.andymods.murimcultivation.sect.Sect;
import com.andymods.murimcultivation.sect.SectRank;
import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.technique.TechniqueBehaviours;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules martial artists fight by: sparring, and which arts an artist of a realm knows. */
class NpcCombatTest {

    private static final String DATA = "/data/murimcultivation/murimcultivation/";

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static <T> T load(String path, Codec<T> codec) {
        try (InputStream stream = NpcCombatTest.class.getResourceAsStream(DATA + path + ".json")) {
            assertNotNull(stream, "missing " + path + ".json");
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return codec.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(error -> new AssertionError("failed to parse " + path + ": " + error));
        } catch (Exception exception) {
            throw new AssertionError("failed to read " + path + ".json", exception);
        }
    }

    // --- Sparring ---------------------------------------------------------------------

    @Test
    void aSparNeverTakesTheLastPointOfHealth() {
        assertEquals(19.0F, Sparring.protect(20.0F, 50.0F));
        assertEquals(0.0F, Sparring.protect(1.0F, 5.0F), "already at one health, a blow does nothing");
        assertEquals(4.0F, Sparring.protect(20.0F, 4.0F), "an ordinary blow lands in full");
        assertTrue(Sparring.protect(0.5F, 3.0F) >= 0.0F, "the cap never turns into healing");
    }

    @Test
    void theBeatenSideYields() {
        assertTrue(Sparring.yields(5.0F, 20.0F));
        assertFalse(Sparring.yields(6.0F, 20.0F), "exactly at the threshold still stands");
        assertFalse(Sparring.yields(20.0F, 20.0F));
        assertFalse(Sparring.yields(0.0F, 0.0F), "no max health is not a defeat");
    }

    @Test
    void aSparLapsesWhenIdleOrApart() {
        assertFalse(Sparring.abandoned(100L, 100L, 4.0D));
        assertTrue(Sparring.abandoned(100L + Sparring.IDLE_TIMEOUT_TICKS + 1L, 100L, 4.0D));
        double tooFar = (Sparring.MAX_DISTANCE + 1.0D) * (Sparring.MAX_DISTANCE + 1.0D);
        assertTrue(Sparring.abandoned(100L, 100L, tooFar));
    }

    // --- What an artist knows ---------------------------------------------------------

    @Test
    void rankAndMasteryRiseWithRealm() {
        assertEquals(SectRank.OUTER_DISCIPLE, NpcTechniques.rankFor(1));
        assertEquals(SectRank.CORE_DISCIPLE, NpcTechniques.rankFor(3));
        assertEquals(SectRank.ELDER, NpcTechniques.rankFor(9));
        assertEquals(SectRank.OUTER_DISCIPLE, NpcTechniques.rankFor(0), "a bad tier still yields a rank");
        assertTrue(NpcTechniques.masteryFor(4) > NpcTechniques.masteryFor(1));
        assertEquals(100, NpcTechniques.masteryFor(9), "mastery is capped");
    }

    @Test
    void theStrongestOfEverySectHaveAnArtToCast() {
        // The point of reusing player techniques is that artists fight with them. A sect whose
        // top-realm members still know no castable art would only ever brawl — which happens
        // silently when a teaching is sustained, or gated above the sect's realm range.
        for (String name : List.of("murim_alliance", "demonic_cult", "hermit_valley")) {
            Sect sect = load("sect/" + name, Sect.CODEC);
            int tier = sect.realmRange().max();
            boolean castsSomething = sect.teachingsUpTo(NpcTechniques.rankFor(tier)).stream()
                    .map(id -> load("technique/" + id.getPath(), Technique.CODEC))
                    .anyMatch(technique -> TechniqueBehaviours.npcUsable(technique.behaviour())
                            && technique.requiredRealmTier() <= tier);
            assertTrue(castsSomething, name + ": even a realm-" + tier + " member knows no castable art");
        }
    }

    @Test
    void everyTeachingNamesARealTechnique() {
        for (String name : List.of("murim_alliance", "demonic_cult", "hermit_valley")) {
            for (ResourceLocation id : load("sect/" + name, Sect.CODEC).teachingsUpTo(SectRank.PATRIARCH)) {
                assertNotNull(load("technique/" + id.getPath(), Technique.CODEC), name + " teaches " + id);
            }
        }
    }
}
