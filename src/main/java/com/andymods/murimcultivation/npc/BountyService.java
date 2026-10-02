package com.andymods.murimcultivation.npc;

import com.andymods.murimcultivation.config.MurimConfig;

/**
 * When the murim stops waiting for you to come to it.
 *
 * <p>Infamy until now was passive: it closed orthodox doors and made wanderers attack rather than
 * talk, but nothing ever came looking. A bounty hunter is that missing half. Past a threshold of
 * infamy, every so often, someone with a price to collect is sent after you.
 *
 * <p>Unlike a wanderer, a hunter is matched to <em>you</em> rather than to the place, because the
 * point is pursuit: a hunter you could always outclass would not be a consequence. It does not duel,
 * does not yield and does not spare. You are not its opponent; you are its contract.
 *
 * <p>Pure and tuned, so who gets hunted and how hard is testable without a world.
 */
public final class BountyService {

    private BountyService() {
    }

    /**
     * @param enabled whether hunters are sent at all
     * @param infamyThreshold infamy at or above which a price goes on your head
     * @param checkIntervalTicks how often the dice are rolled for each player
     * @param chancePerCheck the chance, each check, that a hunter is actually sent
     * @param infamyForStrongerHunter infamy at or above which the hunter is a realm above you
     * @param maxRealmTier the highest realm a hunter may be, whoever it is chasing
     */
    public record Tuning(boolean enabled,
                         int infamyThreshold,
                         int checkIntervalTicks,
                         double chancePerCheck,
                         int infamyForStrongerHunter,
                         int maxRealmTier) {

        public static Tuning fromConfig() {
            return new Tuning(
                    MurimConfig.bountyEnabled(),
                    MurimConfig.bountyInfamyThreshold(),
                    MurimConfig.bountyCheckIntervalSeconds() * 20,
                    MurimConfig.bountyChancePerCheck(),
                    MurimConfig.bountyInfamyForStrongerHunter(),
                    MurimConfig.warriorMaxNaturalRealmTier() + 1);
        }
    }

    /** Whether this player is notorious enough to have a price on their head at all. */
    public static boolean isWanted(int infamy, Tuning tuning) {
        return tuning.enabled() && infamy >= tuning.infamyThreshold();
    }

    /**
     * Whether a hunter is sent this check.
     *
     * @param roll a uniform number in [0, 1)
     */
    public static boolean sendsHunter(int infamy, double roll, Tuning tuning) {
        return isWanted(infamy, tuning) && roll < tuning.chancePerCheck();
    }

    /**
     * The hunter's realm: yours, or one above if you are notorious enough to be worth the expense.
     * Never below the first realm and never above the cap, so a datapack's top realm stays
     * something you climb to rather than something sent after you.
     */
    public static int hunterRealmTier(int targetRealmTier, int infamy, Tuning tuning) {
        int tier = targetRealmTier + (infamy >= tuning.infamyForStrongerHunter() ? 1 : 0);
        return Math.max(1, Math.min(tier, Math.max(1, tuning.maxRealmTier())));
    }
}
