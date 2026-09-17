package com.andymods.murimcultivation.registry;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.alchemy.CauldronBlock;
import com.andymods.murimcultivation.world.SpiritVeinBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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
