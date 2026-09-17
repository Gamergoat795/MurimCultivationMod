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
     * <p>{@code MobCategory.MISC} rather than {@code CREATURE} deliberately: natural spawning is
     * not wanted, since these belong in sect compounds that a later milestone will generate, not
     * scattered across the countryside.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<MartialArtistEntity>> MARTIAL_ARTIST =
            ENTITIES.register("martial_artist", () -> EntityType.Builder
                    .of(MartialArtistEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("martial_artist"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    private ModEntities() {
    }
}
