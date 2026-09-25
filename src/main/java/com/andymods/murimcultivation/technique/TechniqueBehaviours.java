package com.andymods.murimcultivation.technique;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.technique.behaviour.MovementBehaviours;
import com.andymods.murimcultivation.technique.behaviour.OffensiveBehaviours;
import com.andymods.murimcultivation.technique.behaviour.SustainBehaviours;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registry of technique behaviours: the code half of the data/code split.
 *
 * <p>Keyed by {@link ResourceLocation} so a datapack's {@code "behaviour"} field can name one.
 * A typo in a datapack is caught as an unknown behaviour with a clear message rather than
 * silently doing nothing, and {@code TechniqueDefinitionsTest} asserts every shipped technique
 * names one that exists.
 */
public final class TechniqueBehaviours {

    private static final Map<ResourceLocation, TechniqueBehaviour> BEHAVIOURS = new ConcurrentHashMap<>();

    public static final ResourceLocation SWORD_QI = MurimCultivationMod.id("sword_qi");
    public static final ResourceLocation SWORD_FORCE = MurimCultivationMod.id("sword_force");
    public static final ResourceLocation DIVINE_PALM = MurimCultivationMod.id("divine_palm");
    public static final ResourceLocation SHADOWLESS_STEP = MurimCultivationMod.id("shadowless_step");
    public static final ResourceLocation QINGGONG = MurimCultivationMod.id("qinggong");
    public static final ResourceLocation WATER_WALKING = MurimCultivationMod.id("water_walking");
    public static final ResourceLocation IRON_BODY = MurimCultivationMod.id("iron_body");
    public static final ResourceLocation INTERNAL_HEALING = MurimCultivationMod.id("internal_healing");

    static {
        register(SWORD_QI, OffensiveBehaviours::swordQi);
        register(DIVINE_PALM, OffensiveBehaviours::divinePalm);
        register(SWORD_FORCE, OffensiveBehaviours::swordForce);

        register(SHADOWLESS_STEP, MovementBehaviours::shadowlessStep);
        register(QINGGONG, MovementBehaviours::qinggong);
        register(WATER_WALKING, MovementBehaviours::waterWalking);

        register(IRON_BODY, SustainBehaviours::ironBody);
        register(INTERNAL_HEALING, SustainBehaviours::internalHealing);
    }

    /**
     * The behaviours a non-player may cast: the instant ones. Sword Force, Iron Body, Qinggong
     * and Water Walking are sustained, and keep their duration and Qi upkeep in a player's
     * {@code CultivationData}, so they are never offered to a martial artist.
     */
    private static final Set<ResourceLocation> NPC_USABLE =
            Set.of(SWORD_QI, DIVINE_PALM, SHADOWLESS_STEP, INTERNAL_HEALING);

    private TechniqueBehaviours() {
    }

    /** Whether a martial artist may cast a technique that names this behaviour. */
    public static boolean npcUsable(ResourceLocation behaviour) {
        return NPC_USABLE.contains(behaviour);
    }

    /** Every behaviour a non-player may cast, for validation. */
    public static Set<ResourceLocation> npcUsableIds() {
        return NPC_USABLE;
    }

    public static void register(ResourceLocation id, TechniqueBehaviour behaviour) {
        BEHAVIOURS.put(id, behaviour);
    }

    public static Optional<TechniqueBehaviour> get(ResourceLocation id) {
        return Optional.ofNullable(BEHAVIOURS.get(id));
    }

    public static boolean isRegistered(ResourceLocation id) {
        return BEHAVIOURS.containsKey(id);
    }

    /** Every registered behaviour id, for validation and command suggestions. */
    public static Set<ResourceLocation> ids() {
        return Collections.unmodifiableSet(BEHAVIOURS.keySet());
    }
}
