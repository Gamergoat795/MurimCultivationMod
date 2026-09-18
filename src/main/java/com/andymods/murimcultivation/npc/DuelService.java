package com.andymods.murimcultivation.npc;

import com.andymods.murimcultivation.config.MurimConfig;
import net.minecraft.util.StringRepresentable;

/**
 * Whether a challenge is accepted, and on what terms.
 *
 * <p>A duel is a fight with two extra rules — it has to be asked for, and it ends on a yield
 * rather than a death. That is the smallest shape that supports the fantasy, and deliberately not
 * a minigame: the interesting decision is <em>who</em> you challenge and whether you spare them,
 * not a sequence of button presses.
 *
 * <p>The acceptance rule is pure and lives here on its own, because it is the part with a design
 * in it. Everything about entities, targets and damage lives in the caller.
 */
public final class DuelService {

    private DuelService() {
    }

    /**
     * @param maxRealmGap how many realms apart two cultivators may be and still have a duel worth
     *                    fighting, in either direction
     * @param infamyThatProvokes infamy at or above which a wanderer stops parleying and simply
     *                           attacks — the "wanderers attack on sight" half of infamy, arriving
     *                           as a consequence of the number rather than as a separate feature
     * @param yieldHealthFraction the share of maximum health at which a duellist yields
     */
    public record Tuning(int maxRealmGap, int infamyThatProvokes, double yieldHealthFraction) {

        public static Tuning fromConfig() {
            return new Tuning(
                    MurimConfig.duelMaxRealmGap(),
                    MurimConfig.duelInfamyThatProvokes(),
                    MurimConfig.duelYieldHealthFraction());
        }
    }

    /**
     * How a wanderer answers being challenged.
     *
     * <p>Carries explicit serialized names rather than deriving them from {@code name()}, which is
     * the convention every other enum in the mod follows — and the reason is mechanical, not
     * stylistic: {@code tools/verify_sources.py} reads the {@code NAME("id")} form to work out which
     * translation keys a runtime-built prefix can produce. An enum without them is invisible to that
     * check, so a missing message would ship silently.
     */
    public enum Verdict implements StringRepresentable {

        /** They accept, and the duel begins. */
        ACCEPTED("accepted"),
        /** They are far enough above the challenger to consider the request presumptuous. */
        REFUSED_TOO_STRONG("refused_too_strong"),
        /** They are far enough below to know it would be a beating, not a duel. */
        REFUSED_TOO_WEAK("refused_too_weak"),
        /** The challenger's reputation precedes them; there is no parley to be had. */
        ATTACKS_INSTEAD("attacks_instead");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        public boolean accepted() {
            return this == ACCEPTED;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public String translationKey() {
            return "murimcultivation.duel." + id;
        }
    }

    /**
     * Whether this wanderer will fight this challenger.
     *
     * <p>The realm gap is checked <strong>in both directions</strong>, and the downward half is the
     * less obvious one. Upward it is the plan's "a Master refuses unworthy challengers", which also
     * makes the Experts and Masters in the far reaches something to grow into. Downward it closes
     * an honour farm: without it a Transcendent could collect honour off Third-Rate thugs
     * indefinitely, and honour earned by beating people who cannot fight back is precisely what the
     * word is supposed to exclude.
     *
     * <p>Infamy is checked first because it overrides the question. Someone notorious enough is not
     * being weighed as a challenger at all.
     */
    public static Verdict judge(int challengerRealmTier, int warriorRealmTier,
                                int challengerInfamy, Tuning tuning) {
        if (challengerInfamy >= tuning.infamyThatProvokes()) {
            return Verdict.ATTACKS_INSTEAD;
        }

        int gap = warriorRealmTier - challengerRealmTier;
        if (gap > tuning.maxRealmGap()) {
            return Verdict.REFUSED_TOO_STRONG;
        }
        if (-gap > tuning.maxRealmGap()) {
            return Verdict.REFUSED_TOO_WEAK;
        }
        return Verdict.ACCEPTED;
    }

    /**
     * The health a duellist yields at, given their maximum.
     *
     * <p>Never below one, so a yield always leaves someone alive. A configured fraction of zero
     * would otherwise mean "yields at death", which is the one outcome the whole rule exists to
     * prevent.
     */
    public static float yieldThreshold(float maxHealth, Tuning tuning) {
        return Math.max(1.0F, (float) (maxHealth * tuning.yieldHealthFraction()));
    }

    /**
     * How much of an incoming blow to allow through so the victim lands on the yield threshold
     * rather than past it.
     *
     * @return the damage to apply, which is zero when the victim is already at or below the
     *         threshold and the full amount when the blow does not reach it
     */
    public static float damageBeforeYielding(float currentHealth, float maxHealth,
                                             float incoming, Tuning tuning) {
        float threshold = yieldThreshold(maxHealth, tuning);
        if (currentHealth - incoming > threshold) {
            return incoming;
        }
        return Math.max(0.0F, currentHealth - threshold);
    }

    /** Whether a blow of this size would take the victim to or past the yield threshold. */
    public static boolean wouldYield(float currentHealth, float maxHealth,
                                     float incoming, Tuning tuning) {
        return currentHealth - incoming <= yieldThreshold(maxHealth, tuning);
    }
}
