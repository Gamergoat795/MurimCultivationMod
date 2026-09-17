package com.andymods.murimcultivation.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One thing a quest pays out. Same tagged-record shape as {@link QuestObjective}, and validated
 * the same way so a reward naming nothing fails at load rather than silently paying nothing.
 */
public record QuestReward(RewardKind kind, double amount, Optional<ResourceLocation> target) {

    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.<QuestReward>create(instance ->
            instance.group(
                    RewardKind.CODEC.fieldOf("kind").forGetter(QuestReward::kind),
                    Codec.doubleRange(0.0D, 1.0e9D).optionalFieldOf("amount", 1.0D)
                            .forGetter(QuestReward::amount),
                    ResourceLocation.CODEC.optionalFieldOf("target").forGetter(QuestReward::target)
            ).apply(instance, QuestReward::new)
    ).flatXmap(QuestReward::validate, QuestReward::validate);

    private static DataResult<QuestReward> validate(QuestReward reward) {
        if (reward.kind().needsTarget() && reward.target().isEmpty()) {
            return DataResult.error(() -> "reward kind '" + reward.kind().getSerializedName()
                    + "' requires a \"target\"");
        }
        if (!reward.kind().needsTarget() && reward.target().isPresent()) {
            return DataResult.error(() -> "reward kind '" + reward.kind().getSerializedName()
                    + "' takes no \"target\", but one was given: " + reward.target().get());
        }
        return DataResult.success(reward);
    }

    public Component description() {
        return target()
                .map(id -> Component.translatable(kind().translationKey(),
                        (int) amount(), Component.literal(id.getPath())))
                .orElseGet(() -> Component.translatable(kind().translationKey(), (int) amount()));
    }
}
