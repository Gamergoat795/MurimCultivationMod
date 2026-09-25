package com.andymods.murimcultivation.registry;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.item.MartialManualItem;
import com.andymods.murimcultivation.item.PillItem;
import com.andymods.murimcultivation.item.QiGatheringManualItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MurimCultivationMod.MODID);

    /** The manual that turns an ordinary person into a cultivator. The origin beat. */
    public static final DeferredHolder<Item, QiGatheringManualItem> QI_GATHERING_MANUAL =
            ITEMS.register("qi_gathering_manual",
                    () -> new QiGatheringManualItem(new Item.Properties().stacksTo(1)));

    /**
     * One manual item for every martial art. Which art a given stack teaches lives in its
     * {@code technique} data component, so a datapack can hand out a manual for a technique
     * it defined without needing a new item registered in Java.
     */
    public static final DeferredHolder<Item, MartialManualItem> MARTIAL_MANUAL =
            ITEMS.register("martial_manual",
                    () -> new MartialManualItem(new Item.Properties().stacksTo(1)));

    /** The spirit vein's item form, so it can be placed and appears in the creative tab. */
    public static final DeferredHolder<Item, Item> SPIRIT_VEIN_ITEM =
            ITEMS.register("spirit_vein", () -> ModBlocks.blockItem(ModBlocks.SPIRIT_VEIN.get()));

    public static final DeferredHolder<Item, Item> PILL_CAULDRON_ITEM =
            ITEMS.register("pill_cauldron", () -> ModBlocks.blockItem(ModBlocks.PILL_CAULDRON.get()));

    // --- Herbs: the inputs to alchemy ------------------------------------------------
    //
    // Block items, so a herb is both what the cauldron takes and something you can plant.
    // The ids are unchanged from when these were plain items, so the pill formulas and any
    // herbs already sitting in a world's chests still resolve.

    /** A common spiritual root. Brews into a Qi recovery pill. */
    public static final DeferredHolder<Item, Item> SPIRIT_GINSENG =
            ITEMS.register("spirit_ginseng", () -> ModBlocks.blockItem(ModBlocks.SPIRIT_GINSENG.get()));

    /**
     * Grows where Qi has pooled for a long time. Brews into a deviation remedy. Placed on water,
     * like a lily pad, since that is where it grows.
     */
    public static final DeferredHolder<Item, Item> BLOOD_LOTUS =
            ITEMS.register("blood_lotus", () -> new PlaceOnWaterBlockItem(
                    ModBlocks.BLOOD_LOTUS.get(), new Item.Properties()));

    /** Rare, and the only route to refining a foundation without patience. */
    public static final DeferredHolder<Item, Item> JADE_CHRYSANTHEMUM =
            ITEMS.register("jade_chrysanthemum", () -> ModBlocks.blockItem(ModBlocks.JADE_CHRYSANTHEMUM.get()));

    // --- Pills: refined, and each one answers a real problem -------------------------

    public static final DeferredHolder<Item, PillItem> QI_RECOVERY_PILL =
            ITEMS.register("qi_recovery_pill", () -> new PillItem(
                    new Item.Properties().stacksTo(16), PillItem.Effect.QI_RECOVERY, 0.40D));

    /** The answer to a failed breakthrough, which until now could only be waited out. */
    public static final DeferredHolder<Item, PillItem> DEVIATION_REMEDY_PILL =
            ITEMS.register("deviation_remedy_pill", () -> new PillItem(
                    new Item.Properties().stacksTo(16), PillItem.Effect.DEVIATION_REMEDY, 1.0D));

    public static final DeferredHolder<Item, PillItem> FOUNDATION_PILL =
            ITEMS.register("foundation_pill", () -> new PillItem(
                    new Item.Properties().stacksTo(16), PillItem.Effect.PURITY,
                    PillItem.FOUNDATION_PURITY_GRANT));

    /** A spawn egg, since these belong in compounds a later milestone will generate. */
    public static final DeferredHolder<Item, Item> MARTIAL_ARTIST_SPAWN_EGG =
            ITEMS.register("martial_artist_spawn_egg", () -> new DeferredSpawnEggItem(
                    ModEntities.MARTIAL_ARTIST, 0x3B4252, 0xB48EAD, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    private ModItems() {
    }
}
