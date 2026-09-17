package com.andymods.murimcultivation.registry;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.item.MartialManualItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Comparator;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MurimCultivationMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MURIM_TAB =
            CREATIVE_MODE_TABS.register("murim", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.murimcultivation.murim"))
                    .icon(() -> new ItemStack(ModItems.QI_GATHERING_MANUAL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.QI_GATHERING_MANUAL.get());
                        output.accept(ModItems.SPIRIT_VEIN_ITEM.get());
                        output.accept(ModItems.PILL_CAULDRON_ITEM.get());
                        output.accept(ModItems.SPIRIT_GINSENG.get());
                        output.accept(ModItems.BLOOD_LOTUS.get());
                        output.accept(ModItems.JADE_CHRYSANTHEMUM.get());
                        output.accept(ModItems.QI_RECOVERY_PILL.get());
                        output.accept(ModItems.DEVIATION_REMEDY_PILL.get());
                        output.accept(ModItems.FOUNDATION_PILL.get());
                        output.accept(ModItems.MARTIAL_ARTIST_SPAWN_EGG.get());
                        // A manual for every technique the loaded datapacks define, so new
                        // datapack content shows up in creative without any code change.
                        parameters.holders()
                                .lookup(MurimRegistries.TECHNIQUE)
                                .ifPresent(lookup -> lookup.listElementIds()
                                        .sorted(Comparator.comparing(key -> key.location().toString()))
                                        .forEach(key -> output.accept(MartialManualItem.forTechnique(
                                                ModItems.MARTIAL_MANUAL.get(), key.location()))));
                    })
                    .build());

    public static void register(IEventBus modBus) {
        CREATIVE_MODE_TABS.register(modBus);
    }

    private ModCreativeTabs() {
    }
}
