package com.andymods.murimcultivation.technique;

import com.andymods.murimcultivation.MurimCultivationMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registry of technique behaviours: the code half of the data/code split.
 *
 * <p>Keyed by {@link ResourceLocation} so a datapack's {@code "behaviour"} field can name one.
 * Every id the shipped techniques reference is registered here, which means a typo in a
 * datapack is caught as an unknown behaviour at cast time with a clear message, rather than
 * silently doing nothing.
 *
 * <p>M3a registers all eight ids so the whole pipeline is exercised end to end, with
 * {@code internal_healing} fully implemented; the remaining seven are placeholders that
 * refuse the cast (and so refund it) until M3b fills them in.
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
        // Fully implemented: the simplest behaviour, so M3a proves the pipeline end to end.
        register(INTERNAL_HEALING, TechniqueBehaviours::internalHealing);

        // Landing in M3b. Registered now so the shipped technique JSONs resolve, and so a
        // player who casts one gets told it is unfinished rather than silently losing Qi.
        registerPlaceholder(SWORD_QI);
        registerPlaceholder(SWORD_FORCE);
        registerPlaceholder(DIVINE_PALM);
        registerPlaceholder(SHADOWLESS_STEP);
        registerPlaceholder(QINGGONG);
        registerPlaceholder(WATER_WALKING);
        registerPlaceholder(IRON_BODY);
    }

    private TechniqueBehaviours() {
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

    /** Every registered behaviour id, for validation and for {@code /murim technique} suggestions. */
    public static java.util.Set<ResourceLocation> ids() {
        return java.util.Collections.unmodifiableSet(BEHAVIOURS.keySet());
    }

    // --- Implementations ---------------------------------------------------------------

    /**
     * Internal Healing: circulate Qi inward to knit yourself back together.
     *
     * <p>Refuses at full health, which is what makes the refund path in
     * {@link TechniqueService} worth having — casting it topped up should not cost anything.
     */
    private static boolean internalHealing(net.minecraft.server.level.ServerPlayer player,
                                           Technique technique, int mastery) {
        if (player.getHealth() >= player.getMaxHealth()) {
            player.displayClientMessage(
                    Component.translatable("murimcultivation.technique.already_healthy"), true);
            return false;
        }

        double amount = technique.power().healAmount() + TechniqueMastery.damage(technique.power(), mastery);
        player.heal((float) amount);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.6F);
        return true;
    }

    /** Placeholder for a behaviour arriving in M3b: refuses, so the cast is refunded. */
    private static void registerPlaceholder(ResourceLocation id) {
        register(id, (player, technique, mastery) -> {
            player.displayClientMessage(Component.translatable(
                    "murimcultivation.technique.not_implemented", technique.displayName()), true);
            return false;
        });
    }
}
