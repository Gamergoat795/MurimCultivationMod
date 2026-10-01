package com.andymods.murimcultivation.world;

import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local sources of ambient Qi — right now, spirit veins.
 *
 * <p>Separated from {@link com.andymods.murimcultivation.cultivation.QiDensity} because that
 * class is about the *place* (biome, altitude, weather) while this is about what has been *put*
 * there. Keeping them apart means a later milestone can add generated formations, sect
 * cultivation chambers or depleting veins without touching the density formula itself.
 *
 * <p><strong>Results are cached, and that is not optional.</strong> Counting veins means a
 * volume scan of over a thousand block positions, and the callers are the per-tick cultivation
 * loop and the HUD — which asks every frame. Uncached, that is on the order of a hundred
 * thousand block lookups a second on the client alone. The cache recomputes at most once per
 * second per position, which is far finer than the rate at which someone places a vein.
 */
public final class QiSources {

    /** How far a vein's influence reaches horizontally, in blocks. */
    public static final int VEIN_RADIUS = 6;

    /**
     * Vertical reach, deliberately shorter than the horizontal.
     *
     * <p>Veins sit on the floor of a chamber, so a tall scan mostly counts air — and the volume
     * scanned, and so the cost, grows linearly with it.
     */
    public static final int VEIN_HEIGHT = 3;

    /** How long a count stays good for, in ticks. */
    static final long CACHE_TICKS = 20L;

    /**
     * Bounded so a server with many players cannot grow it without limit. Concurrent because
     * the server thread and the client render thread both call in.
     */
    static final int CACHE_CAPACITY = 256;
    private static final Map<CacheKey, CachedCount> CACHE = new ConcurrentHashMap<>();

    private QiSources() {
    }

    /**
     * The multiplier contributed by nearby spirit veins, 1.0 if there are none.
     *
     * <p>Diminishing returns: the first vein matters most, and paving a floor with them should
     * not scale without limit.
     */
    public static double multiplierAt(Level level, BlockPos pos) {
        int veins = countVeinsCached(level, pos);
        if (veins <= 0) {
            return 1.0D;
        }
        // Square-root growth, so four veins are worth roughly twice one rather than four times.
        return 1.0D + MurimConfig.spiritVeinBonus() * Math.sqrt(veins);
    }

    /**
     * Drops one side's cached counts for one dimension. Called when that level unloads, so a world
     * loaded afterwards cannot read a count that belonged to the one before it.
     */
    public static void invalidate(ResourceKey<Level> dimension, boolean clientSide) {
        CACHE.keySet().removeIf(key -> key.dimension().equals(dimension) && key.clientSide() == clientSide);
    }

    private static int countVeinsCached(Level level, BlockPos pos) {
        long tick = level.getGameTime();
        CacheKey key = CacheKey.of(level.dimension(), level.isClientSide(), pos);

        OptionalInt cached = cached(key, tick);
        if (cached.isPresent()) {
            return cached.getAsInt();
        }

        int counted = countVeins(level, pos);
        store(key, tick, counted);
        return counted;
    }

    /**
     * A fresh cached count, or empty.
     *
     * <p>Fresh means {@code 0 <= elapsed < CACHE_TICKS}, and the lower bound is not decoration.
     * Game time is per-level and per-world, so leaving one singleplayer world and loading an older
     * one meets an entry stamped in the future. Without the bound that reads as a negative age,
     * negative is less than twenty, and the stale count is served indefinitely.
     */
    static OptionalInt cached(CacheKey key, long tick) {
        CachedCount entry = CACHE.get(key);
        if (entry == null) {
            return OptionalInt.empty();
        }
        long elapsed = tick - entry.tick();
        return elapsed >= 0 && elapsed < CACHE_TICKS ? OptionalInt.of(entry.count()) : OptionalInt.empty();
    }

    static void store(CacheKey key, long tick, int count) {
        if (CACHE.size() >= CACHE_CAPACITY) {
            // Cheap eviction: the entries are all short-lived, so clearing costs one rescan.
            CACHE.clear();
        }
        CACHE.put(key, new CachedCount(tick, count));
    }

    /** Test hook: a clean slate between cases, since the cache is static. */
    static void clearAll() {
        CACHE.clear();
    }

    private static int countVeins(LevelReader level, BlockPos centre) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                centre.offset(-VEIN_RADIUS, -VEIN_HEIGHT, -VEIN_RADIUS),
                centre.offset(VEIN_RADIUS, VEIN_HEIGHT, VEIN_RADIUS))) {
            // Never force a chunk load from a tick or a render.
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            if (level.getBlockState(pos).is(ModBlocks.SPIRIT_VEIN.get())) {
                found++;
            }
        }
        return found;
    }

    /**
     * Where a count was taken: dimension, side and a quantised cell.
     *
     * <p>All three are needed. Without the dimension, two players at the same coordinates in the
     * Overworld and the Nether share a count — and the 8:1 coordinate scale makes that routine near
     * the origin. Without the side, singleplayer's client and integrated server write over each
     * other, because a dimension's key is identical on both. Kept as a record rather than packed
     * into a long, since packing would mean hashing the dimension and a hash collision would
     * bring back exactly the bug this exists to prevent.
     */
    record CacheKey(ResourceKey<Level> dimension, boolean clientSide, long cell) {

        /** Quantised so a walking player reuses a neighbour's entry instead of thrashing. */
        static CacheKey of(ResourceKey<Level> dimension, boolean clientSide, BlockPos pos) {
            return new CacheKey(dimension, clientSide,
                    BlockPos.asLong(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2));
        }
    }

    /** How many veins were counted near a quantised position, and when. */
    private record CachedCount(long tick, int count) {
    }
}
