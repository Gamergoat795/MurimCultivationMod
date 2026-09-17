package com.andymods.murimcultivation.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A quest, loaded from a datapack.
 *
 * <p>Quests are how the System tells a new cultivator what to do next. The story chain is the
 * closest thing this mod has to a tutorial, so its early entries deliberately ask for things a
 * player would do anyway — meditate, read a manual — and pay out enough to make the next step
 * visibly closer.
 */
public record SystemQuest(
        QuestCategory category,
        String translationKey,
        String descriptionKey,
        int requiredRealmTier,
        List<ResourceLocation> prerequisites,
        List<QuestObjective> objectives,
        List<QuestReward> rewards
) {

    public static final Codec<SystemQuest> CODEC = RecordCodecBuilder.<SystemQuest>create(instance ->
            instance.group(
                    QuestCategory.CODEC.fieldOf("category").forGetter(SystemQuest::category),
                    Codec.STRING.fieldOf("translation_key").forGetter(SystemQuest::translationKey),
                    Codec.STRING.fieldOf("description_key").forGetter(SystemQuest::descriptionKey),
                    Codec.intRange(0, 100).optionalFieldOf("required_realm_tier", 0)
                            .forGetter(SystemQuest::requiredRealmTier),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("prerequisites", List.of())
                            .forGetter(SystemQuest::prerequisites),
                    QuestObjective.CODEC.listOf().fieldOf("objectives").forGetter(SystemQuest::objectives),
                    QuestReward.CODEC.listOf().optionalFieldOf("rewards", List.of())
                            .forGetter(SystemQuest::rewards)
            ).apply(instance, SystemQuest::new)
    ).flatXmap(SystemQuest::validate, SystemQuest::validate);

    private static DataResult<SystemQuest> validate(SystemQuest quest) {
        if (quest.objectives().isEmpty()) {
            return DataResult.error(() -> "a quest must have at least one objective");
        }
        // Two objectives of the same kind and target would share a progress key and so share a
        // counter, which is never what an author means.
        long distinct = quest.objectives().stream().map(QuestObjective::progressKey).distinct().count();
        if (distinct != quest.objectives().size()) {
            return DataResult.error(() -> "a quest has two objectives with the same kind and target, "
                    + "which would share one progress counter");
        }
        if (quest.category() == QuestCategory.DAILY && !quest.prerequisites().isEmpty()) {
            return DataResult.error(() -> "a daily quest cannot have prerequisites; it resets and "
                    + "would re-lock itself");
        }
        return DataResult.success(quest);
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public Component description() {
        return Component.translatable(descriptionKey);
    }
}
