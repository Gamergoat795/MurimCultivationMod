package com.andymods.murimcultivation;

import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.cultivation.Realm;
import com.andymods.murimcultivation.alchemy.PillRecipe;
import com.andymods.murimcultivation.npc.MartialArtistEntity;
import com.andymods.murimcultivation.sect.Sect;
import com.andymods.murimcultivation.system.SystemQuest;
import com.andymods.murimcultivation.system.Title;
import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.registry.ModAttachments;
import com.andymods.murimcultivation.registry.ModBlockEntities;
import com.andymods.murimcultivation.registry.ModBlocks;
import com.andymods.murimcultivation.registry.ModEntities;
import com.andymods.murimcultivation.registry.ModDataComponents;
import com.andymods.murimcultivation.registry.ModCreativeTabs;
import com.andymods.murimcultivation.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

@Mod(MurimCultivationMod.MODID)
public class MurimCultivationMod {

    public static final String MODID = "murimcultivation";

    public MurimCultivationMod(IEventBus modBus, ModContainer modContainer) {
        ModAttachments.register(modBus);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModEntities.register(modBus);
        ModDataComponents.register(modBus);
        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);

        // SERVER, not COMMON: these values define gameplay and the client derives Qi capacity
        // from them for the HUD. A SERVER config is synced to connecting clients, so the bar
        // a player sees is bounded by the same numbers the server is enforcing.
        modContainer.registerConfig(ModConfig.Type.SERVER, MurimConfig.SPEC);
    }

    /** A {@link ResourceLocation} in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {

        /**
         * Registers the datapack registries. The third argument is the network codec:
         * passing it means realms are synced to connecting clients, which the HUD needs
         * so it can render realm names and aura colours without asking the server.
         */
        /** Mobs need an attribute supplier or spawning one throws. */
        @SubscribeEvent
        public static void registerEntityAttributes(EntityAttributeCreationEvent event) {
            event.put(ModEntities.MARTIAL_ARTIST.get(), MartialArtistEntity.createAttributes().build());
        }

        /**
         * Where a martial artist may naturally appear. Without this the spawn the biome
         * modifier adds would fall back to no placement rule at all, and one could spawn
         * underwater or inside a leaf canopy.
         */
        @SubscribeEvent
        public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
            event.register(ModEntities.MARTIAL_ARTIST.get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, MartialArtistEntity::checkSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }

        @SubscribeEvent
        public static void registerDatapackRegistries(DataPackRegistryEvent.NewRegistry event) {
            event.dataPackRegistry(MurimRegistries.REALM, Realm.CODEC, Realm.CODEC);
            event.dataPackRegistry(MurimRegistries.TECHNIQUE, Technique.CODEC, Technique.CODEC);
            event.dataPackRegistry(MurimRegistries.QUEST, SystemQuest.CODEC, SystemQuest.CODEC);
            event.dataPackRegistry(MurimRegistries.TITLE, Title.CODEC, Title.CODEC);
            event.dataPackRegistry(MurimRegistries.SECT, Sect.CODEC, Sect.CODEC);
            event.dataPackRegistry(MurimRegistries.PILL_RECIPE, PillRecipe.CODEC, PillRecipe.CODEC);
        }

        private ModBusEvents() {
        }
    }
}
