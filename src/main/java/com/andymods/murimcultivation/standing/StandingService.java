package com.andymods.murimcultivation.standing;

import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.sect.SectAlignment;

/**
 * What moves honour and infamy, and what they unlock.
 *
 * <p><strong>The rule the whole system rests on: honour tracks conduct, not outcome.</strong>
 * Losing a duel you challenged honestly costs nothing — you simply gain nothing. There is
 * deliberately no {@code lostFairly} method below, and its absence is the design rather than an
 * omission. Without that line honour quietly becomes a second name for combat power, which is the
 * opposite of what the genre means by it: the point of a murim reputation is that it can be lost by
 * a strong cultivator and held by a weak one.
 *
 * <p>Honour and infamy are two independent values rather than one axis, because respected and
 * feared are different things and a cultivator can be both. It also means a fresh character sits at
 * low-low — spoken for by nobody — which is the right place to start.
 *
 * <p>Every method here is pure: it takes a {@link MurimStanding} and a {@link Tuning} and touches
 * no config and no player. That is not incidental. {@code ModConfigSpec} values throw until a world
 * has loaded them, so a formula that reads {@code MurimConfig} inline cannot be unit tested at all —
 * the lesson {@code BreakthroughService.Tuning} was extracted to fix.
 */
public final class StandingService {

    private StandingService() {
    }

    /**
     * The amounts conduct moves, and the thresholds sects judge you by.
     *
     * @param honourPerHonourableWin honour for winning a duel you challenged for
     * @param infamyForgivenPerWin infamy shed by the same, so redemption is done rather than waited out
     * @param infamyPerAmbush infamy for striking someone who was not fighting you
     * @param honourLostPerAmbush honour lost for the same
     * @param infamyPerKillingYielded infamy for killing an opponent who had already yielded
     * @param honourLostPerKillingYielded honour lost for the same — the worst act available
     * @param orthodoxHonourRequired honour an orthodox sect wants before it will hear you
     * @param orthodoxInfamyLimit infamy above which an orthodox sect refuses regardless of honour
     * @param demonicInfamyRequired infamy a demonic sect wants before it takes you seriously
     */
    public record Tuning(int honourPerHonourableWin,
                         int infamyForgivenPerWin,
                         int infamyPerAmbush,
                         int honourLostPerAmbush,
                         int infamyPerKillingYielded,
                         int honourLostPerKillingYielded,
                         int orthodoxHonourRequired,
                         int orthodoxInfamyLimit,
                         int demonicInfamyRequired) {

        public static Tuning fromConfig() {
            return new Tuning(
                    MurimConfig.standingHonourPerHonourableWin(),
                    MurimConfig.standingInfamyForgivenPerWin(),
                    MurimConfig.standingInfamyPerAmbush(),
                    MurimConfig.standingHonourLostPerAmbush(),
                    MurimConfig.standingInfamyPerKillingYielded(),
                    MurimConfig.standingHonourLostPerKillingYielded(),
                    MurimConfig.standingOrthodoxHonourRequired(),
                    MurimConfig.standingOrthodoxInfamyLimit(),
                    MurimConfig.standingDemonicInfamyRequired());
        }
    }

    // --- Conduct ----------------------------------------------------------------------

    /** You challenged, they accepted, you won. Honour up, and a little infamy shed. */
    public static void wonHonourably(MurimStanding standing, Tuning tuning) {
        standing.addHonour(tuning.honourPerHonourableWin());
        standing.addInfamy(-tuning.infamyForgivenPerWin());
    }

    /** You struck someone who was not fighting you. */
    public static void ambushed(MurimStanding standing, Tuning tuning) {
        standing.addInfamy(tuning.infamyPerAmbush());
        standing.addHonour(-tuning.honourLostPerAmbush());
    }

    /**
     * You killed an opponent who had already yielded.
     *
     * <p>The worst thing available, and priced accordingly. This is the act the yield rule exists
     * to make possible — a mechanic that lets you spare someone is only meaningful because you can
     * choose not to.
     */
    public static void killedTheYielded(MurimStanding standing, Tuning tuning) {
        standing.addInfamy(tuning.infamyPerKillingYielded());
        standing.addHonour(-tuning.honourLostPerKillingYielded());
    }

    // --- What standing unlocks --------------------------------------------------------

    /** Whether a sect of a given alignment will hear you out, and if not, why not. */
    public enum Verdict {
        ACCEPTED,
        /** An orthodox sect wants a reputation you have not built yet. */
        HONOUR_TOO_LOW,
        /** An orthodox sect will not be seen with someone this notorious. */
        INFAMY_TOO_HIGH,
        /** A demonic sect has no use for someone nobody fears. */
        INFAMY_TOO_LOW;

        public boolean accepted() {
            return this == ACCEPTED;
        }
    }

    /**
     * Whether this standing satisfies a sect of the given alignment.
     *
     * <p>Neutral sects judge nobody, which is what makes them the on-ramp: a cultivator who has
     * done nothing either way has to start somewhere, and the one unaligned sect the mod ships is
     * also the only one that will take a fresh awakening.
     *
     * <p>Note the orthodox test has two halves and they are not the same test. Honour is what they
     * want; infamy is what they will not tolerate. Someone can be both genuinely respected and too
     * dangerous to associate with, and an orthodox sect refuses them — which is only expressible
     * because honour and infamy are independent.
     */
    public static Verdict judge(SectAlignment alignment, MurimStanding standing, Tuning tuning) {
        return switch (alignment) {
            case ORTHODOX -> {
                if (standing.infamy() > tuning.orthodoxInfamyLimit()) {
                    yield Verdict.INFAMY_TOO_HIGH;
                }
                yield standing.honour() >= tuning.orthodoxHonourRequired()
                        ? Verdict.ACCEPTED
                        : Verdict.HONOUR_TOO_LOW;
            }
            case DEMONIC -> standing.infamy() >= tuning.demonicInfamyRequired()
                    ? Verdict.ACCEPTED
                    : Verdict.INFAMY_TOO_LOW;
            case NEUTRAL -> Verdict.ACCEPTED;
        };
    }
}
