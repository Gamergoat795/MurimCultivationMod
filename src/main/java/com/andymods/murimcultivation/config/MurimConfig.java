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
        private final ModConfigSpec.IntValue focusPromptMinSeconds;
        private final ModConfigSpec.IntValue focusPromptMaxSeconds;
        private final ModConfigSpec.DoubleValue focusSweepSeconds;
        private final ModConfigSpec.DoubleValue focusWindowWidth;
        private final ModConfigSpec.IntValue focusMinReactionTicks;
        private final ModConfigSpec.DoubleValue focusLatencyTolerance;
        private final ModConfigSpec.DoubleValue focusPerfectFraction;
        private final ModConfigSpec.DoubleValue focusHitGain;
        private final ModConfigSpec.DoubleValue focusMissPenalty;
        private final ModConfigSpec.DoubleValue focusPerfectBonusSeconds;
        private final ModConfigSpec.IntValue focusBreakthroughSweeps;
        private final ModConfigSpec.DoubleValue focusBreakthroughTighten;
        private final ModConfigSpec.DoubleValue focusBreakthroughBonus;
        private final ModConfigSpec.DoubleValue qiDensityRichBiome;
        private final ModConfigSpec.DoubleValue qiDensityBarrenBiome;
        private final ModConfigSpec.DoubleValue qiDensityNight;
        private final ModConfigSpec.DoubleValue qiDensityRain;
        private final ModConfigSpec.DoubleValue qiDensityThunder;
        private final ModConfigSpec.DoubleValue qiDensityAltitudeBonusPerBlock;
        private final ModConfigSpec.DoubleValue qiDensityAltitudeBonusCap;
        private final ModConfigSpec.DoubleValue spiritVeinBonus;
        private final ModConfigSpec.DoubleValue qiDensityMinimum;
        private final ModConfigSpec.DoubleValue qiDensityMaximum;
        private final ModConfigSpec.DoubleValue enlightenmentChancePerSecond;
        private final ModConfigSpec.DoubleValue enlightenmentProgressSeconds;
        private final ModConfigSpec.DoubleValue enlightenmentPurityGain;
        private final ModConfigSpec.DoubleValue enlightenmentNearDeathChance;
        private final ModConfigSpec.DoubleValue nearDeathHealthFraction;
        private final ModConfigSpec.DoubleValue breakthroughPurityWeight;
        private final ModConfigSpec.DoubleValue breakthroughMeridianWeight;
        private final ModConfigSpec.DoubleValue breakthroughDensityWeight;
        private final ModConfigSpec.DoubleValue breakthroughMinChance;
        private final ModConfigSpec.DoubleValue breakthroughMaxChance;
        private final ModConfigSpec.DoubleValue breakthroughMinorFailureChance;
        private final ModConfigSpec.DoubleValue breakthroughSevereFailureChance;
        private final ModConfigSpec.BooleanValue breakthroughLightning;
        private final ModConfigSpec.DoubleValue meridianBaseCostInSubstages;
        private final ModConfigSpec.DoubleValue meridianCostGrowthPerOpenNode;
        private final ModConfigSpec.DoubleValue meridianPrimaryDeviationChance;
        private final ModConfigSpec.DoubleValue meridianExtraordinaryDeviationChance;
        private final ModConfigSpec.DoubleValue meridianPurityProtection;
        private final ModConfigSpec.DoubleValue meridianMinimumPurity;
        private final ModConfigSpec.DoubleValue masteryGainFalloff;
        private final ModConfigSpec.DoubleValue masteryMinGainFraction;
        private final ModConfigSpec.DoubleValue masteryQiCostReduction;
        private final ModConfigSpec.DoubleValue masteryCooldownReduction;
        private final ModConfigSpec.BooleanValue techniquesHarmPlayers;
        private final ModConfigSpec.DoubleValue statPointPower;
        private final ModConfigSpec.IntValue statPointsPerSubstage;
        private final ModConfigSpec.IntValue statPointsPerRealm;
        private final ModConfigSpec.IntValue dailyResetIntervalDays;
        private final ModConfigSpec.IntValue standingHonourPerHonourableWin;
        private final ModConfigSpec.IntValue standingInfamyForgivenPerWin;
        private final ModConfigSpec.IntValue standingInfamyPerAmbush;
        private final ModConfigSpec.IntValue standingHonourLostPerAmbush;
        private final ModConfigSpec.IntValue standingInfamyPerKillingYielded;
        private final ModConfigSpec.IntValue standingHonourLostPerKillingYielded;
        private final ModConfigSpec.IntValue standingOrthodoxHonourRequired;
        private final ModConfigSpec.IntValue standingOrthodoxInfamyLimit;
        private final ModConfigSpec.IntValue standingDemonicInfamyRequired;
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
            builder.comment("Attention: the breath-rhythm prompt that stops meditation being",
                            "something you can walk away from. Raising the interval range makes",
                            "prompts rarer and gentler; a server that does not want them at all",
                            "can set focusMissPenalty to 0, which leaves focus pinned at full and",
                            "restores the pre-prompt behaviour exactly.")
                    .push("focus");

            focusPromptMinSeconds = builder
                    .comment("Shortest gap between breath-rhythm prompts, in seconds. The actual",
                            "gap is rolled between this and the maximum so the rhythm cannot be",
                            "learned as a fixed beat and answered without looking.")
                    .defineInRange("promptMinSeconds", 18, 1, 3600);

            focusPromptMaxSeconds = builder
                    .comment("Longest gap between breath-rhythm prompts, in seconds.")
                    .defineInRange("promptMaxSeconds", 45, 1, 3600);

            focusSweepSeconds = builder
                    .comment("How long the marker takes to cross the bar. Shorter is harder.")
                    .defineInRange("sweepSeconds", 2.5D, 0.5D, 30.0D);

            focusWindowWidth = builder
                    .comment("Width of the window to hit, as a fraction of the sweep.")
                    .defineInRange("windowWidth", 0.22D, 0.02D, 1.0D);

            focusMinReactionTicks = builder
                    .comment("Answers faster than this are rejected as inhuman. Three ticks is",
                            "150ms, comfortably below a real reaction time but above zero, so a",
                            "held key or a macro firing on the packet earns nothing.")
                    .defineInRange("minReactionTicks", 3, 0, 100);

            focusLatencyTolerance = builder
                    .comment("How far the client's reported marker position may differ from the",
                            "server's own measurement, as a fraction of the sweep, before the",
                            "answer is rejected. This is what stops a client claiming a perfect",
                            "hit every time. Raise it if honest players on bad connections are",
                            "being rejected; lower it to tighten the check.")
                    .defineInRange("latencyTolerance", 0.18D, 0.01D, 1.0D);

            focusPerfectFraction = builder
                    .comment("The middle share of the window that counts as a perfect answer.")
                    .defineInRange("perfectFraction", 0.34D, 0.0D, 1.0D);

            focusHitGain = builder
                    .comment("Focus restored by a hit. Focus runs 0 to 1 and multiplies into",
                            "meditation progress, purity and the odds of sudden insight.")
                    .defineInRange("hitGain", 0.5D, 0.0D, 1.0D);

            focusMissPenalty = builder
                    .comment("Focus lost by a missed prompt. At the default, three consecutive",
                            "misses reach zero and an absent player gains nothing at all. Nothing",
                            "is ever subtracted from earned progress — missing only stops you",
                            "gaining. Set to 0 to disable the mechanic entirely.")
                    .defineInRange("missPenalty", 0.34D, 0.0D, 1.0D);

            focusPerfectBonusSeconds = builder
                    .comment("A perfect answer is worth this many extra seconds of meditation,",
                            "so attention is rewarded rather than merely not punished.")
                    .defineInRange("perfectBonusSeconds", 4.0D, 0.0D, 1000.0D);

            focusBreakthroughSweeps = builder
                    .comment("How many circulation sweeps a breakthrough asks for. Each is tighter",
                            "than the last. Set to 1 for a single window.")
                    .defineInRange("breakthroughSweeps", 3, 1, 10);

            focusBreakthroughTighten = builder
                    .comment("How much narrower each successive breakthrough sweep is, as a",
                            "fraction of the base window width. At the default, a three-sweep",
                            "attempt runs 100%, 75% then 50% of the normal window.")
                    .defineInRange("breakthroughTighten", 0.25D, 0.0D, 0.9D);

            focusBreakthroughBonus = builder
                    .comment("How much a flawless circulation adds to breakthrough odds. Still",
                            "bounded by the breakthrough minChance and maxChance clamps, so this",
                            "cannot make a hopeless attempt safe — it rewards preparation rather",
                            "than replacing it.")
                    .defineInRange("breakthroughBonus", 0.15D, 0.0D, 1.0D);

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

            spiritVeinBonus = builder
                    .comment("Bonus per spirit vein nearby, before diminishing returns.",
                            "Four veins are worth roughly twice one, not four times.")
                    .defineInRange("spiritVeinBonus", 0.25D, 0.0D, 100.0D);

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

            meridianBaseCostInSubstages = builder
                    .comment("Base cost to open a meridian, expressed as a multiple of the current",
                            "realm's progress-per-substage. Denominated this way so the cost stays",
                            "proportionate as the player climbs.")
                    .defineInRange("baseCostInSubstages", 0.75D, 0.0D, 1000.0D);

            meridianCostGrowthPerOpenNode = builder
                    .comment("Each already-open node makes the next this much more expensive.")
                    .defineInRange("costGrowthPerOpenNode", 0.12D, 0.0D, 100.0D);

            meridianPrimaryDeviationChance = builder
                    .comment("Chance that forcing a primary meridian open goes wrong, before",
                            "foundation purity is taken into account.")
                    .defineInRange("primaryDeviationChance", 0.12D, 0.0D, 1.0D);

            meridianExtraordinaryDeviationChance = builder
                    .comment("Chance that forcing an extraordinary vessel open goes wrong,",
                            "before purity. Vessels are the hard part of the network.")
                    .defineInRange("extraordinaryDeviationChance", 0.30D, 0.0D, 1.0D);

            meridianPurityProtection = builder
                    .comment("How much of the deviation risk a perfect foundation removes.",
                            "1.0 means 100 purity is completely safe; 0.8 leaves a residual risk.")
                    .defineInRange("purityProtection", 0.85D, 0.0D, 1.0D);

            meridianMinimumPurity = builder
                    .comment("Foundation purity required before attempting to open any node.")
                    .defineInRange("minimumPurity", 25.0D, 0.0D, 100.0D);

            builder.pop();
            builder.comment("Breakthrough odds and consequences").push("breakthrough");

            breakthroughPurityWeight = builder
                    .comment("How much purity above the target realm's floor improves the odds,",
                            "at full headroom. The main reward for patience.")
                    .defineInRange("purityWeight", 0.35D, 0.0D, 10.0D);

            breakthroughMeridianWeight = builder
                    .comment("How much opening meridians beyond the requirement improves the odds,",
                            "if every remaining node is open.")
                    .defineInRange("meridianWeight", 0.20D, 0.0D, 10.0D);

            breakthroughDensityWeight = builder
                    .comment("How much the ambient Qi of the attempt location shifts the odds.",
                            "Applied to the density multiplier's distance from 1.0, so a spirit",
                            "vein helps and a barren waste hurts.")
                    .defineInRange("densityWeight", 0.15D, 0.0D, 10.0D);

            breakthroughMinChance = builder
                    .comment("Floor on the success chance. A desperate attempt is never hopeless.")
                    .defineInRange("minChance", 0.05D, 0.0D, 1.0D);

            breakthroughMaxChance = builder
                    .comment("Ceiling on the success chance. A breakthrough is never a formality.")
                    .defineInRange("maxChance", 0.95D, 0.0D, 1.0D);

            breakthroughMinorFailureChance = builder
                    .comment("Failing an attempt with at least this chance causes only a Minor",
                            "Blockage: bad luck on a well-prepared attempt should not be ruinous.")
                    .defineInRange("minorFailureChance", 0.60D, 0.0D, 1.0D);

            breakthroughSevereFailureChance = builder
                    .comment("Failing an attempt with at least this chance causes Reverse Flow.",
                            "Below it, the attempt was a gamble and shatters a meridian.")
                    .defineInRange("severeFailureChance", 0.30D, 0.0D, 1.0D);

            breakthroughLightning = builder
                    .comment("Strike visual-only lightning on a successful breakthrough.",
                            "Purely cosmetic: it cannot hurt the player or set anything alight.")
                    .define("lightning", true);

            builder.pop();
            builder.comment("Martial arts and mastery").push("techniques");

            masteryGainFalloff = builder
                    .comment("How sharply mastery gains diminish as mastery rises.",
                            "1.0 is linear; higher values make the last stretch a longer grind.")
                    .defineInRange("masteryGainFalloff", 1.5D, 0.0D, 10.0D);

            masteryMinGainFraction = builder
                    .comment("Floor on the diminished gain, as a fraction of the base gain.",
                            "Without a floor, full mastery becomes an asymptote a player can",
                            "approach but never actually reach.")
                    .defineInRange("masteryMinGainFraction", 0.12D, 0.0D, 1.0D);

            masteryQiCostReduction = builder
                    .comment("Fraction of a technique's Qi cost removed at full mastery.")
                    .defineInRange("masteryQiCostReduction", 0.40D, 0.0D, 1.0D);

            masteryCooldownReduction = builder
                    .comment("Fraction of a technique's cooldown removed at full mastery.")
                    .defineInRange("masteryCooldownReduction", 0.35D, 0.0D, 1.0D);

            techniquesHarmPlayers = builder
                    .comment("Whether offensive techniques can damage other players.",
                            "Turn off for servers that want cultivation as progression only.")
                    .define("techniquesHarmPlayers", true);

            builder.pop();
            builder.comment("The System: stat points and quests").push("system");

            statPointPower = builder
                    .comment("Global multiplier on what one stat point is worth.",
                            "Scales all four stats at once without touching their relative value.")
                    .defineInRange("statPointPower", 1.0D, 0.0D, 100.0D);

            statPointsPerSubstage = builder
                    .comment("Stat points granted for each substage cleared.")
                    .defineInRange("statPointsPerSubstage", 1, 0, 1000);

            statPointsPerRealm = builder
                    .comment("Extra stat points granted for a realm breakthrough, on top of",
                            "the substage award.")
                    .defineInRange("statPointsPerRealm", 3, 0, 1000);

            dailyResetIntervalDays = builder
                    .comment("In-game days between daily quest resets. 1 means every dawn.",
                            "A Minecraft day is twenty minutes and sleeping skips the night, so at",
                            "1 a player can clear roughly three sets an hour; raise this if that",
                            "makes dailies the fastest route through the realms on your server.")
                    .defineInRange("dailyResetIntervalDays", 1, 1, 1000);

            builder.pop();
            builder.comment("Standing in the murim: honour and infamy, both 0-100.",
                            "These move by how you fight rather than whether you win. Losing a duel",
                            "you challenged for honestly costs nothing at all, by design — honour",
                            "tracks conduct, and a system where it tracked outcome would just be a",
                            "second name for combat power.",
                            "A server that wants none of this can set every amount below to 0, which",
                            "pins both values where they start and leaves sect joining as it was.")
                    .push("standing");

            standingHonourPerHonourableWin = builder
                    .comment("Honour for winning a duel you challenged for. At the default it takes",
                            "five clean wins to reach the orthodox threshold, which is meant to be a",
                            "few evenings rather than a grind.")
                    .defineInRange("honourPerHonourableWin", 4, 0, 100);

            standingInfamyForgivenPerWin = builder
                    .comment("Infamy shed by the same win. Infamy never decays on a timer, so this is",
                            "the only way back: redemption is something you do rather than something",
                            "you wait out. Set to 0 to make a reputation for cruelty permanent.")
                    .defineInRange("infamyForgivenPerWin", 1, 0, 100);

            standingInfamyPerAmbush = builder
                    .comment("Infamy for striking someone who was not fighting you.")
                    .defineInRange("infamyPerAmbush", 6, 0, 100);

            standingHonourLostPerAmbush = builder
                    .comment("Honour lost for the same. Larger than the infamy gain is not required;",
                            "these are separate axes and may move by different amounts.")
                    .defineInRange("honourLostPerAmbush", 3, 0, 100);

            standingInfamyPerKillingYielded = builder
                    .comment("Infamy for killing an opponent who had already yielded. Deliberately the",
                            "largest number here: sparing someone only means something because",
                            "refusing to is available and costly.")
                    .defineInRange("infamyPerKillingYielded", 12, 0, 100);

            standingHonourLostPerKillingYielded = builder
                    .comment("Honour lost for killing the yielded.")
                    .defineInRange("honourLostPerKillingYielded", 10, 0, 100);

            standingOrthodoxHonourRequired = builder
                    .comment("Honour an orthodox sect wants before it will take you.")
                    .defineInRange("orthodoxHonourRequired", 20, 0, 100);

            standingOrthodoxInfamyLimit = builder
                    .comment("Infamy above which an orthodox sect refuses you whatever your honour.",
                            "The two halves are not the same test: honour is what they want, infamy is",
                            "what they will not be seen with, and someone can be both respected and",
                            "too dangerous to associate with.")
                    .defineInRange("orthodoxInfamyLimit", 25, 0, 100);

            standingDemonicInfamyRequired = builder
                    .comment("Infamy a demonic sect wants before it takes you seriously. They have no",
                            "use for someone nobody fears.")
                    .defineInRange("demonicInfamyRequired", 20, 0, 100);

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

    public static int focusPromptMinSeconds() {
        return VALUES.focusPromptMinSeconds.get();
    }

    public static int focusPromptMaxSeconds() {
        return VALUES.focusPromptMaxSeconds.get();
    }

    public static double focusSweepSeconds() {
        return VALUES.focusSweepSeconds.get();
    }

    public static double focusWindowWidth() {
        return VALUES.focusWindowWidth.get();
    }

    public static int focusMinReactionTicks() {
        return VALUES.focusMinReactionTicks.get();
    }

    public static double focusLatencyTolerance() {
        return VALUES.focusLatencyTolerance.get();
    }

    public static double focusPerfectFraction() {
        return VALUES.focusPerfectFraction.get();
    }

    public static double focusHitGain() {
        return VALUES.focusHitGain.get();
    }

    public static double focusMissPenalty() {
        return VALUES.focusMissPenalty.get();
    }

    public static double focusPerfectBonusSeconds() {
        return VALUES.focusPerfectBonusSeconds.get();
    }

    public static int focusBreakthroughSweeps() {
        return VALUES.focusBreakthroughSweeps.get();
    }

    public static double focusBreakthroughTighten() {
        return VALUES.focusBreakthroughTighten.get();
    }

    public static double focusBreakthroughBonus() {
        return VALUES.focusBreakthroughBonus.get();
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

    public static double spiritVeinBonus() {
        return VALUES.spiritVeinBonus.get();
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

    public static double breakthroughPurityWeight() {
        return VALUES.breakthroughPurityWeight.get();
    }

    public static double breakthroughMeridianWeight() {
        return VALUES.breakthroughMeridianWeight.get();
    }

    public static double breakthroughDensityWeight() {
        return VALUES.breakthroughDensityWeight.get();
    }

    public static double breakthroughMinChance() {
        return VALUES.breakthroughMinChance.get();
    }

    public static double breakthroughMaxChance() {
        return VALUES.breakthroughMaxChance.get();
    }

    public static double breakthroughMinorFailureChance() {
        return VALUES.breakthroughMinorFailureChance.get();
    }

    public static double breakthroughSevereFailureChance() {
        return VALUES.breakthroughSevereFailureChance.get();
    }

    public static boolean breakthroughLightning() {
        return VALUES.breakthroughLightning.get();
    }

    public static double meridianBaseCostInSubstages() {
        return VALUES.meridianBaseCostInSubstages.get();
    }

    public static double meridianCostGrowthPerOpenNode() {
        return VALUES.meridianCostGrowthPerOpenNode.get();
    }

    public static double meridianPrimaryDeviationChance() {
        return VALUES.meridianPrimaryDeviationChance.get();
    }

    public static double meridianExtraordinaryDeviationChance() {
        return VALUES.meridianExtraordinaryDeviationChance.get();
    }

    public static double meridianPurityProtection() {
        return VALUES.meridianPurityProtection.get();
    }

    public static double meridianMinimumPurity() {
        return VALUES.meridianMinimumPurity.get();
    }

    public static double masteryGainFalloff() {
        return VALUES.masteryGainFalloff.get();
    }

    public static double masteryMinGainFraction() {
        return VALUES.masteryMinGainFraction.get();
    }

    public static double masteryQiCostReduction() {
        return VALUES.masteryQiCostReduction.get();
    }

    public static double masteryCooldownReduction() {
        return VALUES.masteryCooldownReduction.get();
    }

    public static boolean techniquesHarmPlayers() {
        return VALUES.techniquesHarmPlayers.get();
    }

    public static double statPointPower() {
        return VALUES.statPointPower.get();
    }

    public static int statPointsPerSubstage() {
        return VALUES.statPointsPerSubstage.get();
    }

    public static int statPointsPerRealm() {
        return VALUES.statPointsPerRealm.get();
    }

    public static int dailyResetIntervalDays() {
        return VALUES.dailyResetIntervalDays.get();
    }

    public static int standingHonourPerHonourableWin() {
        return VALUES.standingHonourPerHonourableWin.get();
    }

    public static int standingInfamyForgivenPerWin() {
        return VALUES.standingInfamyForgivenPerWin.get();
    }

    public static int standingInfamyPerAmbush() {
        return VALUES.standingInfamyPerAmbush.get();
    }

    public static int standingHonourLostPerAmbush() {
        return VALUES.standingHonourLostPerAmbush.get();
    }

    public static int standingInfamyPerKillingYielded() {
        return VALUES.standingInfamyPerKillingYielded.get();
    }

    public static int standingHonourLostPerKillingYielded() {
        return VALUES.standingHonourLostPerKillingYielded.get();
    }

    public static int standingOrthodoxHonourRequired() {
        return VALUES.standingOrthodoxHonourRequired.get();
    }

    public static int standingOrthodoxInfamyLimit() {
        return VALUES.standingOrthodoxInfamyLimit.get();
    }

    public static int standingDemonicInfamyRequired() {
        return VALUES.standingDemonicInfamyRequired.get();
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
