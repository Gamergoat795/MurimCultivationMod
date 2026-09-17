package com.andymods.murimcultivation.registry;

import com.andymods.murimcultivation.MurimCultivationMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item data components.
 *
 * <p>The {@code technique} component is what lets one {@code MartialManualItem} class back
 * every manual in the game. The alternative — an item class and registry entry per technique —
 * means a datapack could never add a manual for a technique it defines, which would undercut
 * the whole point of techniques being datapack content.
 */
public final class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MurimCultivationMod.MODID);

    /** Which technique a manual teaches. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> TECHNIQUE =
            DATA_COMPONENTS.register("technique", () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(StreamCodec.<RegistryFriendlyByteBuf, ResourceLocation>of(
                            (buf, id) -> buf.writeResourceLocation(id),
                            RegistryFriendlyByteBuf::readResourceLocation))
                    .build());

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
    }

    private ModDataComponents() {
    }
}
