package com.andymods.murimcultivation.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the System tracks about a cultivator's standing: earned and spent stat points,
 * and the titles they have won.
 *
 * <p>Grouped into one nested object rather than living as loose fields on
 * {@code CultivationData}, because {@code RecordCodecBuilder.group} caps at sixteen fields and
 * that budget was nearly spent. Nesting cohesive state costs one field instead of four and
 * models the domain better besides.
 */
public class SystemProgress {

    private int unspentPoints;
    private final Map<StatType, Integer> allocations;
    private final Set<ResourceLocation> titles;
    private Optional<ResourceLocation> equippedTitle;

    public static final Codec<SystemProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("unspent_points", 0).forGetter(SystemProgress::unspentPoints),
            Codec.unboundedMap(StatType.CODEC, Codec.INT).optionalFieldOf("allocations", Map.of())
                    .forGetter(progress -> Map.copyOf(progress.allocations)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("titles", List.of())
                    .forGetter(progress -> List.copyOf(progress.titles)),
            ResourceLocation.CODEC.optionalFieldOf("equipped_title").forGetter(SystemProgress::equippedTitle)
    ).apply(instance, SystemProgress::new));

    public SystemProgress() {
        this(0, Map.of(), List.of(), Optional.empty());
    }

    public SystemProgress(int unspentPoints,
                          Map<StatType, Integer> allocations,
                          List<ResourceLocation> titles,
                          Optional<ResourceLocation> equippedTitle) {
        this.unspentPoints = Math.max(0, unspentPoints);
        this.allocations = new EnumMap<>(StatType.class);
        allocations.forEach((stat, points) -> this.allocations.put(stat, StatType.clampPoints(points)));
        this.titles = new LinkedHashSet<>(titles);
        this.equippedTitle = equippedTitle;
    }

    // --- Points -----------------------------------------------------------------------

    public int unspentPoints() {
        return unspentPoints;
    }

    public void grantPoints(int amount) {
        this.unspentPoints = Math.max(0, this.unspentPoints + Math.max(0, amount));
    }

    public int pointsIn(StatType stat) {
        return allocations.getOrDefault(stat, 0);
    }

    public Map<StatType, Integer> allocations() {
        return Collections.unmodifiableMap(allocations);
    }

    /** Total points ever committed, across every stat. */
    public int totalSpent() {
        return allocations.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Commits points to a stat.
     *
     * <p>Refuses rather than clamping when the player cannot afford it or the stat is already
     * capped, so a UI that sends a stale click cannot quietly conjure points.
     *
     * @return whether the points were spent
     */
    public boolean spend(StatType stat, int points) {
        if (points <= 0 || points > unspentPoints) {
            return false;
        }
        int current = pointsIn(stat);
        if (current + points > StatType.MAX_POINTS_PER_STAT) {
            return false;
        }
        allocations.put(stat, current + points);
        unspentPoints -= points;
        return true;
    }

    /** Returns every committed point to the unspent pool. Used by a respec or {@code /murim reset}. */
    public int refundAll() {
        int refunded = totalSpent();
        allocations.clear();
        unspentPoints += refunded;
        return refunded;
    }

    // --- Titles -----------------------------------------------------------------------

    public Set<ResourceLocation> titles() {
        return Collections.unmodifiableSet(titles);
    }

    public boolean hasTitle(ResourceLocation title) {
        return titles.contains(title);
    }

    public boolean grantTitle(ResourceLocation title) {
        return titles.add(title);
    }

    /** The title currently worn, whose attribute grants are applied. */
    public Optional<ResourceLocation> equippedTitle() {
        return equippedTitle;
    }

    /** Equips a title the player owns. Passing null or an unowned title clears the slot. */
    public boolean equipTitle(ResourceLocation title) {
        if (title == null) {
            equippedTitle = Optional.empty();
            return true;
        }
        if (!titles.contains(title)) {
            return false;
        }
        equippedTitle = Optional.of(title);
        return true;
    }

    public void copyFrom(SystemProgress source) {
        this.unspentPoints = source.unspentPoints;
        this.allocations.clear();
        this.allocations.putAll(source.allocations);
        this.titles.clear();
        this.titles.addAll(source.titles);
        this.equippedTitle = source.equippedTitle;
    }
}
