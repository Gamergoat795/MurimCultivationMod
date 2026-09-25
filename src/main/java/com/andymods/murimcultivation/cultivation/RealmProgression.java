package com.andymods.murimcultivation.cultivation;

import com.andymods.murimcultivation.MurimRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Read-only views over the realm registry, ordered by {@link Realm#tier()}.
 *
 * <p>Datapack registries are unordered maps, so the ladder is derived from the tier field
 * rather than from load order. That is what lets a datapack slot a new realm between two
 * existing ones without editing either of them.
 */
public final class RealmProgression {

    private static final Comparator<Holder.Reference<Realm>> BY_TIER =
            Comparator.comparingInt(holder -> holder.value().tier());

    public static Registry<Realm> registry(RegistryAccess access) {
        return access.registryOrThrow(MurimRegistries.REALM);
    }

    /** Every realm, lowest tier first. */
    public static List<Holder.Reference<Realm>> ladder(Registry<Realm> registry) {
        return registry.holders().sorted(BY_TIER).toList();
    }

    /** The realm a brand-new cultivator starts in: the lowest tier that exists. */
    public static Optional<Holder.Reference<Realm>> lowest(Registry<Realm> registry) {
        return registry.holders().min(BY_TIER);
    }

    public static Optional<Holder.Reference<Realm>> highest(Registry<Realm> registry) {
        return registry.holders().max(BY_TIER);
    }

    /**
     * The next realm above the given one — the lowest realm whose tier is strictly greater.
     * Empty at the top of the ladder.
     */
    public static Optional<Holder.Reference<Realm>> next(Registry<Realm> registry, Realm current) {
        return registry.holders()
                .filter(holder -> holder.value().tier() > current.tier())
                .min(BY_TIER);
    }

    public static Optional<Holder.Reference<Realm>> previous(Registry<Realm> registry, Realm current) {
        return registry.holders()
                .filter(holder -> holder.value().tier() < current.tier())
                .max(BY_TIER);
    }

    public static boolean isHighest(Registry<Realm> registry, Realm current) {
        return next(registry, current).isEmpty();
    }

    /**
     * The highest realm at or below a tier, so a roll that lands in a gap a datapack left in
     * the ladder still resolves to a real realm. Empty only below the lowest realm.
     */
    public static Optional<Holder.Reference<Realm>> atOrBelow(Registry<Realm> registry, int tier) {
        return registry.holders()
                .filter(holder -> holder.value().tier() <= tier)
                .max(BY_TIER);
    }

    /** Resolves a key to its realm, or empty if a datapack removed it out from under a save. */
    public static Optional<Realm> byKey(Registry<Realm> registry, ResourceKey<Realm> key) {
        return registry.getOptional(key);
    }

    /** One-based position on the ladder, for display: "Realm 4 of 9". */
    public static int ladderPosition(Registry<Realm> registry, Realm realm) {
        return (int) registry.holders().filter(holder -> holder.value().tier() <= realm.tier()).count();
    }

    public static int ladderSize(Registry<Realm> registry) {
        return registry.size();
    }

    private RealmProgression() {
    }
}
