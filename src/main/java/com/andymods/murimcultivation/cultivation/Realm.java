package com.andymods.murimcultivation.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One rung of the Murim ladder, loaded from a datapack.
 *
 * <p>A realm owns everything that distinguishes being a Third-Rate nobody from being a
 * Flower Realm master: how much cultivation it takes to advance inside it, how much Qi the
 * body can hold, how clean a foundation the next breakthrough demands, how many meridians
 * must be open before the body can survive the transition, and what the realm makes you
 * physically capable of.
 *
 * <p>Ordering is by {@link #tier}, not by registration order, so datapacks can insert a
 * realm between two existing ones without touching either.
 */
public record Realm(
        int tier,
        String translationKey,
        String hangul,
        double progressPerSubstage,
        double qiCapacity,
        double qiRegenPerSecond,
        double purityFloor,
        int requiredOpenMeridians,
        double breakthroughBaseChance,
        int techniqueTier,
        int auraColor,
        List<AttributeGrant> attributes
) {

    /** A strictly positive double. Zero would make substage maths divide by zero. */
    private static final Codec<Double> POSITIVE_DOUBLE = Codec.doubleRange(0.001D, Double.MAX_VALUE);

    public static final Codec<Realm> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("tier").forGetter(Realm::tier),
            Codec.STRING.fieldOf("translation_key").forGetter(Realm::translationKey),
            Codec.STRING.optionalFieldOf("hangul", "").forGetter(Realm::hangul),
            POSITIVE_DOUBLE.fieldOf("progress_per_substage").forGetter(Realm::progressPerSubstage),
            POSITIVE_DOUBLE.fieldOf("qi_capacity").forGetter(Realm::qiCapacity),
            Codec.doubleRange(0.0D, 1000.0D).optionalFieldOf("qi_regen_per_second", 1.0D)
                    .forGetter(Realm::qiRegenPerSecond),
            Codec.doubleRange(0.0D, 100.0D).optionalFieldOf("purity_floor", 0.0D).forGetter(Realm::purityFloor),
            Codec.intRange(0, Meridian.count()).optionalFieldOf("required_open_meridians", 0)
                    .forGetter(Realm::requiredOpenMeridians),
            Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("breakthrough_base_chance", 1.0D)
                    .forGetter(Realm::breakthroughBaseChance),
            Codec.intRange(0, 100).optionalFieldOf("technique_tier", 0).forGetter(Realm::techniqueTier),
            Codec.INT.optionalFieldOf("aura_color", 0xFFFFFF).forGetter(Realm::auraColor),
            AttributeGrant.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(Realm::attributes)
    ).apply(instance, Realm::new));

    /**
     * Progress that must be banked to advance out of the given substage — to the next substage,
     * or, from {@link Substage#PEAK}, into the next realm.
     *
     * <p>The cost rises through the realm, so the last step inside a realm is the hardest and
     * the breakthrough is the wall it should be.
     */
    public double progressToLeave(Substage substage) {
        return progressPerSubstage * (substage.index() + 1);
    }

    /** Total progress to cross this realm end to end: the sum of all four substage steps. */
    public double totalProgressToClear() {
        double total = 0.0D;
        for (Substage substage : Substage.values()) {
            total += progressToLeave(substage);
        }
        return total;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    /**
     * The realm's name with its Hangul in tow — "Flower Realm (화경)" — for the moments
     * worth announcing. Falls back to the plain name when a datapack omits the Hangul.
     */
    public Component fullDisplayName() {
        if (hangul.isEmpty()) {
            return displayName();
        }
        return Component.translatable(translationKey).append(Component.literal(" (" + hangul + ")"));
    }
}
