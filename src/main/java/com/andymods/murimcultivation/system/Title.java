package com.andymods.murimcultivation.system;

import com.andymods.murimcultivation.cultivation.AttributeGrant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * An earned title — "Sword Saint", "Demon Slayer" — that can be worn for a passive bonus.
 *
 * <p>Reuses {@link AttributeGrant} from the realm system rather than inventing a second way to
 * say "+2 attack damage", so a datapack author who has written a realm already knows how to
 * write a title.
 */
public record Title(String translationKey, String hangul, int color, List<AttributeGrant> attributes) {

    public static final Codec<Title> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("translation_key").forGetter(Title::translationKey),
            Codec.STRING.optionalFieldOf("hangul", "").forGetter(Title::hangul),
            Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(Title::color),
            AttributeGrant.CODEC.listOf().optionalFieldOf("attributes", List.of())
                    .forGetter(Title::attributes)
    ).apply(instance, Title::new));

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public Component fullDisplayName() {
        if (hangul.isEmpty()) {
            return displayName();
        }
        return Component.translatable(translationKey).append(Component.literal(" (" + hangul + ")"));
    }
}
