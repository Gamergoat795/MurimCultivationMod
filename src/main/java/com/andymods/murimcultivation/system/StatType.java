package com.andymods.murimcultivation.system;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The four things a cultivator can pour earned stat points into.
 *
 * <p>Kept deliberately small. Enough that two players at the same realm can have made
 * genuinely different choices, few enough that the System window never turns into a
 * spreadsheet.
 *
 * <p>{@link #INSIGHT} is the interesting one: it buys cultivation *rate* rather than power,
 * so every point spent on it is a bet that the run is long enough to pay back — a real
 * decision against taking the immediate strength instead.
 */
public enum StatType implements StringRepresentable {

    /** Toughness. Each point is extra maximum health. */
    BODY("body", Attributes.MAX_HEALTH, 1.0D, AttributeModifier.Operation.ADD_VALUE),

    /** Striking power. Each point is extra attack damage. */
    FORCE("force", Attributes.ATTACK_DAMAGE, 0.25D, AttributeModifier.Operation.ADD_VALUE),

    /**
     * The breadth of the Qi network. Each point is extra maximum Qi.
     *
     * <p>Applied in {@code CultivationService.qiCapacity} rather than as an attribute, because
     * Qi capacity is a derived value of this mod's own, not a vanilla attribute.
     */
    MERIDIAN("meridian", null, 5.0D, AttributeModifier.Operation.ADD_VALUE),

    /** Understanding. Each point raises how fast cultivation accrues. */
    INSIGHT("insight", null, 0.02D, AttributeModifier.Operation.ADD_VALUE);

    public static final Codec<StatType> CODEC = StringRepresentable.fromEnum(StatType::values);

    /** Hard ceiling per stat, so a very long run cannot make one number meaningless. */
    public static final int MAX_POINTS_PER_STAT = 100;

    private final String id;
    private final Holder<Attribute> attribute;
    private final double perPoint;
    private final AttributeModifier.Operation operation;

    StatType(String id, Holder<Attribute> attribute, double perPoint, AttributeModifier.Operation operation) {
        this.id = id;
        this.attribute = attribute;
        this.perPoint = perPoint;
        this.operation = operation;
    }

    /** The vanilla attribute this stat feeds, or null when the mod derives the value itself. */
    public Holder<Attribute> attribute() {
        return attribute;
    }

    public boolean feedsAnAttribute() {
        return attribute != null;
    }

    public AttributeModifier.Operation operation() {
        return operation;
    }

    /** How much one point is worth, before any global scaling. */
    public double perPoint() {
        return perPoint;
    }

    /**
     * The total bonus from spending {@code points} here.
     *
     * <p>Pure, and takes its scaling explicitly, so the stat curve is unit testable. A formula
     * that read config inline could not be tested at all — a {@code ModConfigSpec} value throws
     * until a world has loaded it.
     */
    public double bonusFor(int points, double globalScale) {
        return clampPoints(points) * perPoint * globalScale;
    }

    public static int clampPoints(int points) {
        return Math.max(0, Math.min(MAX_POINTS_PER_STAT, points));
    }

    public String translationKey() {
        return "murimcultivation.stat." + id;
    }

    public String descriptionKey() {
        return "murimcultivation.stat." + id + ".description";
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
