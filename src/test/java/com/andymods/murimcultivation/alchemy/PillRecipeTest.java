package com.andymods.murimcultivation.alchemy;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the pill formulas the mod ships.
 *
 * <p>The check worth having here is that no two formulas claim the same herb. With one input
 * slot the cauldron takes the first match it finds, so a duplicate would make one formula
 * unreachable in a way that depends on registry iteration order — the kind of bug that appears
 * only on someone else's machine.
 */
class PillRecipeTest {

    private static final String DIR = "/data/murimcultivation/murimcultivation/pill_recipe/";

    private static final List<String> EXPECTED =
            List.of("qi_recovery_pill", "deviation_remedy_pill", "foundation_pill");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Map<String, PillRecipe> loadRecipes() {
        Map<String, PillRecipe> recipes = new LinkedHashMap<>();
        for (String name : EXPECTED) {
            try (InputStream stream = PillRecipeTest.class.getResourceAsStream(DIR + name + ".json")) {
                assertNotNull(stream, "missing formula: " + name + ".json");
                JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                recipes.put(name, PillRecipe.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(error -> new AssertionError("failed to parse " + name + ": " + error)));
            } catch (Exception exception) {
                throw new AssertionError("failed to read " + name + ".json", exception);
            }
        }
        return recipes;
    }

    @Test
    void everyShippedFormulaParses() {
        assertEquals(EXPECTED.size(), loadRecipes().size());
    }

    @Test
    void noTwoFormulasClaimTheSameHerb() {
        // The cauldron has one input slot and takes the first match, so a duplicate herb makes
        // one formula unreachable depending on registry iteration order.
        var herbs = loadRecipes().values().stream().map(PillRecipe::herb).toList();
        assertEquals(herbs.size(), herbs.stream().distinct().count(),
                "two formulas share an input herb");
    }

    @Test
    void everyFormulaProducesSomethingAndTakesTime() {
        loadRecipes().forEach((name, recipe) -> {
            assertTrue(recipe.resultCount() >= 1, name + " produces nothing");
            assertTrue(recipe.brewTicks() > 0, name + " brews instantly, which defeats the point");
            assertNotNull(recipe.herb(), name + " has no herb");
            assertNotNull(recipe.result(), name + " has no result");
        });
    }

    @Test
    void harderPillsTakeLongerThanEasyOnes() {
        Map<String, PillRecipe> recipes = loadRecipes();
        assertTrue(recipes.get("foundation_pill").brewTicks()
                        > recipes.get("qi_recovery_pill").brewTicks(),
                "refining a foundation should be slower than topping up Qi");
    }

    @Test
    void matchingIsByItemAndRejectsOthers() {
        PillRecipe recipe = loadRecipes().get("qi_recovery_pill");
        assertTrue(recipe.matches(new ItemStack(recipe.herb())));
        assertFalse(recipe.matches(new ItemStack(Items.STONE)));
        assertFalse(recipe.matches(ItemStack.EMPTY));
    }

    @Test
    void theResultIsANewStackEachTime() {
        // A shared stack would let one brew's output be mutated by another's.
        PillRecipe recipe = loadRecipes().get("qi_recovery_pill");
        ItemStack first = recipe.createResult();
        ItemStack second = recipe.createResult();
        assertFalse(first == second, "createResult must not hand out the same stack twice");
        assertEquals(recipe.resultCount(), first.getCount());
    }
}
