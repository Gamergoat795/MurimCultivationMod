package com.andymods.murimcultivation.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A cultivator's standing with the System: what is in progress, what is finished, and when the
 * repeatable set last cleared.
 *
 * <p>Progress is keyed by quest id and then by {@link QuestObjective#progressKey()} rather than
 * by objective index, so reordering a quest's objectives in a datapack cannot silently reassign
 * a player's counters to the wrong goal.
 *
 * <p>Only cumulative objectives are stored. Threshold objectives — reach a realm, hold N open
 * meridians — are re-read from live state each time, so a cultivator who loses a meridian to a
 * shattered breakthrough correctly loses credit for it too.
 */
public class QuestLog {

    private final Map<ResourceLocation, Map<String, Integer>> progress;
    private final Set<ResourceLocation> completed;
    private final Set<ResourceLocation> claimedDailies;
    private long lastDailyResetDay;

    public static final Codec<QuestLog> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .optionalFieldOf("progress", Map.of())
                    .forGetter(log -> Map.copyOf(log.progress)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("completed", List.of())
                    .forGetter(log -> List.copyOf(log.completed)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("claimed_dailies", List.of())
                    .forGetter(log -> List.copyOf(log.claimedDailies)),
            Codec.LONG.optionalFieldOf("last_daily_reset_day", -1L).forGetter(QuestLog::lastDailyResetDay)
    ).apply(instance, QuestLog::new));

    public QuestLog() {
        this(Map.of(), List.of(), List.of(), -1L);
    }

    public QuestLog(Map<ResourceLocation, Map<String, Integer>> progress,
                    List<ResourceLocation> completed,
                    List<ResourceLocation> claimedDailies,
                    long lastDailyResetDay) {
        this.progress = new HashMap<>();
        progress.forEach((quest, counters) -> this.progress.put(quest, new HashMap<>(counters)));
        this.completed = new LinkedHashSet<>(completed);
        this.claimedDailies = new LinkedHashSet<>(claimedDailies);
        this.lastDailyResetDay = lastDailyResetDay;
    }

    // --- Progress ---------------------------------------------------------------------

    public int progressOf(ResourceLocation quest, QuestObjective objective) {
        return progress.getOrDefault(quest, Map.of()).getOrDefault(objective.progressKey(), 0);
    }

    /**
     * Advances a cumulative objective's counter.
     *
     * @return the new value
     */
    public int advance(ResourceLocation quest, QuestObjective objective, int by) {
        if (by <= 0) {
            return progressOf(quest, objective);
        }
        Map<String, Integer> counters = progress.computeIfAbsent(quest, key -> new HashMap<>());
        int updated = Math.min(objective.amount(), counters.getOrDefault(objective.progressKey(), 0) + by);
        counters.put(objective.progressKey(), updated);
        return updated;
    }

    public void clearProgress(ResourceLocation quest) {
        progress.remove(quest);
    }

    public Map<ResourceLocation, Map<String, Integer>> progress() {
        return Collections.unmodifiableMap(progress);
    }

    // --- Completion -------------------------------------------------------------------

    public boolean isCompleted(ResourceLocation quest) {
        return completed.contains(quest);
    }

    public Set<ResourceLocation> completed() {
        return Collections.unmodifiableSet(completed);
    }

    /** Marks a story quest permanently done, or a daily done until the next reset. */
    public boolean complete(ResourceLocation quest, QuestCategory category) {
        clearProgress(quest);
        return category.repeatable() ? claimedDailies.add(quest) : completed.add(quest);
    }

    /** Whether a daily has already been claimed in the current period. */
    public boolean isDailyClaimed(ResourceLocation quest) {
        return claimedDailies.contains(quest);
    }

    public Set<ResourceLocation> claimedDailies() {
        return Collections.unmodifiableSet(claimedDailies);
    }

    // --- Daily reset ------------------------------------------------------------------

    public long lastDailyResetDay() {
        return lastDailyResetDay;
    }

    /**
     * Clears the repeatable set if enough in-game days have passed.
     *
     * @param currentDay the world's current day number
     * @param intervalDays how many days a period lasts; 1 means every dawn
     * @return whether a reset happened
     */
    public boolean resetDailiesIfDue(long currentDay, int intervalDays) {
        int interval = Math.max(1, intervalDays);
        if (lastDailyResetDay >= 0 && currentDay < lastDailyResetDay + interval) {
            return false;
        }

        boolean hadAny = !claimedDailies.isEmpty();
        claimedDailies.forEach(progress::remove);
        claimedDailies.clear();
        lastDailyResetDay = currentDay;
        // A first-ever reset establishes the baseline day without counting as a real reset.
        return hadAny;
    }

    public void copyFrom(QuestLog source) {
        this.progress.clear();
        source.progress.forEach((quest, counters) -> this.progress.put(quest, new HashMap<>(counters)));
        this.completed.clear();
        this.completed.addAll(source.completed);
        this.claimedDailies.clear();
        this.claimedDailies.addAll(source.claimedDailies);
        this.lastDailyResetDay = source.lastDailyResetDay;
    }
}
