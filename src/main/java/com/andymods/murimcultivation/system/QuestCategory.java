package com.andymods.murimcultivation.system;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Whether a quest is part of the through-line or one of the repeatable set. */
public enum QuestCategory implements StringRepresentable {

    /** A one-time quest in the progression chain. Never resets. */
    STORY("story"),

    /**
     * A repeatable quest that clears on the reset cadence.
     *
     * <p>Rewards are deliberately modest next to story quests: dailies reset each in-game day
     * by configuration, and a Minecraft day is twenty minutes, so generous dailies would be
     * the fastest route to every realm rather than a side activity.
     */
    DAILY("daily");

    public static final Codec<QuestCategory> CODEC = StringRepresentable.fromEnum(QuestCategory::values);

    private final String id;

    QuestCategory(String id) {
        this.id = id;
    }

    public boolean repeatable() {
        return this == DAILY;
    }

    public String translationKey() {
        return "murimcultivation.quest_category." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
