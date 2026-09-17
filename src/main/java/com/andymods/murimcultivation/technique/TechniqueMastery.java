package com.andymods.murimcultivation.technique;

import com.andymods.murimcultivation.config.MurimConfig;

/**
 * What practice does to a martial art.
 *
 * <p>Mastery runs 0–100 and is earned by use, with diminishing returns: the first uses of a
 * new art teach a lot, the last few percent are a grind. It makes the art cheaper in Qi and
 * quicker to come round again, while how hard it *hits* scales per technique via
 * {@link TechniquePower#damagePerMastery()} — how steeply an art rewards practice is part of
 * that art's identity, not a global constant.
 *
 * <p>Every function here is pure and takes its tunables explicitly, with thin config-reading
 * overloads for production call sites. Reading {@code MurimConfig} inside a formula makes it
 * impossible to unit test, because a {@code ModConfigSpec} value throws until a world has
 * loaded it — and the mastery economy is exactly the kind of curve that needs tests.
 */
public final class TechniqueMastery {

    public static final int MIN = 0;
    public static final int MAX = 100;

    private TechniqueMastery() {
    }

    /**
     * The mastery economy's global knobs.
     *
     * @param gainFalloff            how sharply gains diminish as mastery rises; 1.0 is linear
     * @param minGainFraction        floor on the diminished gain, so 100 stays reachable
     * @param qiCostReductionAtFull  fraction of Qi cost removed at full mastery
     * @param cooldownReductionAtFull fraction of cooldown removed at full mastery
     */
    public record Tuning(double gainFalloff,
                         double minGainFraction,
                         double qiCostReductionAtFull,
                         double cooldownReductionAtFull) {

        public static Tuning fromConfig() {
            return new Tuning(
                    MurimConfig.masteryGainFalloff(),
                    MurimConfig.masteryMinGainFraction(),
                    MurimConfig.masteryQiCostReduction(),
                    MurimConfig.masteryCooldownReduction());
        }
    }

    public static int clamp(int mastery) {
        return Math.max(MIN, Math.min(MAX, mastery));
    }

    /**
     * Mastery gained from one use.
     *
     * <p>Diminishes as mastery rises, but never to nothing: the floor guarantees full mastery
     * is actually reachable rather than an asymptote a player can never touch.
     */
    public static double gainFrom(int currentMastery, double baseGain, Tuning tuning) {
        if (baseGain <= 0.0D || currentMastery >= MAX) {
            return 0.0D;
        }
        double remaining = (MAX - clamp(currentMastery)) / (double) MAX;
        double falloff = Math.pow(remaining, tuning.gainFalloff());
        return baseGain * Math.max(tuning.minGainFraction(), falloff);
    }

    /** Qi cost after mastery. Practice makes an art more economical, never free. */
    public static double qiCost(double baseCost, int mastery, Tuning tuning) {
        double reduction = (clamp(mastery) / (double) MAX) * tuning.qiCostReductionAtFull();
        return baseCost * Math.max(0.0D, 1.0D - reduction);
    }

    /** Cooldown after mastery, in ticks. Rounds up so a cooldown never vanishes entirely. */
    public static int cooldownTicks(int baseCooldown, int mastery, Tuning tuning) {
        if (baseCooldown <= 0) {
            return 0;
        }
        double reduction = (clamp(mastery) / (double) MAX) * tuning.cooldownReductionAtFull();
        return Math.max(1, (int) Math.ceil(baseCooldown * Math.max(0.0D, 1.0D - reduction)));
    }

    /** Damage at a given mastery, from the technique's own scaling. */
    public static double damage(TechniquePower power, int mastery) {
        return power.damageAt(clamp(mastery));
    }

    /** A 0–4 star rating, for compact display next to an art's name. */
    public static int stars(int mastery) {
        return clamp(mastery) / 25;
    }

    // --- Config-reading conveniences --------------------------------------------------

    public static double gainFrom(int currentMastery, double baseGain) {
        return gainFrom(currentMastery, baseGain, Tuning.fromConfig());
    }

    public static double qiCost(double baseCost, int mastery) {
        return qiCost(baseCost, mastery, Tuning.fromConfig());
    }

    public static int cooldownTicks(int baseCooldown, int mastery) {
        return cooldownTicks(baseCooldown, mastery, Tuning.fromConfig());
    }
}
