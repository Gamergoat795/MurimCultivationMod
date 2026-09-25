package com.andymods.murimcultivation.registry;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.alchemy.CauldronBlock;
import com.andymods.murimcultivation.world.SpiritVeinBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, MurimCultivationMod.MODID);

    /** A place where the earth's Qi surfaces. Raises the ambient Qi around it. */
    public static final DeferredHolder<Block, SpiritVeinBlock> SPIRIT_VEIN =
            BLOCKS.register("spirit_vein", () -> new SpiritVeinBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .strength(3.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 6)
                            .sound(SoundType.AMETHYST)));

    /** Where herbs become pills. */
    public static final DeferredHolder<Block, CauldronBlock> PILL_CAULDRON =
            BLOCKS.register("pill_cauldron", () -> new CauldronBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
                            .sound(SoundType.METAL)));

    // --- Herbs as they grow in the world ------------------------------------------------
    //
    // Each shares its id with the herb item, the way vanilla's flowers do, so the block drops
    // exactly the item the pill formulas already name and planting one puts it back. Vanilla
    // block classes rather than new ones: a flower and a lily pad already have the placement
    // rules wanted here, and a subclass would only restate them.

    /** A root that grows on ordinary soil in forests and Qi-rich land. */
    public static final DeferredHolder<Block, FlowerBlock> SPIRIT_GINSENG =
            BLOCKS.register("spirit_ginseng", () -> new FlowerBlock(
                    MobEffects.SATURATION, 0.35F, herbProperties()));

    /** Floats on still water, like a lily pad, where Qi has pooled for a long time. */
    public static final DeferredHolder<Block, WaterlilyBlock> BLOOD_LOTUS =
            BLOCKS.register("blood_lotus", () -> new WaterlilyBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .instabreak()
                            .sound(SoundType.LILY_PAD)
                            .noOcclusion()
                            .pushReaction(PushReaction.DESTROY)));

    /** Grows only in a few Qi-rich highlands, which is what keeps foundation pills rare. */
    public static final DeferredHolder<Block, FlowerBlock> JADE_CHRYSANTHEMUM =
            BLOCKS.register("jade_chrysanthemum", () -> new FlowerBlock(
                    MobEffects.REGENERATION, 8.0F, herbProperties()));

    private static BlockBehaviour.Properties herbProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .noCollission()
                .instabreak()
                .sound(SoundType.GRASS)
                .offsetType(BlockBehaviour.OffsetType.XZ)
                .pushReaction(PushReaction.DESTROY);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    /** The item form of a block, registered alongside the mod's other items. */
    public static Item blockItem(Block block) {
        return new BlockItem(block, new Item.Properties());
    }

    private ModBlocks() {
    }
}
