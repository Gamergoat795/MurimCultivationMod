package com.example.murimcultivation.item;

import com.example.murimcultivation.MurimCultivationMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MurimCultivationMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MurimCultivationMod.MODID);

    public static final RegistryObject<Item> QI_GATHERING_MANUAL = ITEMS.register("qi_gathering_manual",
            () -> new QiGatheringManualItem(new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> MURIM_TAB = CREATIVE_MODE_TABS.register("murim_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("Murim Cultivation"))
                    .icon(() -> new ItemStack(QI_GATHERING_MANUAL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(QI_GATHERING_MANUAL.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        CREATIVE_MODE_TABS.register(eventBus);
    }
}