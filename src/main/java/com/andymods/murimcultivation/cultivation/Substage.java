package com.andymods.murimcultivation.cultivation;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The four steps within a single realm. A cultivator climbs Early -> Mid -> Late -> Peak
 * inside their current realm before they are eligible to break through to the next one,
 * which turns nine realms into thirty-six felt steps of progress.
 */
public enum Substage implements StringRepresentable {
    EARLY("early", 0),
    MID("mid", 1),
    LATE("late", 2),
    PEAK("peak", 3);

    public static final Codec<Substage> CODEC = StringRepresentable.fromEnum(Substage::values);

    private static final Substage[] BY_ORDINAL = values();

    private final String id;
    private final int index;

    Substage(String id, int index) {
        this.id = id;
        this.index = index;
    }

    public int index() {
        return index;
    }

    /** The translation key for this substage's short label, e.g. {@code murimcultivation.substage.peak}. */
    public String translationKey() {
        return "murimcultivation.substage." + id;
    }

    public boolean isLast() {
        return this == PEAK;
    }

    /** The next substage, or {@code null} at {@link #PEAK} — where the next step is a breakthrough instead. */
    public Substage next() {
        return isLast() ? null : BY_ORDINAL[index + 1];
    }

    public static Substage byIndex(int index) {
        if (index < 0) {
            return EARLY;
        }
        return index >= BY_ORDINAL.length ? PEAK : BY_ORDINAL[index];
    }

    public static int count() {
        return BY_ORDINAL.length;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
