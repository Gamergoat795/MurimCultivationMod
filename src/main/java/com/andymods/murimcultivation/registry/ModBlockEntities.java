package com.andymods.murimcultivation.registry;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.alchemy.CauldronBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MurimCultivationMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CauldronBlockEntity>> CAULDRON =
            BLOCK_ENTITIES.register("cauldron", () -> BlockEntityType.Builder
                    .of(CauldronBlockEntity::new, ModBlocks.PILL_CAULDRON.get())
                    .build(null));

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }

    private ModBlockEntities() {
    }
}
