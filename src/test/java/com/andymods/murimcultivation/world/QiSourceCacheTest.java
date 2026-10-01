package com.andymods.murimcultivation.world;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The spirit-vein cache, and the three ways it used to hand one place's count to another.
 *
 * <p>All three are regressions found by reading rather than by playing: two dimensions at the same
 * coordinates shared a count, singleplayer's two sides shared a count, and an entry stamped by an
 * older world's later clock read as fresh forever.
 */
class QiSourceCacheTest {

    private static ResourceKey<Level> overworld;
    private static ResourceKey<Level> nether;
    private static final BlockPos POS = new BlockPos(8, 64, -8);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        overworld = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
        nether = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
    }

    @BeforeEach
    void clean() {
        QiSources.clearAll();
    }

    @Test
    void twoDimensionsAtOneCoordinateDoNotShareACount() {
        QiSources.store(QiSources.CacheKey.of(overworld, false, POS), 100L, 4);

        assertEquals(OptionalInt.of(4), QiSources.cached(QiSources.CacheKey.of(overworld, false, POS), 105L));
        assertFalse(QiSources.cached(QiSources.CacheKey.of(nether, false, POS), 105L).isPresent(),
                "a vein array in the Overworld must not bless the same coordinates in the Nether");
    }

    @Test
    void theTwoSidesOfOneDimensionDoNotShareACount() {
        QiSources.store(QiSources.CacheKey.of(overworld, false, POS), 100L, 4);

        assertFalse(QiSources.cached(QiSources.CacheKey.of(overworld, true, POS), 105L).isPresent(),
                "the client's HUD and the integrated server must keep separate entries");
        assertNotEquals(QiSources.CacheKey.of(overworld, false, POS), QiSources.CacheKey.of(overworld, true, POS));
    }

    @Test
    void anEntryFromTheFutureIsStaleRatherThanFresh() {
        // Stamped by a world whose clock was further along than the one now loaded.
        QiSources.store(QiSources.CacheKey.of(overworld, false, POS), 50_000L, 4);

        assertFalse(QiSources.cached(QiSources.CacheKey.of(overworld, false, POS), 1_000L).isPresent());
    }

    @Test
    void anEntryExpiresAfterItsWindow() {
        QiSources.CacheKey key = QiSources.CacheKey.of(overworld, false, POS);
        QiSources.store(key, 100L, 2);

        assertEquals(OptionalInt.of(2), QiSources.cached(key, 100L), "fresh the tick it was taken");
        assertEquals(OptionalInt.of(2), QiSources.cached(key, 100L + QiSources.CACHE_TICKS - 1));
        assertFalse(QiSources.cached(key, 100L + QiSources.CACHE_TICKS).isPresent());
    }

    @Test
    void nearbyPositionsShareAQuantisedCell() {
        assertEquals(QiSources.CacheKey.of(overworld, false, new BlockPos(8, 64, 8)),
                QiSources.CacheKey.of(overworld, false, new BlockPos(11, 67, 11)));
        assertNotEquals(QiSources.CacheKey.of(overworld, false, new BlockPos(8, 64, 8)),
                QiSources.CacheKey.of(overworld, false, new BlockPos(12, 64, 8)));
    }

    @Test
    void invalidatingOneLevelLeavesTheOthersAlone() {
        QiSources.store(QiSources.CacheKey.of(overworld, false, POS), 100L, 1);
        QiSources.store(QiSources.CacheKey.of(overworld, true, POS), 100L, 2);
        QiSources.store(QiSources.CacheKey.of(nether, false, POS), 100L, 3);

        QiSources.invalidate(overworld, false);

        assertFalse(QiSources.cached(QiSources.CacheKey.of(overworld, false, POS), 101L).isPresent());
        assertEquals(OptionalInt.of(2), QiSources.cached(QiSources.CacheKey.of(overworld, true, POS), 101L));
        assertEquals(OptionalInt.of(3), QiSources.cached(QiSources.CacheKey.of(nether, false, POS), 101L));
    }
}
