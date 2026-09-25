package com.andymods.murimcultivation.npc;

/**
 * The rules of a spar: a fight between a cultivator and a martial artist who is not their
 * enemy, which ends when one side yields rather than when one side dies.
 *
 * <p>Pure arithmetic with no game state, so the rules can be tested without a world. The
 * entity and {@code SparringEvents} own the state and apply these.
 */
public final class Sparring {

    /** Either side yields once brought below this fraction of their maximum health. */
    public static final float YIELD_FRACTION = 0.3F;

    /** A spar with no blow exchanged for this long, in ticks, is abandoned. */
    public static final long IDLE_TIMEOUT_TICKS = 20L * 20L;

    /** Walking further apart than this, in blocks, abandons the spar. */
    public static final double MAX_DISTANCE = 16.0D;

    private Sparring() {
    }

    /**
     * The damage a blow may do in a spar: all of it, except whatever would take the last point
     * of health. Nobody dies in a spar, however hard the final hit lands.
     */
    public static float protect(float health, float incoming) {
        return Math.max(0.0F, Math.min(incoming, health - 1.0F));
    }

    /** Whether someone at this health has been beaten and yields. */
    public static boolean yields(float health, float maxHealth) {
        return maxHealth > 0.0F && health < maxHealth * YIELD_FRACTION;
    }

    /** Whether a spar has lapsed: too long without a blow, or the two have walked apart. */
    public static boolean abandoned(long now, long lastExchange, double distanceSqr) {
        return now - lastExchange > IDLE_TIMEOUT_TICKS || distanceSqr > MAX_DISTANCE * MAX_DISTANCE;
    }
}
