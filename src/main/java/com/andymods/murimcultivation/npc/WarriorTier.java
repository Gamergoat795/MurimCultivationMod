package com.andymods.murimcultivation.npc;

import net.minecraft.util.StringRepresentable;
import com.mojang.serialization.Codec;

/**
 * What kind of fighter a wandering warrior is.
 *
 * <p>A tier is a <em>label over a band of realms</em>, not a second source of numbers. The realm a
 * warrior rolls is what decides its health and damage, straight from that realm's own
 * {@code attributes} list in the datapack, so the curve a player fights is the same curve they
 * climb. If the tier carried its own statistics they would drift from the ladder the moment
 * anyone rebalanced either.
 */
public enum WarriorTier implements StringRepresentable {

    /** Third-Rate. Common, weak, and as likely as not dishonourable themselves. */
    THUG("thug", 1, 1),

    /** Second- to First-Rate. The standard duel partner, and the one you meet most. */
    WANDERER("wanderer", 2, 3),

    /** Peak. Uncommon and genuinely dangerous to anyone who has not prepared. */
    EXPERT("expert", 4, 4),

    /** Transcendent. Rare, and refuses challengers it considers beneath it. */
    MASTER("master", 5, 5);

    public static final Codec<WarriorTier> CODEC = StringRepresentable.fromEnum(WarriorTier::values);

    private final String id;
    private final int lowestRealmTier;
    private final int highestRealmTier;

    WarriorTier(String id, int lowestRealmTier, int highestRealmTier) {
        this.id = id;
        this.lowestRealmTier = lowestRealmTier;
        this.highestRealmTier = highestRealmTier;
    }

    public int lowestRealmTier() {
        return lowestRealmTier;
    }

    public int highestRealmTier() {
        return highestRealmTier;
    }

    /** Whether a realm tier falls in this band. */
    public boolean covers(int realmTier) {
        return realmTier >= lowestRealmTier && realmTier <= highestRealmTier;
    }

    /** The band a realm tier belongs to, saturating at {@link #MASTER} for anything higher. */
    public static WarriorTier forRealmTier(int realmTier) {
        for (WarriorTier tier : values()) {
            if (tier.covers(realmTier)) {
                return tier;
            }
        }
        // Above Transcendent there is no further label. Deliberately saturating rather than
        // throwing: a datapack is free to add realms above the nine shipped here, and a warrior
        // placed at one of them should still be describable.
        return realmTier < THUG.lowestRealmTier ? THUG : MASTER;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String translationKey() {
        return "murimcultivation.warrior_tier." + id;
    }
}
