package com.andymods.murimcultivation.sect;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Standing within a sect, from the gate to the inner council.
 *
 * <p>Rank is earned with reputation and is what gates a sect's own teachings. The thresholds
 * live here rather than per sect so that "core disciple" means a comparable thing wherever you
 * earned it; a sect's JSON tunes how quickly reputation accrues, not what the ladder is.
 */
public enum SectRank implements StringRepresentable {

    /** Not a member. */
    OUTSIDER("outsider", 0),

    /** Taken in, given chores and the most basic manual. */
    OUTER_DISCIPLE("outer_disciple", 100),

    /** Trusted with real training. */
    INNER_DISCIPLE("inner_disciple", 400),

    /** One of the sect's own, taught its signature arts. */
    CORE_DISCIPLE("core_disciple", 1200),

    /** Senior enough to teach. */
    ELDER("elder", 3000),

    /** The sect's head. */
    PATRIARCH("patriarch", 8000);

    public static final Codec<SectRank> CODEC = StringRepresentable.fromEnum(SectRank::values);

    private static final SectRank[] BY_ORDINAL = values();

    private final String id;
    private final int reputationRequired;

    SectRank(String id, int reputationRequired) {
        this.id = id;
        this.reputationRequired = reputationRequired;
    }

    public int reputationRequired() {
        return reputationRequired;
    }

    public boolean isMember() {
        return this != OUTSIDER;
    }

    /** The highest rank this much reputation earns. */
    public static SectRank forReputation(int reputation) {
        SectRank earned = OUTSIDER;
        for (SectRank rank : BY_ORDINAL) {
            if (reputation >= rank.reputationRequired()) {
                earned = rank;
            }
        }
        return earned;
    }

    /** The next rank up, or empty at the top. */
    public SectRank next() {
        return ordinal() + 1 < BY_ORDINAL.length ? BY_ORDINAL[ordinal() + 1] : null;
    }

    public String translationKey() {
        return "murimcultivation.sect_rank." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
