package com.andymods.murimcultivation.npc;

import com.andymods.murimcultivation.config.MurimConfig;

/**
 * How strong a wandering warrior is when it appears, and why it varies by where.
 *
 * <p><strong>A warrior's realm is fixed at spawn and never scales to the player.</strong> That is
 * the decision this whole class exists to implement: it gives the world geography. Some regions are
 * safe and some are not, and beating a Peak-realm Expert means something precisely because it was
 * never going to be levelled down to meet you. It is only a fair design because the duel rules let
 * you survive losing — without that, meeting someone far above you would just be a death sentence
 * and every player would learn to avoid the interesting encounters.
 *
 * <p>The roll reads the two biome tags the mod already ships. {@code #murimcultivation:qi_rich} and
 * {@code #qi_barren} exist for cultivation rate, and reusing them here costs no new data and says
 * something true: warriors cultivate where the Qi is good, so the bamboo groves and the deep dark
 * hold the dangerous ones and the badlands hold thugs.
 *
 * <p>Everything here is pure and takes a {@link Tuning}. No config reads, no {@code Level}, no
 * {@code RandomSource} in the core — a roll is a function of a pressure value and a number from 0 to
 * 99, which is what makes the distribution testable at all.
 */
public final class WarriorSpawns {

    private WarriorSpawns() {
    }

    // The band boundaries over a 0-99 roll, at zero pressure and at full. These are CUMULATIVE
    // rather than per-band shares, and that is deliberate: interpolating four independent shares
    // means four truncating divisions, and the one left as the remainder then wobbles by a point
    // as pressure rises. Interpolating the boundaries instead makes each one monotonic in
    // pressure on its own, so the thug share only ever falls and the master share only ever
    // rises — which are exactly the two properties the design is claiming.
    private static final int THUG_END_CALM = 60;
    private static final int THUG_END_WILD = 10;
    private static final int WANDERER_END_CALM = 90;
    private static final int WANDERER_END_WILD = 45;
    private static final int EXPERT_END_CALM = 99;
    private static final int EXPERT_END_WILD = 80;

    /**
     * How the world decides what it can throw at you.
     *
     * @param qiRichPressure pressure added by a Qi-rich biome — warriors train where the Qi is
     * @param neutralPressure pressure in an ordinary biome
     * @param qiBarrenPressure pressure in a barren one, where only thugs bother
     * @param pressurePerHundredBlocks how fast remoteness from world spawn raises the stakes
     * @param maxDistancePressure the ceiling distance alone can contribute, so there is a far edge
     *                            to the difficulty curve rather than an unbounded ramp
     * @param maxNaturalRealmTier the highest realm a wild spawn may roll
     */
    public record Tuning(int qiRichPressure,
                         int neutralPressure,
                         int qiBarrenPressure,
                         int pressurePerHundredBlocks,
                         int maxDistancePressure,
                         int maxNaturalRealmTier) {

        public static Tuning fromConfig() {
            return new Tuning(
                    MurimConfig.warriorQiRichPressure(),
                    MurimConfig.warriorNeutralPressure(),
                    MurimConfig.warriorQiBarrenPressure(),
                    MurimConfig.warriorPressurePerHundredBlocks(),
                    MurimConfig.warriorMaxDistancePressure(),
                    MurimConfig.warriorMaxNaturalRealmTier());
        }
    }

    /**
     * How dangerous this place is, 0 to 100.
     *
     * <p>Biome sets the floor and remoteness raises it. A Qi-rich biome nine thousand blocks out is
     * where the Masters are; the plains outside your first house are where the thugs are.
     */
    public static int pressure(boolean qiRich, boolean qiBarren, double distanceFromSpawn, Tuning tuning) {
        int base = qiRich ? tuning.qiRichPressure()
                : qiBarren ? tuning.qiBarrenPressure()
                : tuning.neutralPressure();

        // NaN is the one input that has to be special-cased: it would survive Math.min and then
        // cast to zero, silently collapsing the curve instead of failing loudly. An infinite
        // distance is meaningful by contrast, and reads as the far edge — it is what the ceiling
        // below is for. A world's spawn point can move, so a negative distance is possible too.
        double distance = Double.isNaN(distanceFromSpawn) ? 0.0D : Math.max(0.0D, distanceFromSpawn);
        int fromDistance = (int) Math.min(
                tuning.maxDistancePressure(),
                distance / 100.0D * tuning.pressurePerHundredBlocks());

        return Math.max(0, Math.min(100, base + fromDistance));
    }

    /**
     * Which band a warrior falls into, given the local pressure and a roll from 0 to 99.
     *
     * <p>Every roll lands in exactly one band at every pressure: the three boundaries are ordered
     * by construction and {@link WarriorTier#MASTER} takes everything above the last, so no sliver
     * of the range is ever unassigned.
     */
    public static WarriorTier bandFor(int pressure, int roll) {
        int clamped = Math.max(0, Math.min(100, pressure));
        int thugEnd = boundary(THUG_END_CALM, THUG_END_WILD, clamped);
        int wandererEnd = boundary(WANDERER_END_CALM, WANDERER_END_WILD, clamped);
        int expertEnd = boundary(EXPERT_END_CALM, EXPERT_END_WILD, clamped);

        int bounded = Math.max(0, Math.min(99, roll));
        if (bounded < thugEnd) {
            return WarriorTier.THUG;
        }
        if (bounded < wandererEnd) {
            return WarriorTier.WANDERER;
        }
        if (bounded < expertEnd) {
            return WarriorTier.EXPERT;
        }
        return WarriorTier.MASTER;
    }

    /** Linear interpolation of one cumulative boundary between calm and wild, by pressure. */
    private static int boundary(int atCalm, int atWild, int pressure) {
        return atCalm + (atWild - atCalm) * pressure / 100;
    }

    /**
     * A concrete realm tier inside a band, capped at what a wild spawn is allowed to reach.
     *
     * <p>The cap matters more than it looks. The shipped ladder runs to tier 9, whose attribute
     * grants are +80 health and +30 attack damage — a fine thing to be at the end of a long climb
     * and an absurd thing to meet while walking. Capping natural spawns at Transcendent leaves the
     * top four realms as something only a deliberately placed encounter can be.
     *
     * @param roll any non-negative number; only its remainder across the band is used
     */
    public static int realmTierFor(WarriorTier band, int roll, Tuning tuning) {
        int span = band.highestRealmTier() - band.lowestRealmTier() + 1;
        int within = band.lowestRealmTier() + Math.floorMod(roll, Math.max(1, span));
        // The cap wins over the band, so a cap set below a band's floor pulls the realm down out
        // of it. That is intended rather than a hole: the entity re-derives its label from the
        // realm it ends up with, so a capped Master is simply named as whatever it actually is.
        return Math.min(within, Math.max(1, tuning.maxNaturalRealmTier()));
    }
}
