package com.andymods.murimcultivation.standing;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The murim's ranking list: who stands highest among those present.
 *
 * <p>A staple of the genre — every story has its list of the Ten Great Masters — and cheap here,
 * because everything it orders by already exists. Realm first, then substage, then how far along
 * the next step they are; honour breaks a dead heat, because between two equals the murim
 * remembers the one with the better name.
 *
 * <p>Online players only. An offline player's data is not loaded, and a list that silently mixed
 * stale entries with live ones would be worse than a short one.
 */
public final class MurimRankings {

    private MurimRankings() {
    }

    /** One line of the list. */
    public record Entry(String name, int realmTier, int substage, double progress, int honour) {
    }

    public static final Comparator<Entry> ORDER = Comparator
            .comparingInt(Entry::realmTier)
            .thenComparingInt(Entry::substage)
            .thenComparingDouble(Entry::progress)
            .thenComparingInt(Entry::honour)
            .reversed()
            // Last, and ascending, so the order is total and stable rather than up to the server.
            .thenComparing(Entry::name);

    /** The top {@code limit}, best first. */
    public static List<Entry> top(Collection<Entry> entries, int limit) {
        return entries.stream().sorted(ORDER).limit(Math.max(0, limit)).toList();
    }
}
