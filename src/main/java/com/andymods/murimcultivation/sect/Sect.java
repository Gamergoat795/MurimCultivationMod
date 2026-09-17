package com.andymods.murimcultivation.sect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * A martial sect, loaded from a datapack.
 *
 * <p><strong>A skeleton, deliberately.</strong> Everything a sect needs to exist and be joined
 * is here — identity, alignment, a joining requirement, and which arts it teaches at which rank.
 * What is not here is anyone to talk to, quests of its own, or territory. Those are the next
 * milestone's work; this establishes the shape so that milestone is additive rather than a
 * redesign.
 */
public record Sect(
        String translationKey,
        String hangul,
        SectAlignment alignment,
        int color,
        int requiredRealmTier,
        Map<SectRank, List<ResourceLocation>> teachings
) {

    public static final Codec<Sect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("translation_key").forGetter(Sect::translationKey),
            Codec.STRING.optionalFieldOf("hangul", "").forGetter(Sect::hangul),
            SectAlignment.CODEC.optionalFieldOf("alignment", SectAlignment.NEUTRAL)
                    .forGetter(Sect::alignment),
            Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(Sect::color),
            Codec.intRange(0, 100).optionalFieldOf("required_realm_tier", 1)
                    .forGetter(Sect::requiredRealmTier),
            Codec.unboundedMap(SectRank.CODEC, ResourceLocation.CODEC.listOf())
                    .optionalFieldOf("teachings", Map.of()).forGetter(Sect::teachings)
    ).apply(instance, Sect::new));

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public Component fullDisplayName() {
        if (hangul.isEmpty()) {
            return displayName();
        }
        return Component.translatable(translationKey).append(Component.literal(" (" + hangul + ")"));
    }

    /** The arts this sect will teach at the given rank, cumulative from the bottom up. */
    public List<ResourceLocation> teachingsUpTo(SectRank rank) {
        return teachings.entrySet().stream()
                .filter(entry -> entry.getKey().ordinal() <= rank.ordinal())
                .flatMap(entry -> entry.getValue().stream())
                .distinct()
                .toList();
    }
}
