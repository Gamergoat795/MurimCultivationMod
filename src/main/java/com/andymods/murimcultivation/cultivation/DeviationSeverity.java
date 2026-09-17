package com.andymods.murimcultivation.cultivation;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * How badly a botched breakthrough scrambled the cultivator's Qi. This is the cost that
 * makes a breakthrough a decision rather than a button: rushing one with a poor foundation
 * can set you back further than the patience would have cost.
 *
 * <p>M2 applies the mechanical consequences; the state itself lives here so that saves
 * written before then stay forward-compatible.
 */
public enum DeviationSeverity implements StringRepresentable {
    /** No deviation. The healthy default. */
    NONE("none", 0, 0.0D, 0),

    /** Qi snags in a channel. Unpleasant, clears on its own, costs a little progress. */
    MINOR_BLOCKAGE("minor_blockage", 1, 0.10D, 2 * 60 * 20),

    /** Qi runs backwards. Real debuffs, real progress loss, wants a pill to clear. */
    REVERSE_FLOW("reverse_flow", 2, 0.30D, 6 * 60 * 20),

    /** A channel tears. A meridian slams shut and a large chunk of progress is gone. */
    SHATTERED_MERIDIAN("shattered_meridian", 3, 0.60D, 15 * 60 * 20);

    public static final Codec<DeviationSeverity> CODEC = StringRepresentable.fromEnum(DeviationSeverity::values);

    private final String id;
    private final int rank;
    private final double progressLossFraction;
    private final int durationTicks;

    DeviationSeverity(String id, int rank, double progressLossFraction, int durationTicks) {
        this.id = id;
        this.rank = rank;
        this.progressLossFraction = progressLossFraction;
        this.durationTicks = durationTicks;
    }

    public int rank() {
        return rank;
    }

    /** Fraction of accumulated progress destroyed when this deviation strikes. */
    public double progressLossFraction() {
        return progressLossFraction;
    }

    /** How long it lingers if left untreated, in ticks. */
    public int durationTicks() {
        return durationTicks;
    }

    /** Whether a meridian is forced shut. Only the worst tier does this. */
    public boolean closesMeridian() {
        return this == SHATTERED_MERIDIAN;
    }

    public boolean isActive() {
        return this != NONE;
    }

    public String translationKey() {
        return "murimcultivation.deviation." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
