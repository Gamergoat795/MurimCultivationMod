package com.andymods.murimcultivation.technique;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A martial art, loaded from a datapack.
 *
 * <p>A technique owns its gates and its numbers but not its behaviour. {@link #behaviour()}
 * names an implementation registered in {@link TechniqueBehaviours}, which is code. That is a
 * deliberate boundary: a datapack can ship "Greater Sword Qi" as the projectile behaviour with
 * bigger numbers, a higher realm gate and a longer cooldown — the genuinely useful case —
 * without the mod needing to embed a scripting language.
 *
 * <p>Ordering is by {@link #tier()}, checked against the realm's own
 * {@code technique_tier}, so a realm gates which arts a cultivator's body can channel at all.
 */
public record Technique(
        int tier,
        String translationKey,
        String hangul,
        ResourceLocation behaviour,
        double qiCost,
        int cooldownTicks,
        int requiredRealmTier,
        int requiredOpenMeridians,
        HandRequirement hand,
        double masteryGainPerUse,
        TechniquePower power
) {

    public static final Codec<Technique> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 100).fieldOf("tier").forGetter(Technique::tier),
            Codec.STRING.fieldOf("translation_key").forGetter(Technique::translationKey),
            Codec.STRING.optionalFieldOf("hangul", "").forGetter(Technique::hangul),
            ResourceLocation.CODEC.fieldOf("behaviour").forGetter(Technique::behaviour),
            Codec.doubleRange(0.0D, 100000.0D).optionalFieldOf("qi_cost", 0.0D).forGetter(Technique::qiCost),
            Codec.intRange(0, 72000).optionalFieldOf("cooldown_ticks", 0).forGetter(Technique::cooldownTicks),
            Codec.intRange(0, 100).optionalFieldOf("required_realm_tier", 1)
                    .forGetter(Technique::requiredRealmTier),
            Codec.intRange(0, 20).optionalFieldOf("required_open_meridians", 0)
                    .forGetter(Technique::requiredOpenMeridians),
            HandRequirement.CODEC.optionalFieldOf("hand", HandRequirement.ANY).forGetter(Technique::hand),
            Codec.doubleRange(0.0D, 100.0D).optionalFieldOf("mastery_gain_per_use", 1.0D)
                    .forGetter(Technique::masteryGainPerUse),
            TechniquePower.CODEC.optionalFieldOf("power", TechniquePower.NONE).forGetter(Technique::power)
    ).apply(instance, Technique::new));

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    /** The art's name with its Hangul — "Sword Qi (검기)" — for messages worth the flourish. */
    public Component fullDisplayName() {
        if (hangul.isEmpty()) {
            return displayName();
        }
        return Component.translatable(translationKey).append(Component.literal(" (" + hangul + ")"));
    }
}
