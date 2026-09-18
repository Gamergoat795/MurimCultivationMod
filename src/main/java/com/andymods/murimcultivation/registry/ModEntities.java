package com.andymods.murimcultivation.registry;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.npc.MartialArtistEntity;
import com.andymods.murimcultivation.npc.WanderingWarriorEntity;
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

    /**
     * A wandering warrior.
     *
     * <p>{@code MobCategory.MONSTER} rather than {@code CREATURE}, which is the opposite choice to
     * the teacher above and for a concrete reason: {@code CREATURE} mobs are placed during chunk
     * generation and then essentially never respawn, so warriors would be a finite resource that a
     * player exhausts. {@code MONSTER} keeps them appearing. The category's usual darkness
     * requirement is not inherited — the spawn rule registered for this type permits any light
     * level, because these are people on the road rather than something that crawls out of the
     * night.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<WanderingWarriorEntity>> WANDERING_WARRIOR =
            ENTITIES.register("wandering_warrior", () -> EntityType.Builder
                    .of(WanderingWarriorEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("wandering_warrior"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    private ModEntities() {
    }
}
