package com.andymods.murimcultivation.system;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stat point and title bookkeeping.
 *
 * <p>The invariant that matters most: a player can never end up with more allocated than they
 * earned. A UI sending a stale click, a double-processed packet or a refund bug all show up as
 * conjured points, so spending refuses rather than clamps and every path is checked here.
 */
class SystemProgressTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        // StatType holds Holder<Attribute> constants.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("murimcultivation", path);
    }

    @Test
    void aFreshCultivatorHasNothing() {
        SystemProgress progress = new SystemProgress();
        assertEquals(0, progress.unspentPoints());
        assertEquals(0, progress.totalSpent());
        assertTrue(progress.titles().isEmpty());
        assertTrue(progress.equippedTitle().isEmpty());
    }

    @Test
    void spendingMovesPointsFromUnspentToAllocated() {
        SystemProgress progress = new SystemProgress();
        progress.grantPoints(10);

        assertTrue(progress.spend(StatType.BODY, 4));
        assertEquals(6, progress.unspentPoints());
        assertEquals(4, progress.pointsIn(StatType.BODY));
        assertEquals(4, progress.totalSpent());
    }

    @Test
    void pointsCanNeverBeConjured() {
        SystemProgress progress = new SystemProgress();
        progress.grantPoints(3);

        assertFalse(progress.spend(StatType.FORCE, 4), "spending more than held must be refused");
        assertEquals(3, progress.unspentPoints(), "a refused spend must not deduct");
        assertEquals(0, progress.totalSpent(), "a refused spend must not allocate");

        // The sum of unspent and spent must always equal what was granted.
        assertTrue(progress.spend(StatType.FORCE, 3));
        assertEquals(3, progress.unspentPoints() + progress.totalSpent());
    }

    @Test
    void zeroAndNegativeSpendsAreRefused() {
        SystemProgress progress = new SystemProgress();
        progress.grantPoints(5);
        assertFalse(progress.spend(StatType.BODY, 0));
        assertFalse(progress.spend(StatType.BODY, -3));
        assertEquals(5, progress.unspentPoints());
    }

    @Test
    void aStatCannotExceedItsCap() {
        SystemProgress progress = new SystemProgress();
        progress.grantPoints(StatType.MAX_POINTS_PER_STAT + 50);

        assertTrue(progress.spend(StatType.BODY, StatType.MAX_POINTS_PER_STAT));
        assertFalse(progress.spend(StatType.BODY, 1), "a capped stat must refuse further points");
        assertEquals(StatType.MAX_POINTS_PER_STAT, progress.pointsIn(StatType.BODY));
        assertEquals(50, progress.unspentPoints(), "the refused point must stay unspent");
    }

    @Test
    void refundReturnsExactlyWhatWasSpent() {
        SystemProgress progress = new SystemProgress();
        progress.grantPoints(20);
        progress.spend(StatType.BODY, 5);
        progress.spend(StatType.INSIGHT, 7);

        int refunded = progress.refundAll();

        assertEquals(12, refunded);
        assertEquals(20, progress.unspentPoints(), "everything granted should be spendable again");
        assertEquals(0, progress.totalSpent());
        assertEquals(0, progress.pointsIn(StatType.BODY));
    }

    @Test
    void onlyOwnedTitlesCanBeWorn() {
        SystemProgress progress = new SystemProgress();
        assertFalse(progress.equipTitle(id("sword_saint")), "an unowned title must be refused");
        assertTrue(progress.equippedTitle().isEmpty());

        progress.grantTitle(id("sword_saint"));
        assertTrue(progress.equipTitle(id("sword_saint")));
        assertEquals(id("sword_saint"), progress.equippedTitle().orElseThrow());

        assertTrue(progress.equipTitle(null), "passing null should clear the slot");
        assertTrue(progress.equippedTitle().isEmpty());
    }

    @Test
    void grantingATitleTwiceIsNotAnError() {
        SystemProgress progress = new SystemProgress();
        assertTrue(progress.grantTitle(id("tempered")));
        assertFalse(progress.grantTitle(id("tempered")), "the second grant should report nothing new");
        assertEquals(1, progress.titles().size());
    }

    @Test
    void statBonusesScaleLinearlyAndRespectTheGlobalMultiplier() {
        assertEquals(0.0D, StatType.BODY.bonusFor(0, 1.0D), 1.0e-9D);
        assertEquals(StatType.BODY.perPoint() * 10, StatType.BODY.bonusFor(10, 1.0D), 1.0e-9D);
        assertEquals(StatType.BODY.perPoint() * 10 * 2.0D, StatType.BODY.bonusFor(10, 2.0D), 1.0e-9D);
        // A zero multiplier switches the mechanic off without breaking anything.
        assertEquals(0.0D, StatType.BODY.bonusFor(50, 0.0D), 1.0e-9D);
    }

    @Test
    void statBonusesAreClampedToTheCap() {
        double atCap = StatType.FORCE.bonusFor(StatType.MAX_POINTS_PER_STAT, 1.0D);
        double beyond = StatType.FORCE.bonusFor(StatType.MAX_POINTS_PER_STAT + 500, 1.0D);
        assertEquals(atCap, beyond, 1.0e-9D, "points beyond the cap must not keep paying out");
        assertEquals(0.0D, StatType.FORCE.bonusFor(-10, 1.0D), 1.0e-9D);
    }

    @Test
    void onlyTheStatsBackedByVanillaAttributesClaimToBe() {
        // Meridian and Insight feed values this mod derives itself, so they must not also try
        // to register an attribute modifier — that would double-count or silently do nothing.
        assertTrue(StatType.BODY.feedsAnAttribute());
        assertTrue(StatType.FORCE.feedsAnAttribute());
        assertFalse(StatType.MERIDIAN.feedsAnAttribute());
        assertFalse(StatType.INSIGHT.feedsAnAttribute());
    }

    @Test
    void copyFromDuplicatesEverything() {
        SystemProgress source = new SystemProgress();
        source.grantPoints(9);
        source.spend(StatType.MERIDIAN, 4);
        source.grantTitle(id("first_rate"));
        source.equipTitle(id("first_rate"));

        SystemProgress target = new SystemProgress();
        target.copyFrom(source);

        assertEquals(source.unspentPoints(), target.unspentPoints());
        assertEquals(source.allocations(), target.allocations());
        assertEquals(source.titles(), target.titles());
        assertEquals(source.equippedTitle(), target.equippedTitle());
    }
}
