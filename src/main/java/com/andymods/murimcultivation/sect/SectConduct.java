package com.andymods.murimcultivation.sect;

import com.andymods.murimcultivation.config.MurimConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the sects react when you beat one of their people — and, more to the point, what you did
 * after.
 *
 * <p>The table, as designed by the mod's author:
 *
 * <table>
 *   <tr><th></th><th>Their sect</th><th>Other sects</th><th>Other demonic sects</th></tr>
 *   <tr><td>Duel, spared</td><td>+small</td><td>—</td><td>—</td></tr>
 *   <tr><td>Duel, killed after yielding</td><td>−small</td><td>+small</td><td>+small-medium</td></tr>
 *   <tr><td>Ambush, killed</td><td>−large</td><td>−minimal</td><td>+medium</td></tr>
 *   <tr><td>Ambush, spared</td><td>—</td><td>—</td><td>—</td></tr>
 * </table>
 *
 * <p>Read it as politics rather than morality. Honour and infamy are the moral ledger and move
 * separately, in {@code StandingService}. This is about interest: killing a sect's member after a
 * fair fight weakens a rival, so the other sects quietly approve and the Demonic Cult approves
 * openly. Murdering one from ambush is something the orthodox world cannot be seen to tolerate, so
 * everyone but the Cult distances themselves a little. Sparing someone you beat fairly earns their
 * sect's respect and nobody else's notice; sparing someone you jumped earns nothing, because
 * nobody thanks you for stopping.
 *
 * <p>Only sect members move anything. An unaffiliated wanderer has no one to answer to.
 *
 * <p>Pure, with a {@link Tuning}, so the whole table is testable without a world.
 */
public final class SectConduct {

    private SectConduct() {
    }

    /** How a fight with a sect member ended. */
    public enum Outcome implements StringRepresentable {
        DUEL_SPARED("duel_spared"),
        DUEL_KILLED("duel_killed"),
        AMBUSH_SPARED("ambush_spared"),
        AMBUSH_KILLED("ambush_killed");

        private final String id;

        Outcome(String id) {
            this.id = id;
        }

        public static Outcome of(boolean fairFight, boolean killed) {
            if (fairFight) {
                return killed ? DUEL_KILLED : DUEL_SPARED;
            }
            return killed ? AMBUSH_KILLED : AMBUSH_SPARED;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /** The five magnitudes the table is written in. */
    public record Tuning(int minimal, int small, int smallMedium, int medium, int large) {

        public static Tuning fromConfig() {
            return new Tuning(
                    MurimConfig.conductMinimal(),
                    MurimConfig.conductSmall(),
                    MurimConfig.conductSmallMedium(),
                    MurimConfig.conductMedium(),
                    MurimConfig.conductLarge());
        }
    }

    /** A sect as the table needs to see it: who it is and which side it is on. */
    public record SectEntry(ResourceLocation id, SectAlignment alignment) {
    }

    /**
     * The standing change for every sect, keyed by sect id, zero changes omitted.
     *
     * <p>The victim's own sect always gets the "their sect" column, even when it is demonic: the
     * Cult does not celebrate the murder of its own. The demonic column applies to demonic sects
     * <em>other than</em> the victim's.
     */
    public static Map<ResourceLocation, Integer> deltas(Outcome outcome, ResourceLocation victimSect,
                                                        List<SectEntry> sects, Tuning tuning) {
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
        put(result, victimSect, switch (outcome) {
            case DUEL_SPARED -> tuning.small();
            case DUEL_KILLED -> -tuning.small();
            case AMBUSH_KILLED -> -tuning.large();
            case AMBUSH_SPARED -> 0;
        });

        for (SectEntry sect : sects) {
            if (sect.id().equals(victimSect)) {
                continue;
            }
            boolean demonic = sect.alignment() == SectAlignment.DEMONIC;
            put(result, sect.id(), switch (outcome) {
                case DUEL_KILLED -> demonic ? tuning.smallMedium() : tuning.small();
                case AMBUSH_KILLED -> demonic ? tuning.medium() : -tuning.minimal();
                case DUEL_SPARED, AMBUSH_SPARED -> 0;
            });
        }
        return result;
    }

    private static void put(Map<ResourceLocation, Integer> result, ResourceLocation id, int amount) {
        if (amount != 0) {
            result.put(id, amount);
        }
    }
}
