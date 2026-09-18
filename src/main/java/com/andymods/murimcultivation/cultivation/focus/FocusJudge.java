package com.andymods.murimcultivation.cultivation.focus;

import com.andymods.murimcultivation.config.MurimConfig;

/**
 * Decides whether a player answered a breath-rhythm prompt in time.
 *
 * <p><strong>Pure on purpose.</strong> Every input is a parameter, so the whole judgement is unit
 * testable — which matters more here than anywhere else in the mod, because this is the one piece
 * of logic a cheating client has an incentive to probe. The {@code Tuning} record follows the
 * pattern established by {@code BreakthroughService.Tuning}: a {@code ModConfigSpec} value throws
 * until a world has loaded it, so a formula that read config inline could not be tested at all.
 *
 * <p><strong>What this can and cannot prevent.</strong> It removes the reward for leaving a
 * computer running, which is the point, and it defeats a naive autoclicker because the moment and
 * the window are both server-chosen from {@code player.getRandom()} — unsynced, so unpredictable.
 * It cannot stop a purpose-built client that reads the prompt packet and replies correctly; no
 * minigame rendered on a Minecraft client can. {@link Verdict#INCONSISTENT} raises the cost of
 * that cheat from trivial to deliberate, which is the honest ceiling.
 */
public final class FocusJudge {

    private FocusJudge() {
    }

    /** The outcome of one answered prompt. */
    public enum Verdict {
        /** Dead centre of the window. Restores focus fully and pays a small bonus. */
        PERFECT(true),
        /** Inside the window. Restores some focus. */
        HIT(true),
        /** Outside the window, or no answer before the sweep ended. */
        MISS(false),
        /**
         * The client's claimed marker position disagrees with how long the server actually waited.
         * Treated as a miss. This is what stops a client reporting a perfect position every time,
         * while a player on a bad connection still passes — the tolerance is generous enough to
         * cover real latency and far too tight to cover a fabricated answer.
         */
        INCONSISTENT(false),
        /**
         * Answered faster than a human can react. A key held down or a macro firing on the packet
         * lands here rather than being rewarded for superhuman reflexes.
         */
        TOO_FAST(false);

        private final boolean success;

        Verdict(boolean success) {
            this.success = success;
        }

        public boolean success() {
            return success;
        }
    }

    /**
     * The numbers behind a judgement.
     *
     * @param minReactionTicks  answers sooner than this are {@link Verdict#TOO_FAST}
     * @param latencyTolerance  how far, as a fraction of the sweep, the client's claimed position
     *                          may differ from the server's own measurement
     * @param perfectFraction   the middle share of the window that counts as {@link Verdict#PERFECT}
     * @param hitGain           focus restored by a {@link Verdict#HIT}
     * @param missPenalty       focus lost by a {@link Verdict#MISS}
     */
    public record Tuning(int minReactionTicks, double latencyTolerance, double perfectFraction,
                         double hitGain, double missPenalty) {

        public static Tuning fromConfig() {
            return new Tuning(
                    MurimConfig.focusMinReactionTicks(),
                    MurimConfig.focusLatencyTolerance(),
                    MurimConfig.focusPerfectFraction(),
                    MurimConfig.focusHitGain(),
                    MurimConfig.focusMissPenalty());
        }
    }

    /**
     * Judges an answer.
     *
     * @param clientPosition    where the client says its marker was, 0..1 across the sweep
     * @param serverElapsedTicks how long the server actually waited for the answer
     * @param prompt            the challenge as the server issued it
     * @param tuning            the numbers
     */
    public static Verdict judge(double clientPosition, int serverElapsedTicks,
                                FocusPrompt prompt, Tuning tuning) {
        if (serverElapsedTicks < tuning.minReactionTicks()) {
            return Verdict.TOO_FAST;
        }

        // Where the server thinks the marker was when the answer arrived. Beyond the end of the
        // sweep this keeps growing, which is correct: a late answer should read as late, not wrap.
        double serverPosition = prompt.sweepTicks() <= 0
                ? 1.0D
                : serverElapsedTicks / (double) prompt.sweepTicks();

        if (Math.abs(clientPosition - serverPosition) > tuning.latencyTolerance()) {
            return Verdict.INCONSISTENT;
        }
        if (clientPosition < prompt.windowStart() || clientPosition > prompt.windowEnd()) {
            return Verdict.MISS;
        }

        double centre = (prompt.windowStart() + prompt.windowEnd()) / 2.0D;
        double halfPerfect = prompt.windowWidth() * tuning.perfectFraction() / 2.0D;
        return Math.abs(clientPosition - centre) <= halfPerfect ? Verdict.PERFECT : Verdict.HIT;
    }

    /**
     * The focus a player should hold after a verdict.
     *
     * <p>Kept separate from {@link #judge} so the arithmetic can be tested on its own, and so the
     * clamping lives in exactly one place. Focus is never pushed below zero or above one, and a
     * miss never touches earned progress — an absent player stops gaining rather than losing
     * anything, which is the whole design.
     */
    public static double applyVerdict(double focus, Verdict verdict, Tuning tuning) {
        double next = switch (verdict) {
            case PERFECT -> 1.0D;
            case HIT -> focus + tuning.hitGain();
            // A fabricated or superhuman answer costs exactly what ignoring the prompt costs.
            // Deliberately not harsher: the check has false positives on a bad connection, and
            // punishing a laggy honest player would be worse than under-punishing a cheat.
            case MISS, INCONSISTENT, TOO_FAST -> focus - tuning.missPenalty();
        };
        return Math.max(0.0D, Math.min(1.0D, next));
    }
}
