package com.andymods.murimcultivation.system;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The kinds of thing the System can ask a cultivator to do.
 *
 * <p>Kinds are code and their numbers are data, the same split {@code TechniqueBehaviours}
 * uses: a datapack composes quests from these, but cannot invent a new kind of objective
 * without Java. {@link #needsTarget()} says which kinds require a {@code target} id, which is
 * what lets a malformed quest be rejected at load rather than sitting inert until a player
 * picks it up.
 */
public enum ObjectiveKind implements StringRepresentable {

    /** Meditate for a total number of seconds. */
    MEDITATE_SECONDS("meditate_seconds", false),

    /** Reach a specific realm. The target is a realm id. */
    REACH_REALM("reach_realm", true),

    /** Have a number of meridians open at once. */
    OPEN_MERIDIANS("open_meridians", false),

    /** Learn a specific art. The target is a technique id. */
    LEARN_TECHNIQUE("learn_technique", true),

    /** Cast a specific art a number of times. The target is a technique id. */
    CAST_TECHNIQUE("cast_technique", true),

    /** Reach a mastery level in a specific art. The target is a technique id. */
    MASTER_TECHNIQUE("master_technique", true),

    /** Kill a number of living things. */
    KILL_ENTITIES("kill_entities", false),

    /** Reach a foundation purity. */
    REACH_PURITY("reach_purity", false),

    /** Survive a Qi Deviation through to its end. */
    SURVIVE_DEVIATION("survive_deviation", false);

    public static final Codec<ObjectiveKind> CODEC = StringRepresentable.fromEnum(ObjectiveKind::values);

    private final String id;
    private final boolean needsTarget;

    ObjectiveKind(String id, boolean needsTarget) {
        this.id = id;
        this.needsTarget = needsTarget;
    }

    /** Whether this kind is meaningless without a {@code target} id. */
    public boolean needsTarget() {
        return needsTarget;
    }

    /**
     * Whether progress is a running tally that only goes up (kills, casts, seconds), as
     * opposed to a threshold re-read from current state (realm, purity, open meridians).
     *
     * <p>The distinction matters: a tally must be stored, while a threshold must not be,
     * or a player who loses a meridian would keep credit for having opened it.
     */
    public boolean isCumulative() {
        return switch (this) {
            case MEDITATE_SECONDS, CAST_TECHNIQUE, KILL_ENTITIES, SURVIVE_DEVIATION -> true;
            case REACH_REALM, OPEN_MERIDIANS, LEARN_TECHNIQUE, MASTER_TECHNIQUE, REACH_PURITY -> false;
        };
    }

    public String translationKey() {
        return "murimcultivation.objective." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
