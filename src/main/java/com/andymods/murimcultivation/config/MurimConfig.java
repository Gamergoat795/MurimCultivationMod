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

    public static int cultivationTickInterval() {
        return VALUES.cultivationTickInterval.get();
    }

    public static boolean announceBreakthroughs() {
        return VALUES.announceBreakthroughs.get();
    }

    private MurimConfig() {
    }
}
