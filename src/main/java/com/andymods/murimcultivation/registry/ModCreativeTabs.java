package com.andymods.murimcultivation.registry;

import com.andymods.murimcultivation.MurimCultivationMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MurimCultivationMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MURIM_TAB =
            CREATIVE_MODE_TABS.register("murim", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.murimcultivation.murim"))
                    .icon(() -> new ItemStack(ModItems.QI_GATHERING_MANUAL.get()))
                    .displayItems((parameters, output) -> output.accept(ModItems.QI_GATHERING_MANUAL.get()))
                    .build());

    public static void register(IEventBus modBus) {
        CREATIVE_MODE_TABS.register(modBus);
    }

    private ModCreativeTabs() {
    }
}
