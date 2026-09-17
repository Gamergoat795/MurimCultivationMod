package com.andymods.murimcultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Server-authoritative tuning. Everything here is a number a server owner or modpack author
 * might reasonably want to change without a datapack; anything that defines *content* — the
 * realms themselves, techniques, quests — lives in JSON instead.
 */
public final class MurimConfig {

    public static final ModConfigSpec SPEC;
    private static final Values VALUES;

    static {
        Pair<Values, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Values::new);
        VALUES = pair.getLeft();
        SPEC = pair.getRight();
    }

    private static final class Values {

        private final ModConfigSpec.DoubleValue meditationProgressPerSecond;
        private final ModConfigSpec.DoubleValue meditationPurityPerMinute;
        private final ModConfigSpec.IntValue meditationRampSeconds;
        private final ModConfigSpec.DoubleValue meditationRampMultiplier;
        private final ModConfigSpec.DoubleValue qiPerPrimaryMeridian;
        private final ModConfigSpec.DoubleValue qiPerExtraordinaryVessel;
        private final ModConfigSpec.DoubleValue meditationMoveTolerance;
        private final ModConfigSpec.DoubleValue qiDensityRichBiome;
        private final ModConfigSpec.DoubleValue qiDensityBarrenBiome;
        private final ModConfigSpec.DoubleValue qiDensityNight;
        private final ModConfigSpec.DoubleValue qiDensityRain;
        private final ModConfigSpec.DoubleValue qiDensityThunder;
        private final ModConfigSpec.DoubleValue qiDensityAltitudeBonusPerBlock;
        private final ModConfigSpec.DoubleValue qiDensityAltitudeBonusCap;
        private final ModConfigSpec.DoubleValue qiDensityMinimum;
        private final ModConfigSpec.DoubleValue qiDensityMaximum;
        private final ModConfigSpec.DoubleValue enlightenmentChancePerSecond;
        private final ModConfigSpec.DoubleValue enlightenmentProgressSeconds;
        private final ModConfigSpec.DoubleValue enlightenmentPurityGain;
        private final ModConfigSpec.DoubleValue enlightenmentNearDeathChance;
        private final ModConfigSpec.DoubleValue nearDeathHealthFraction;
        private final ModConfigSpec.IntValue cultivationTickInterval;
        private final ModConfigSpec.BooleanValue announceBreakthroughs;

        private Values(ModConfigSpec.Builder builder) {
            builder.comment("Cultivation rates").push("cultivation");

            meditationProgressPerSecond = builder
                    .comment("Base cultivation progress gained per second of sustained meditation,",
                            "before location Qi density and foundation purity are applied.")
                    .defineInRange("meditationProgressPerSecond", 1.0D, 0.0D, 10000.0D);

            meditationPurityPerMinute = builder
                    .comment("Foundation purity gained per minute of patient meditation.",
                            "Purity is what makes breakthroughs survivable, so this is the",
                            "main reward for cultivating slowly instead of chasing shortcuts.")
                    .defineInRange("meditationPurityPerMinute", 0.5D, 0.0D, 100.0D);

            meditationRampSeconds = builder
                    .comment("Seconds of unbroken meditation before the gain rate reaches its maximum.")
                    .defineInRange("meditationRampSeconds", 60, 1, 6000);

            meditationRampMultiplier = builder
                    .comment("Gain multiplier at full ramp. A long uninterrupted session should be",
                            "worth more than the same time broken into fragments.")
                    .defineInRange("meditationRampMultiplier", 2.5D, 1.0D, 100.0D);

            meditationMoveTolerance = builder
                    .comment("How far, in blocks, a meditating player may drift before the session",
                            "is interrupted. Meditation never immobilises the player; moving away",
                            "simply ends it. A small tolerance stops server-side position",
                            "corrections from cancelling a legitimate sit-down.")
                    .defineInRange("meditationMoveTolerance", 0.75D, 0.05D, 16.0D);

            builder.pop();
            builder.comment("Ambient Qi density: where you cultivate matters").push("qi_density");

            qiDensityRichBiome = builder
                    .comment("Multiplier in biomes tagged #murimcultivation:qi_rich.")
                    .defineInRange("richBiome", 1.5D, 0.0D, 100.0D);

            qiDensityBarrenBiome = builder
                    .comment("Multiplier in biomes tagged #murimcultivation:qi_barren.")
                    .defineInRange("barrenBiome", 0.6D, 0.0D, 100.0D);

            qiDensityNight = builder
                    .comment("Multiplier at night, the traditional hour for cultivation.")
                    .defineInRange("night", 1.2D, 0.0D, 100.0D);

            qiDensityRain = builder
                    .comment("Multiplier while it is raining.")
                    .defineInRange("rain", 1.1D, 0.0D, 100.0D);

            qiDensityThunder = builder
                    .comment("Multiplier during a thunderstorm, when the heavens are watching.")
                    .defineInRange("thunder", 1.35D, 0.0D, 100.0D);

            qiDensityAltitudeBonusPerBlock = builder
                    .comment("Bonus per block of distance from sea level, in either direction.",
                            "Mountain peaks and deep caves are both good places to cultivate.")
                    .defineInRange("altitudeBonusPerBlock", 0.0035D, 0.0D, 1.0D);

            qiDensityAltitudeBonusCap = builder
                    .comment("Maximum total bonus from altitude, so extremes do not run away.")
                    .defineInRange("altitudeBonusCap", 0.45D, 0.0D, 100.0D);

            qiDensityMinimum = builder
                    .comment("Floor on the combined multiplier. A poor location should be slow,",
                            "not useless.")
                    .defineInRange("minimum", 0.4D, 0.01D, 100.0D);

            qiDensityMaximum = builder
                    .comment("Ceiling on the combined multiplier, so a perfect spot is a real",
                            "find rather than a different game.")
                    .defineInRange("maximum", 3.0D, 0.01D, 1000.0D);

            builder.pop();
            builder.comment("Sudden insight").push("enlightenment");

            enlightenmentChancePerSecond = builder
                    .comment("Base chance per second of meditation of a moment of insight,",
                            "before purity and location are applied.")
                    .defineInRange("chancePerSecond", 0.004D, 0.0D, 1.0D);

            enlightenmentProgressSeconds = builder
                    .comment("Insight is worth this many seconds of meditation. Expressed in time",
                            "rather than a flat number so it stays meaningful at every realm.")
                    .defineInRange("progressSeconds", 45.0D, 0.0D, 100000.0D);

            enlightenmentPurityGain = builder
                    .comment("Foundation purity granted by a moment of insight.")
                    .defineInRange("purityGain", 1.5D, 0.0D, 100.0D);

            enlightenmentNearDeathChance = builder
                    .comment("Chance of insight when a blow leaves the player near death.",
                            "The classic breakthrough-mid-fight moment.")
                    .defineInRange("nearDeathChance", 0.15D, 0.0D, 1.0D);

            nearDeathHealthFraction = builder
                    .comment("Health fraction at or below which a survived hit counts as",
                            "a brush with death.")
                    .defineInRange("nearDeathHealthFraction", 0.2D, 0.0D, 1.0D);

            builder.pop();
            builder.comment("Qi network").push("meridians");

            qiPerPrimaryMeridian = builder
                    .comment("Extra maximum Qi granted by each of the twelve open primary meridians.")
                    .defineInRange("qiPerPrimaryMeridian", 10.0D, 0.0D, 10000.0D);

            qiPerExtraordinaryVessel = builder
                    .comment("Extra maximum Qi granted by each of the eight open extraordinary vessels.")
                    .defineInRange("qiPerExtraordinaryVessel", 40.0D, 0.0D, 10000.0D);

            builder.pop();
            builder.comment("Performance and presentation").push("general");

            cultivationTickInterval = builder
                    .comment("How often, in ticks, each player's cultivation is evaluated.",
                            "20 is once per second. Higher values are cheaper and coarser.")
                    .defineInRange("cultivationTickInterval", 20, 1, 200);

            announceBreakthroughs = builder
                    .comment("Announce realm breakthroughs to everyone on the server.",
                            "The whole Murim hearing your name is half the fun.")
                    .define("announceBreakthroughs", true);

            builder.pop();
        }
    }

    public static double meditationProgressPerSecond() {
        return VALUES.meditationProgressPerSecond.get();
    }

    public static double meditationPurityPerMinute() {
        return VALUES.meditationPurityPerMinute.get();
    }

    public static int meditationRampSeconds() {
        return VALUES.meditationRampSeconds.get();
    }

    public static double meditationRampMultiplier() {
        return VALUES.meditationRampMultiplier.get();
    }

    public static double qiPerPrimaryMeridian() {
        return VALUES.qiPerPrimaryMeridian.get();
    }

    public static double qiPerExtraordinaryVessel() {
        return VALUES.qiPerExtraordinaryVessel.get();
    }

    public static double meditationMoveTolerance() {
        return VALUES.meditationMoveTolerance.get();
    }

    public static double qiDensityRichBiome() {
        return VALUES.qiDensityRichBiome.get();
    }

    public static double qiDensityBarrenBiome() {
        return VALUES.qiDensityBarrenBiome.get();
    }

    public static double qiDensityNight() {
        return VALUES.qiDensityNight.get();
    }

    public static double qiDensityRain() {
        return VALUES.qiDensityRain.get();
    }

    public static double qiDensityThunder() {
        return VALUES.qiDensityThunder.get();
    }

    public static double qiDensityAltitudeBonusPerBlock() {
        return VALUES.qiDensityAltitudeBonusPerBlock.get();
    }

    public static double qiDensityAltitudeBonusCap() {
        return VALUES.qiDensityAltitudeBonusCap.get();
    }

    public static double qiDensityMinimum() {
        return VALUES.qiDensityMinimum.get();
    }

    public static double qiDensityMaximum() {
        return VALUES.qiDensityMaximum.get();
    }

    public static double enlightenmentChancePerSecond() {
        return VALUES.enlightenmentChancePerSecond.get();
    }

    public static double enlightenmentProgressSeconds() {
        return VALUES.enlightenmentProgressSeconds.get();
    }

    public static double enlightenmentPurityGain() {
        return VALUES.enlightenmentPurityGain.get();
    }

    public static double enlightenmentNearDeathChance() {
        return VALUES.enlightenmentNearDeathChance.get();
    }

    public static double nearDeathHealthFraction() {
        return VALUES.nearDeathHealthFraction.get();
    }

    public static int cultivationTickInterval() {
        return VALUES.cultivationTickInterval.get();
    }

    public static boolean announceBreakthroughs() {
        return VALUES.announceBreakthroughs.get();
    }

    private MurimConfig() {
    }
}
