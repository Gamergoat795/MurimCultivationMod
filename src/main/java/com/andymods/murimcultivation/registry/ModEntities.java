package com.andymods.murimcultivation.registry;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.npc.MartialArtistEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MurimCultivationMod.MODID);

    /**
     * A sect martial artist.
     *
     * <p>{@code MobCategory.CREATURE}, so it is placed during chunk generation alongside the
     * animals of the biome and counts against the peaceful-mob cap rather than the monster one.
     * Where they appear is decided by data, not here: {@code add_martial_artists.json} adds the
     * spawn to every biome some sect claims as territory.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<MartialArtistEntity>> MARTIAL_ARTIST =
            ENTITIES.register("martial_artist", () -> EntityType.Builder
                    .of(MartialArtistEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("martial_artist"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    private ModEntities() {
    }
}
