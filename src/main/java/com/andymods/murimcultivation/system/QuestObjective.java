package com.andymods.murimcultivation.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One thing a quest asks for.
 *
 * <p>Modelled as a single tagged record rather than a {@code Codec.dispatch} hierarchy. The
 * dispatch approach would be more extensible in principle, but every objective this mod has is
 * "a kind, an amount, and optionally a target", so a class per kind would be nine near-empty
 * classes and a registry to look them up in. {@code TechniquePower} made the same call for the
 * same reason and it has held up well.
 *
 * <p>The codec validates that kinds needing a target have one, so a malformed quest fails at
 * datapack load with a clear message instead of sitting inert until a player picks it up.
 */
public record QuestObjective(ObjectiveKind kind, int amount, Optional<ResourceLocation> target) {

    public static final Codec<QuestObjective> CODEC = RecordCodecBuilder.<QuestObjective>create(instance ->
            instance.group(
                    ObjectiveKind.CODEC.fieldOf("kind").forGetter(QuestObjective::kind),
                    Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("amount", 1)
                            .forGetter(QuestObjective::amount),
                    ResourceLocation.CODEC.optionalFieldOf("target").forGetter(QuestObjective::target)
            ).apply(instance, QuestObjective::new)
    ).flatXmap(QuestObjective::validate, QuestObjective::validate);

    private static DataResult<QuestObjective> validate(QuestObjective objective) {
        if (objective.kind().needsTarget() && objective.target().isEmpty()) {
            return DataResult.error(() -> "objective kind '" + objective.kind().getSerializedName()
                    + "' requires a \"target\"");
        }
        if (!objective.kind().needsTarget() && objective.target().isPresent()) {
            return DataResult.error(() -> "objective kind '" + objective.kind().getSerializedName()
                    + "' takes no \"target\", but one was given: " + objective.target().get());
        }
        return DataResult.success(objective);
    }

    /**
     * A stable key for storing progress against this objective within a quest, so reordering a
     * quest's objectives in a datapack does not silently reassign a player's counters.
     */
    public String progressKey() {
        return kind().getSerializedName() + "/" + target().map(ResourceLocation::toString).orElse("-");
    }

    /** The one-line description shown in the quest list. */
    public Component description() {
        return target()
                .map(id -> Component.translatable(kind().translationKey(), amount(),
                        Component.translatable("murimcultivation.technique." + id.getPath())))
                .orElseGet(() -> Component.translatable(kind().translationKey(), amount()));
    }
}
