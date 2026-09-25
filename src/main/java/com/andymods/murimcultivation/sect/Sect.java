package com.andymods.murimcultivation.sect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A martial sect, loaded from a datapack.
 *
 * <p>Identity, alignment, a joining requirement, which arts it teaches at which rank, and
 * where its members are found: {@code territory} is a biome tag, and martial artists spawning
 * in a biome it covers belong to this sect, at a realm rolled from {@code realm_range}. Both are
 * optional, so a datapack sect with no territory still exists and can be joined — it simply
 * has no one wandering the world for it.
 */
public record Sect(
        String translationKey,
        String hangul,
        SectAlignment alignment,
        int color,
        int requiredRealmTier,
        Map<SectRank, List<ResourceLocation>> teachings,
        Optional<TagKey<Biome>> territory,
        RealmRange realmRange
) {

    /** The realm tiers this sect's wandering members are drawn from, inclusive at both ends. */
    public record RealmRange(int min, int max) {

        public static final RealmRange DEFAULT = new RealmRange(1, 3);

        public static final Codec<RealmRange> CODEC = RecordCodecBuilder.<RealmRange>create(
                instance -> instance.group(
                        Codec.intRange(1, 100).fieldOf("min").forGetter(RealmRange::min),
                        Codec.intRange(1, 100).fieldOf("max").forGetter(RealmRange::max)
                ).apply(instance, RealmRange::new)
        ).flatXmap(RealmRange::validate, RealmRange::validate);

        private static DataResult<RealmRange> validate(RealmRange range) {
            return range.min() <= range.max()
                    ? DataResult.success(range)
                    : DataResult.error(() -> "realm_range min " + range.min() + " is above max " + range.max());
        }
    }

    public static final Codec<Sect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("translation_key").forGetter(Sect::translationKey),
            Codec.STRING.optionalFieldOf("hangul", "").forGetter(Sect::hangul),
            SectAlignment.CODEC.optionalFieldOf("alignment", SectAlignment.NEUTRAL)
                    .forGetter(Sect::alignment),
            Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(Sect::color),
            Codec.intRange(0, 100).optionalFieldOf("required_realm_tier", 1)
                    .forGetter(Sect::requiredRealmTier),
            Codec.unboundedMap(SectRank.CODEC, ResourceLocation.CODEC.listOf())
                    .optionalFieldOf("teachings", Map.of()).forGetter(Sect::teachings),
            TagKey.hashedCodec(Registries.BIOME).optionalFieldOf("territory").forGetter(Sect::territory),
            RealmRange.CODEC.optionalFieldOf("realm_range", RealmRange.DEFAULT).forGetter(Sect::realmRange)
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

    /** Whether this sect's members are found in the given biome. */
    public boolean claims(Holder<Biome> biome) {
        return territory.map(tag -> biome.is(tag)).orElse(false);
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
