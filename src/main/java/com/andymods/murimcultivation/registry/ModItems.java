package com.andymods.murimcultivation.registry;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.item.QiGatheringManualItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MurimCultivationMod.MODID);

    /** The manual that turns an ordinary person into a cultivator. The origin beat. */
    public static final DeferredHolder<Item, QiGatheringManualItem> QI_GATHERING_MANUAL =
            ITEMS.register("qi_gathering_manual",
                    () -> new QiGatheringManualItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    private ModItems() {
    }
}
