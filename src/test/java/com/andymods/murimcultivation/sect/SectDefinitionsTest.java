package com.andymods.murimcultivation.sect;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the sects the mod ships. */
class SectDefinitionsTest {

    private static final String SECT_DIR = "/data/murimcultivation/murimcultivation/sect/";

    private static final List<String> EXPECTED =
            List.of("murim_alliance", "demonic_cult", "hermit_valley");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Map<String, Sect> loadSects() {
        Map<String, Sect> sects = new LinkedHashMap<>();
        for (String name : EXPECTED) {
            try (InputStream stream = SectDefinitionsTest.class.getResourceAsStream(SECT_DIR + name + ".json")) {
                assertNotNull(stream, "missing sect: " + name + ".json");
                JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                sects.put(name, Sect.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(error -> new AssertionError("failed to parse " + name + ": " + error)));
            } catch (Exception exception) {
                throw new AssertionError("failed to read " + name + ".json", exception);
            }
        }
        return sects;
    }

    @Test
    void everyShippedSectParses() {
        assertEquals(EXPECTED.size(), loadSects().size());
    }

    @Test
    void bothSidesOfTheMurimExist() {
        // The alignment mechanic is meaningless without at least one sect on each side.
        var alignments = loadSects().values().stream().map(Sect::alignment).toList();
        assertTrue(alignments.contains(SectAlignment.ORTHODOX), "expected an orthodox sect");
        assertTrue(alignments.contains(SectAlignment.DEMONIC), "expected a demonic sect");
    }

    @Test
    void thereIsAnUnalignedOptionForSomeoneWhoWantsNeitherSide() {
        assertTrue(loadSects().values().stream()
                        .anyMatch(sect -> sect.alignment() == SectAlignment.NEUTRAL),
                "a player who wants to avoid the Alliance/Cult choice should still have somewhere to go");
    }

    @Test
    void everySectTeachesSomethingToAnOuterDisciple() {
        // Joining should be worth something immediately, or there is no reason to.
        loadSects().forEach((name, sect) -> assertTrue(
                !sect.teachingsUpTo(SectRank.OUTER_DISCIPLE).isEmpty(),
                name + " teaches an outer disciple nothing, so joining it does nothing"));
    }

    @Test
    void teachingsAccumulateUpTheRanks() {
        loadSects().forEach((name, sect) -> {
            int outer = sect.teachingsUpTo(SectRank.OUTER_DISCIPLE).size();
            int core = sect.teachingsUpTo(SectRank.CORE_DISCIPLE).size();
            assertTrue(core >= outer,
                    name + ": a higher rank must not know fewer arts than a lower one");
        });
    }

    @Test
    void teachingsAreDistinctWhenAccumulated() {
        loadSects().forEach((name, sect) -> {
            List<ResourceLocation> all = sect.teachingsUpTo(SectRank.PATRIARCH);
            assertEquals(all.size(), all.stream().distinct().count(),
                    name + ": an art listed at two ranks should appear once when accumulated");
        });
    }

    @Test
    void everySectIsIndividuallySane() {
        loadSects().forEach((name, sect) -> {
            assertTrue(sect.translationKey().startsWith("murimcultivation.sect."),
                    name + ": translation key should be namespaced");
            assertTrue(!sect.hangul().isEmpty(), name + ": shipped sects should carry their Hangul");
            assertTrue(sect.requiredRealmTier() >= 0, name + ": realm requirement cannot be negative");
        });
    }

    @Test
    void atLeastOneSectWillTakeANewCultivator() {
        // If every sect gated above the starting realm, the whole system would be invisible
        // for the first hours of play.
        assertTrue(loadSects().values().stream().anyMatch(sect -> sect.requiredRealmTier() <= 1),
                "expected a sect reachable at the starting realm");
    }
}
