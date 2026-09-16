package com.andymods.murimcultivation.cultivation;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.network.SyncCultivationPayload;
import com.andymods.murimcultivation.network.SyncCultivationValuesPayload;
import com.andymods.murimcultivation.registry.ModAttachments;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/**
 * The one place that knows how cultivation numbers are derived and applied.
 *
 * <p>Everything that can change a player's realm, meridians or Qi routes through here so
 * that the derived state — Qi capacity, attribute modifiers, the client's copy of the data —
 * is recomputed in exactly one way. Scattering that logic across item, packet and event
 * handlers is how a stat mod ends up with bonuses that vanish on death and HUDs that lie.
 */
public final class CultivationService {

    /** Namespace for the attribute modifiers this mod owns, so they can be removed precisely. */
    private static final String REALM_MODIFIER_PREFIX = "realm_bonus";

    public static CultivationData data(Player player) {
        return player.getData(ModAttachments.CULTIVATION);
    }

    public static Registry<Realm> realmRegistry(Player player) {
        return RealmProgression.registry(player.level().registryAccess());
    }

    /**
     * The player's current realm. An un-placed or dangling realm key falls back to the lowest
     * realm on the ladder, so a save that outlived a datapack change still loads.
     */
    public static Optional<Realm> realmOf(Player player) {
        return realmOf(realmRegistry(player), data(player));
    }

    public static Optional<Realm> realmOf(Registry<Realm> registry, CultivationData data) {
        Optional<Realm> stored = data.realmKey().flatMap(key -> RealmProgression.byKey(registry, key));
        if (stored.isPresent()) {
            return stored;
        }
        return RealmProgression.lowest(registry).map(Holder::value);
    }

    // --- Derived values ---------------------------------------------------------------

    /**
     * Maximum Qi: the realm's base capacity plus what the open meridian network adds.
     * Always computed, never stored, so it cannot fall out of step with the realm.
     */
    public static double qiCapacity(Registry<Realm> registry, CultivationData data) {
        double base = realmOf(registry, data).map(Realm::qiCapacity).orElse(100.0D);
        int primary = data.openMeridianCount() - data.openExtraordinaryCount();
        int extraordinary = data.openExtraordinaryCount();
        return base
                + primary * MurimConfig.qiPerPrimaryMeridian()
                + extraordinary * MurimConfig.qiPerExtraordinaryVessel();
    }

    public static double qiCapacity(Player player) {
        return qiCapacity(realmRegistry(player), data(player));
    }

    /** Passive Qi regeneration per second, from the realm and scaled by foundation purity. */
    public static double qiRegenPerSecond(Registry<Realm> registry, CultivationData data) {
        double base = realmOf(registry, data).map(Realm::qiRegenPerSecond).orElse(1.0D);
        // A clean foundation circulates Qi better: 0 purity halves regen, 100 purity adds 50%.
        double purityFactor = 0.5D + (data.purity() / CultivationData.MAX_PURITY);
        return base * purityFactor;
    }

    /**
     * Progress needed to reach the next step, whether that is the next substage or the
     * breakthrough at the top of the realm.
     */
    public static double progressForNextStep(Registry<Realm> registry, CultivationData data) {
        return realmOf(registry, data)
                .map(realm -> realm.progressToLeave(data.substage()))
                .orElse(Double.MAX_VALUE);
    }

    /** Fraction of the way to the next step, 0..1, for HUD bars. */
    public static double stepCompletion(Registry<Realm> registry, CultivationData data) {
        double required = progressForNextStep(registry, data);
        if (required <= 0.0D || required == Double.MAX_VALUE) {
            return 0.0D;
        }
        return Math.min(1.0D, data.progress() / required);
    }

    /** Whether the player has banked enough progress to take the next step. */
    public static boolean canAdvance(Registry<Realm> registry, CultivationData data) {
        return data.progress() >= progressForNextStep(registry, data);
    }

    // --- Mutations --------------------------------------------------------------------

    /**
     * Advances substages while there is banked progress to pay for them, stopping at Peak —
     * where the next step is a breakthrough the player must choose to attempt.
     *
     * @return the number of substages gained
     */
    public static int advanceSubstages(ServerPlayer player) {
        Registry<Realm> registry = realmRegistry(player);
        CultivationData data = data(player);
        int gained = 0;

        while (!data.substage().isLast() && canAdvance(registry, data)) {
            double cost = progressForNextStep(registry, data);
            data.setProgress(data.progress() - cost);
            data.setSubstage(data.substage().next());
            gained++;
        }

        if (gained > 0) {
            applyRealmAttributes(player);
            syncToClient(player);
        }
        return gained;
    }

    /** Places the player in a realm outright, recalculating everything that depends on it. */
    public static void setRealm(ServerPlayer player, ResourceKey<Realm> realm, Substage substage) {
        CultivationData data = data(player);
        data.setRealmKey(realm);
        data.setSubstage(substage);
        clampQiToCapacity(player, data);
        applyRealmAttributes(player);
        syncToClient(player);
    }

    /**
     * Re-clamps stored Qi against the current capacity. Called whenever the realm or the
     * meridian network changes, since both move the ceiling.
     */
    public static void clampQiToCapacity(Player player, CultivationData data) {
        data.setQi(data.qi(), qiCapacity(player));
    }

    /**
     * Recomputes every attribute modifier this mod owns.
     *
     * <p>Modifiers are <em>transient</em>, so they are never written to the player's NBT and
     * cannot outlive the mod, but that also means they must be re-applied on every login,
     * respawn and dimension change — see {@code PlayerLifecycleEvents}.
     *
     * <p>Notably this does not touch the player's current health. Topping a player up as a
     * side effect of recalculating their maximum turns every relog into a free heal.
     */
    public static void applyRealmAttributes(ServerPlayer player) {
        Registry<Realm> registry = realmRegistry(player);

        // Strip our modifier from every attribute that any realm mentions, so switching to a
        // realm that does not grant an attribute correctly drops the old bonus.
        for (Realm realm : registry) {
            for (AttributeGrant grant : realm.attributes()) {
                AttributeInstance instance = player.getAttribute(grant.attribute());
                if (instance != null) {
                    instance.removeModifier(modifierId(grant.attribute()));
                }
            }
        }

        Optional<Realm> current = realmOf(registry, data(player));
        if (current.isEmpty()) {
            return;
        }

        for (AttributeGrant grant : current.get().attributes()) {
            AttributeInstance instance = player.getAttribute(grant.attribute());
            if (instance == null) {
                continue;
            }
            instance.addTransientModifier(new AttributeModifier(
                    modifierId(grant.attribute()), grant.amount(), grant.operation()));
        }

        // A realm loss can lower max health below current health; clamp rather than leave the
        // player rendering more hearts than they have.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * A stable, unique modifier id per attribute, e.g.
     * {@code murimcultivation:realm_bonus/minecraft/generic.max_health}.
     */
    public static ResourceLocation modifierId(Holder<Attribute> attribute) {
        ResourceLocation attributeId = attribute.unwrapKey()
                .map(ResourceKey::location)
                .orElseGet(() -> MurimCultivationMod.id("unknown_attribute"));
        return MurimCultivationMod.id(
                REALM_MODIFIER_PREFIX + "/" + attributeId.getNamespace() + "/" + attributeId.getPath());
    }

    // --- Sync -------------------------------------------------------------------------

    /** Pushes the player's full cultivation state to their own client. */
    public static void syncToClient(ServerPlayer player) {
        CultivationData data = data(player);
        PacketDistributor.sendToPlayer(player, new SyncCultivationPayload(data, data.isMeditating()));
    }

    /**
     * Pushes only the fast-changing values. Used by the per-second cultivation tick, where
     * sending the player's whole technique list and meridian network would be wasteful.
     */
    public static void syncValuesToClient(ServerPlayer player) {
        CultivationData data = data(player);
        PacketDistributor.sendToPlayer(player, new SyncCultivationValuesPayload(
                data.qi(), data.progress(), data.purity(), data.isMeditating()));
    }

    private CultivationService() {
    }
}
