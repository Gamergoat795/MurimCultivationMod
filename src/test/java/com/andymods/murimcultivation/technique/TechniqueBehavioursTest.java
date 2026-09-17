package com.andymods.murimcultivation.technique;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour registry.
 *
 * <p>Catches the two ways the data/code split drifts: a technique naming a behaviour that does
 * not exist (which only surfaces when a player tries to cast it), and a behaviour nobody uses
 * (dead code that looks alive).
 */
class TechniqueBehavioursTest {

    /** The eight behaviours the shipped techniques use. */
    private static final List<String> EXPECTED = List.of(
            "sword_qi", "sword_force", "divine_palm", "shadowless_step",
            "qinggong", "water_walking", "iron_body", "internal_healing");

    @Test
    void everyExpectedBehaviourIsRegistered() {
        for (String name : EXPECTED) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("murimcultivation", name);
            assertTrue(TechniqueBehaviours.isRegistered(id), "behaviour not registered: " + id);
            assertTrue(TechniqueBehaviours.get(id).isPresent(), "behaviour not retrievable: " + id);
        }
    }

    @Test
    void thereAreNoOrphanBehaviours() {
        // A registered behaviour no shipped technique references is dead code that still looks
        // reachable. If one is intentionally spare, add it to EXPECTED with a note.
        Set<String> registered = TechniqueBehaviours.ids().stream()
                .map(ResourceLocation::getPath)
                .collect(Collectors.toSet());
        assertEquals(Set.copyOf(EXPECTED), registered,
                "the registered behaviours and the expected set have diverged");
    }

    @Test
    void anUnknownBehaviourIsReportedRatherThanAssumed() {
        ResourceLocation nonsense =
                ResourceLocation.fromNamespaceAndPath("murimcultivation", "not_a_real_behaviour");
        assertTrue(TechniqueBehaviours.get(nonsense).isEmpty());
        assertTrue(!TechniqueBehaviours.isRegistered(nonsense));
    }

    @Test
    void theRegistryIsNotEmpty() {
        // Guards against the static initialiser silently failing to run.
        assertEquals(EXPECTED.size(), TechniqueBehaviours.ids().size());
    }
}
