package com.andymods.murimcultivation.standing;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The standing record itself: what it clamps, what it deliberately does not, and that it survives
 * a round trip.
 *
 * <p>The asymmetry is the thing most worth pinning down. Honour and infamy are bounded 0–100
 * because they are read as thresholds; sect reputation is unbounded and may go negative because it
 * always could, and quietly clamping it here would change how {@code SectRank.forReputation}
 * behaves for anyone a sect has turned against.
 */
class MurimStandingTest {

    private static final ResourceLocation ALLIANCE =
            ResourceLocation.fromNamespaceAndPath("murimcultivation", "murim_alliance");
    private static final ResourceLocation CULT =
            ResourceLocation.fromNamespaceAndPath("murimcultivation", "demonic_cult");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aFreshCultivatorIsUnknownToEveryone() {
        MurimStanding standing = new MurimStanding();
        assertEquals(0, standing.honour());
        assertEquals(0, standing.infamy());
        assertEquals(Map.of(), standing.sectReputation());
        assertEquals(0, standing.sectReputation(ALLIANCE), "an unmet sect reads as zero, not absent");
    }

    @Test
    void honourAndInfamyClampToTheirRange() {
        MurimStanding standing = new MurimStanding(Map.of(), 500, -500);
        assertEquals(MurimStanding.MAX, standing.honour(), "the constructor sanitises rather than trusting");
        assertEquals(MurimStanding.MIN, standing.infamy());
    }

    @Test
    void movingReportsWhatWasActuallyApplied() {
        // The clamped difference is the return value, so a caller can tell the player the truth
        // instead of the amount it asked for.
        MurimStanding nearTheTop = new MurimStanding(Map.of(), 98, 0);
        assertEquals(2, nearTheTop.addHonour(5));
        assertEquals(MurimStanding.MAX, nearTheTop.honour());

        MurimStanding nearTheFloor = new MurimStanding(Map.of(), 2, 0);
        assertEquals(-2, nearTheFloor.addHonour(-5));
        assertEquals(MurimStanding.MIN, nearTheFloor.honour());

        MurimStanding spotless = new MurimStanding();
        assertEquals(0, spotless.addInfamy(-5), "there is nothing to forgive");
        assertEquals(MurimStanding.MIN, spotless.infamy());
    }

    @Test
    void sectReputationIsDeliberatelyNotClamped() {
        // Being actively unwelcome is a real state; SectRank.forReputation returns OUTSIDER for it.
        MurimStanding standing = new MurimStanding();
        standing.addSectReputation(ALLIANCE, -400);
        assertEquals(-400, standing.sectReputation(ALLIANCE));

        standing.addSectReputation(CULT, 9000);
        assertEquals(9000, standing.sectReputation(CULT), "rank thresholds run well past 100");
    }

    @Test
    void theReputationViewIsReadOnly() {
        MurimStanding standing = new MurimStanding();
        standing.addSectReputation(ALLIANCE, 10);
        assertThrows(UnsupportedOperationException.class,
                () -> standing.sectReputation().put(CULT, 1),
                "handing out a mutable view would let a caller bypass the accessors");
    }

    @Test
    void everythingSurvivesARoundTrip() {
        MurimStanding original = new MurimStanding();
        original.addSectReputation(ALLIANCE, 250);
        original.addSectReputation(CULT, -30);
        original.setHonour(63);
        original.setInfamy(17);

        Tag encoded = MurimStanding.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        MurimStanding restored = MurimStanding.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        assertEquals(63, restored.honour());
        assertEquals(17, restored.infamy());
        assertEquals(original.sectReputation(), restored.sectReputation());
    }

    @Test
    void anEmptyTagDecodesToAFreshStanding() {
        // Every field is optional, which is what lets a world saved before this record existed
        // load without failing — it arrives with no murim_standing key at all.
        MurimStanding restored = MurimStanding.CODEC
                .parse(JsonOps.INSTANCE, new JsonObject())
                .getOrThrow();
        assertEquals(0, restored.honour());
        assertEquals(0, restored.infamy());
        assertEquals(Map.of(), restored.sectReputation());
    }

    @Test
    void copyingDuplicatesEverythingAndLeavesTheTargetUsable() {
        MurimStanding source = new MurimStanding();
        source.addSectReputation(ALLIANCE, 120);
        source.setHonour(40);
        source.setInfamy(8);

        MurimStanding target = new MurimStanding();
        target.addSectReputation(CULT, 999);
        target.copyFrom(source);

        assertEquals(40, target.honour());
        assertEquals(8, target.infamy());
        assertEquals(120, target.sectReputation(ALLIANCE));
        assertEquals(0, target.sectReputation(CULT), "copyFrom replaces rather than merges");

        // Copied in place, so the parent's final reference stays valid and independent.
        target.setHonour(1);
        assertEquals(40, source.honour(), "the two must not share state after a copy");
    }
}
