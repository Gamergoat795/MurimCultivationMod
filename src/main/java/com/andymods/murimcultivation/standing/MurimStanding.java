package com.andymods.murimcultivation.standing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * How the murim sees you: your standing with each sect, your honour, and your infamy.
 *
 * <p>Grouped into one nested object for the same reason {@code SystemProgress} and
 * {@code QuestLog} were in M4 — {@code RecordCodecBuilder.group} caps at sixteen fields and
 * {@code CultivationData} was using fourteen. Folding the existing {@code sect_reputation} map in
 * alongside honour and infamy costs one field and frees the one reputation used to hold, so three
 * values now ride where one did. It also models the domain properly: these are read together
 * constantly, because they are all answers to the same question.
 *
 * <p>A mutable class rather than a record, matching {@code SystemProgress}: the parent holds this
 * in a {@code final} field and copies <em>into</em> it, so {@link #copyFrom} can clear and
 * repopulate without invalidating anyone's reference.
 *
 * <p><strong>Honour and infamy are clamped; sect reputation is deliberately not.</strong>
 * Reputation has always been allowed to go negative — you can be actively unwelcome somewhere, and
 * {@code SectRank.forReputation} handles it by returning {@code OUTSIDER} — so clamping it here
 * would be a silent behaviour change. Honour and infamy are bounded 0–100 instead, because they
 * are read as thresholds rather than as a running total, and an unbounded threshold is not one.
 */
public class MurimStanding {

    /** The floor for honour and infamy. Not for sect reputation, which may go negative. */
    public static final int MIN = 0;

    /** The ceiling for honour and infamy. */
    public static final int MAX = 100;

    private final Map<ResourceLocation, Integer> sectReputation;
    private int honour;
    private int infamy;

    public static final Codec<MurimStanding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("sect_reputation", Map.of())
                    .forGetter(standing -> Map.copyOf(standing.sectReputation)),
            Codec.INT.optionalFieldOf("honour", 0).forGetter(MurimStanding::honour),
            Codec.INT.optionalFieldOf("infamy", 0).forGetter(MurimStanding::infamy)
    ).apply(instance, MurimStanding::new));

    /** A cultivator nobody has heard of. Respected by no one, feared by no one. */
    public MurimStanding() {
        this(Map.of(), 0, 0);
    }

    public MurimStanding(Map<ResourceLocation, Integer> sectReputation, int honour, int infamy) {
        this.sectReputation = new HashMap<>(sectReputation);
        // Sanitised here rather than with a range codec, so a hand-edited save or a datapack
        // cannot produce a value the rest of the mod has to defend against.
        this.honour = clamp(honour);
        this.infamy = clamp(infamy);
    }

    /** Clamps a reputation value to the honour/infamy range. */
    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    // --- Sect reputation ---------------------------------------------------------------

    /** Standing with one sect. Zero for a sect you have never dealt with. */
    public int sectReputation(ResourceLocation sect) {
        return sectReputation.getOrDefault(sect, 0);
    }

    /** Unclamped on purpose — see the class javadoc. */
    public void addSectReputation(ResourceLocation sect, int amount) {
        sectReputation.merge(sect, amount, Integer::sum);
    }

    public Map<ResourceLocation, Integer> sectReputation() {
        return Collections.unmodifiableMap(sectReputation);
    }

    // --- Honour and infamy -------------------------------------------------------------

    /** How well the murim thinks of you. Earned by conduct, not by winning. */
    public int honour() {
        return honour;
    }

    /** How much the murim fears you. Earned by ambush and by killing those who yield. */
    public int infamy() {
        return infamy;
    }

    /**
     * Moves honour, clamped. Negative amounts lower it.
     *
     * @return the amount actually applied, which is smaller than asked for at the bounds
     */
    public int addHonour(int amount) {
        int before = honour;
        honour = clamp(honour + amount);
        return honour - before;
    }

    /** Moves infamy, clamped. Negative amounts lower it. */
    public int addInfamy(int amount) {
        int before = infamy;
        infamy = clamp(infamy + amount);
        return infamy - before;
    }

    public void setHonour(int value) {
        this.honour = clamp(value);
    }

    public void setInfamy(int value) {
        this.infamy = clamp(value);
    }

    // --- Bulk copy --------------------------------------------------------------------

    /** Copies another standing into this one in place, leaving the reference valid. */
    public void copyFrom(MurimStanding source) {
        this.sectReputation.clear();
        this.sectReputation.putAll(source.sectReputation);
        this.honour = source.honour;
        this.infamy = source.infamy;
    }
}
