package com.andymods.murimcultivation.technique;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Sustained techniques — the ones that stay on for a while rather than firing once.
 *
 * <p>All of them are held as transient state with a tick countdown, and all of their attribute
 * modifiers are transient too. That means a crash, a relog or a death cannot leave a player
 * permanently buffed, which is the failure mode this kind of timed effect usually has: the
 * modifier gets written to NBT, the removal never runs, and the player keeps the bonus forever.
 */
public final class TechniqueBuffs {

    /** Every attribute a technique buff is allowed to touch, so expiry can clean up exhaustively. */
    private static final List<Holder<Attribute>> MANAGED_ATTRIBUTES = List.of(
            Attributes.ATTACK_DAMAGE,
            Attributes.ENTITY_INTERACTION_RANGE,
            Attributes.MOVEMENT_SPEED,
            Attributes.KNOCKBACK_RESISTANCE);

    private TechniqueBuffs() {
    }

    /** Starts or refreshes a sustained technique. */
    public static void start(ServerPlayer player, ResourceLocation id, int durationTicks) {
        CultivationService.data(player).setTechniqueActive(id, durationTicks);
    }

    /**
     * Adds a transient attribute modifier owned by a technique. Transient means it is never
     * serialized, so it cannot outlive the buff even if expiry is somehow missed.
     */
    public static void addModifier(ServerPlayer player, ResourceLocation id,
                                   Holder<Attribute> attribute, double amount,
                                   AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        ResourceLocation modifierId = modifierId(id, attribute);
        instance.removeModifier(modifierId);
        instance.addTransientModifier(new AttributeModifier(modifierId, amount, operation));
    }

    /** Ends a sustained technique and strips every modifier it owned. */
    public static void expire(ServerPlayer player, ResourceLocation id) {
        CultivationService.data(player).clearTechniqueActive(id);

        for (Holder<Attribute> attribute : MANAGED_ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(modifierId(id, attribute));
            }
        }

        player.displayClientMessage(Component.translatable(
                "murimcultivation.technique.expired",
                Component.translatable("murimcultivation.technique." + id.getPath())), true);
    }

    /**
     * Ticks every sustained technique, expiring the ones that ran out and draining Qi for the
     * ones that cost upkeep. Called once per player tick.
     */
    public static void tick(ServerPlayer player, CultivationData data) {
        if (!data.hasAnyTechniqueActive()) {
            return;
        }

        drainUpkeep(player, data);

        for (ResourceLocation expired : data.tickActiveTechniques()) {
            expire(player, expired);
        }
    }

    /**
     * Charges per-tick Qi upkeep for channelled techniques, ending any the player can no
     * longer afford. Iron Body holding after the Qi runs out would make it strictly better
     * than a timed buff.
     */
    private static void drainUpkeep(ServerPlayer player, CultivationData data) {
        for (ResourceLocation id : data.activeTechniques()) {
            Technique technique = TechniqueService.byId(player, id).orElse(null);
            if (technique == null) {
                continue;
            }
            double upkeep = technique.power().qiPerTick();
            if (upkeep <= 0.0D) {
                continue;
            }
            if (!data.spendQi(upkeep)) {
                expire(player, id);
                player.displayClientMessage(
                        Component.translatable("murimcultivation.technique.upkeep_failed"), true);
            }
        }
    }

    /** Strips every technique modifier from a player. Used on respawn and dimension change. */
    public static void clearAll(ServerPlayer player) {
        CultivationData data = CultivationService.data(player);
        for (ResourceLocation id : data.activeTechniques()) {
            for (Holder<Attribute> attribute : MANAGED_ATTRIBUTES) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(modifierId(id, attribute));
                }
            }
        }
        data.clearTransientCombatState();
    }

    /** A modifier id unique to one technique's effect on one attribute. */
    private static ResourceLocation modifierId(ResourceLocation technique, Holder<Attribute> attribute) {
        String attributePath = attribute.unwrapKey()
                .map(key -> key.location().getPath())
                .orElse("unknown");
        return MurimCultivationMod.id("technique/" + technique.getPath() + "/" + attributePath);
    }
}
