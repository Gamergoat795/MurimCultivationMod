package com.andymods.murimcultivation.technique;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Every numeric knob a technique behaviour might read, in one record.
 *
 * <p>A free-form {@code Map<String, Double>} would be more flexible and much worse: no
 * validation, no defaults, and a typo in a datapack becomes a silent zero at runtime instead
 * of a load-time error. Each behaviour reads only the fields it cares about, and a datapack
 * author gets told when they misspell one.
 *
 * <p>Damage scaling with mastery lives here, per technique, rather than in config: how steeply
 * a particular art rewards practice is part of that art's identity. The global mastery
 * economy — what mastery does to Qi cost and cooldown — is config, in
 * {@link TechniqueMastery.Tuning}.
 */
public record TechniquePower(
        double baseDamage,
        double damagePerMastery,
        double range,
        double radius,
        int durationTicks,
        int amplifier,
        double knockback,
        double healAmount,
        double speedMultiplier,
        double qiPerTick
) {

    /** All-zero defaults; a behaviour that needs a field expects its JSON to set it. */
    public static final TechniquePower NONE =
            new TechniquePower(0.0D, 0.0D, 0.0D, 0.0D, 0, 0, 0.0D, 0.0D, 1.0D, 0.0D);

    public static final Codec<TechniquePower> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("base_damage", 0.0D).forGetter(TechniquePower::baseDamage),
            Codec.DOUBLE.optionalFieldOf("damage_per_mastery", 0.0D).forGetter(TechniquePower::damagePerMastery),
            Codec.doubleRange(0.0D, 256.0D).optionalFieldOf("range", 0.0D).forGetter(TechniquePower::range),
            Codec.doubleRange(0.0D, 256.0D).optionalFieldOf("radius", 0.0D).forGetter(TechniquePower::radius),
            Codec.intRange(0, 72000).optionalFieldOf("duration_ticks", 0).forGetter(TechniquePower::durationTicks),
            Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(TechniquePower::amplifier),
            Codec.doubleRange(0.0D, 100.0D).optionalFieldOf("knockback", 0.0D).forGetter(TechniquePower::knockback),
            Codec.doubleRange(0.0D, 1024.0D).optionalFieldOf("heal_amount", 0.0D)
                    .forGetter(TechniquePower::healAmount),
            Codec.doubleRange(0.0D, 100.0D).optionalFieldOf("speed_multiplier", 1.0D)
                    .forGetter(TechniquePower::speedMultiplier),
            Codec.doubleRange(0.0D, 1000.0D).optionalFieldOf("qi_per_tick", 0.0D)
                    .forGetter(TechniquePower::qiPerTick)
    ).apply(instance, TechniquePower::new));

    /**
     * Damage at a given mastery. Practising an art makes it hit harder, at a rate the art
     * itself declares.
     */
    public double damageAt(int mastery) {
        return baseDamage + damagePerMastery * mastery;
    }
}
