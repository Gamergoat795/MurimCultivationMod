package com.andymods.murimcultivation.system;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What the System hands over for a completed quest. Kinds are code; their numbers are data. */
public enum RewardKind implements StringRepresentable {

    /** Cultivation progress toward the next realm. */
    PROGRESS("progress", false),

    /** Foundation purity. */
    PURITY("purity", false),

    /** Stat points to spend. */
    STAT_POINTS("stat_points", false),

    /** An item stack. The target is an item id. */
    ITEM("item", true),

    /** A martial art, learned outright. The target is a technique id. */
    TECHNIQUE("technique", true),

    /** A title. The target is a title id. */
    TITLE("title", true),

    /** Standing with a sect. The target is a sect id. */
    SECT_REPUTATION("sect_reputation", true);

    public static final Codec<RewardKind> CODEC = StringRepresentable.fromEnum(RewardKind::values);

    private final String id;
    private final boolean needsTarget;

    RewardKind(String id, boolean needsTarget) {
        this.id = id;
        this.needsTarget = needsTarget;
    }

    public boolean needsTarget() {
        return needsTarget;
    }

    public String translationKey() {
        return "murimcultivation.reward." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
