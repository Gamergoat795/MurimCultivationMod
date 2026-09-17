package com.andymods.murimcultivation.alchemy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A pill formula, loaded from a datapack.
 *
 * <p>Deliberately <em>not</em> a vanilla {@code RecipeType}. A vanilla recipe would need a
 * serializer, a recipe-book category and a container to match against, all so that a cauldron
 * with one input slot could look things up. As a datapack registry it uses the same machinery as
 * realms, techniques, quests, titles and sects — so a datapack author who has written any of
 * those already knows how to write a pill, and the mod has one loading mechanism rather than two.
 */
public record PillRecipe(Item herb, Item result, int resultCount, int brewTicks, double purityCost) {

    public static final Codec<PillRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("herb").forGetter(PillRecipe::herb),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("result").forGetter(PillRecipe::result),
            Codec.intRange(1, 64).optionalFieldOf("result_count", 1).forGetter(PillRecipe::resultCount),
            Codec.intRange(1, 72000).optionalFieldOf("brew_ticks", 200).forGetter(PillRecipe::brewTicks),
            Codec.doubleRange(0.0D, 100.0D).optionalFieldOf("purity_cost", 0.0D)
                    .forGetter(PillRecipe::purityCost)
    ).apply(instance, PillRecipe::new));

    public boolean matches(ItemStack stack) {
        return stack.is(herb);
    }

    public ItemStack createResult() {
        return new ItemStack(result, resultCount);
    }
}
