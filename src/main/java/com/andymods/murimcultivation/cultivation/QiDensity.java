package com.andymods.murimcultivation.cultivation;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.config.MurimConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * How rich the ambient Qi is where the cultivator is sitting.
 *
 * <p>This is what stops meditation from being a screensaver. Where you choose to cultivate
 * matters: a bamboo grove or a mountain peak at midnight in a thunderstorm is worth
 * meaningfully more than standing in a desert at noon, so players go looking for good spots
 * instead of AFK-ing in a hole.
 *
 * <p>Biome quality is driven by tags, so a datapack can redefine what counts as spiritually
 * rich without touching code.
 */
public final class QiDensity {

    public static final TagKey<Biome> QI_RICH =
            TagKey.create(Registries.BIOME, MurimCultivationMod.id("qi_rich"));
    public static final TagKey<Biome> QI_BARREN =
            TagKey.create(Registries.BIOME, MurimCultivationMod.id("qi_barren"));

    /** Sea level. Altitude bonuses are measured as distance away from here, in either direction. */
    private static final int NEUTRAL_ALTITUDE = 63;

    private QiDensity() {
    }

    /**
     * The overall multiplier applied to cultivation gains at this position.
     *
     * <p>Deliberately multiplicative and bounded: a perfect spot should feel like a real find,
     * not like a different game. The floor keeps a bad location slow rather than useless.
     */
    public static double multiplierAt(Level level, BlockPos pos) {
        double multiplier = biomeMultiplier(level, pos)
                * altitudeMultiplier(pos)
                * timeMultiplier(level)
                * weatherMultiplier(level);

        return Math.max(MurimConfig.qiDensityMinimum(), Math.min(MurimConfig.qiDensityMaximum(), multiplier));
    }

    public static double multiplierFor(Player player) {
        return multiplierAt(player.level(), player.blockPosition());
    }

    /** Spiritually rich land helps; barren land hinders. Both are tag-driven. */
    public static double biomeMultiplier(Level level, BlockPos pos) {
        var biome = level.getBiome(pos);
        if (biome.is(QI_RICH)) {
            return MurimConfig.qiDensityRichBiome();
        }
        if (biome.is(QI_BARREN)) {
            return MurimConfig.qiDensityBarrenBiome();
        }
        return 1.0D;
    }

    /**
     * Height above or depth below sea level both help, which is why hermits pick mountain
     * peaks and deep caves rather than the middle of a plain.
     *
     * <p>The arithmetic is separated from the config lookup so the balance curve can be unit
     * tested. Reading config inside the formula makes it unreachable from a test, because
     * {@code ModConfigSpec} values throw until a world has loaded them.
     */
    public static double altitudeMultiplier(int y, double bonusPerBlock, double bonusCap) {
        int distance = Math.abs(y - NEUTRAL_ALTITUDE);
        return 1.0D + Math.min(bonusCap, distance * bonusPerBlock);
    }

    public static double altitudeMultiplier(BlockPos pos) {
        return altitudeMultiplier(pos.getY(),
                MurimConfig.qiDensityAltitudeBonusPerBlock(),
                MurimConfig.qiDensityAltitudeBonusCap());
    }

    /** Night is the traditional hour for cultivation. */
    public static double timeMultiplier(Level level) {
        return level.isNight() ? MurimConfig.qiDensityNight() : 1.0D;
    }

    /** A storm charges the air; the heavens are paying attention. */
    public static double weatherMultiplier(Level level) {
        if (level.isThundering()) {
            return MurimConfig.qiDensityThunder();
        }
        return level.isRaining() ? MurimConfig.qiDensityRain() : 1.0D;
    }

    /**
     * A coarse label for the HUD, so a player can tell whether a spot is worth sitting in
     * without doing arithmetic.
     */
    public static Quality qualityOf(double multiplier) {
        if (multiplier >= 1.75D) {
            return Quality.SPIRIT_VEIN;
        }
        if (multiplier >= 1.30D) {
            return Quality.RICH;
        }
        if (multiplier >= 0.95D) {
            return Quality.ORDINARY;
        }
        return Quality.BARREN;
    }

    public enum Quality {
        BARREN("barren"),
        ORDINARY("ordinary"),
        RICH("rich"),
        SPIRIT_VEIN("spirit_vein");

        private final String id;

        Quality(String id) {
            this.id = id;
        }

        public String translationKey() {
            return "murimcultivation.qi_density." + id;
        }
    }
}
