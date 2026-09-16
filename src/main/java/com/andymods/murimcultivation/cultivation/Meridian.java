package com.andymods.murimcultivation.cultivation;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.List;

/**
 * The twenty nodes of the cultivator's Qi network: the twelve primary meridians and the
 * eight extraordinary vessels. Opening a node is a deliberate, risky act that permanently
 * widens what the body can hold and channel — and the higher realms will not admit a
 * cultivator whose network is still mostly sealed.
 */
public enum Meridian implements StringRepresentable {
    // The twelve primary meridians.
    LUNG("lung", Kind.PRIMARY),
    LARGE_INTESTINE("large_intestine", Kind.PRIMARY),
    STOMACH("stomach", Kind.PRIMARY),
    SPLEEN("spleen", Kind.PRIMARY),
    HEART("heart", Kind.PRIMARY),
    SMALL_INTESTINE("small_intestine", Kind.PRIMARY),
    BLADDER("bladder", Kind.PRIMARY),
    KIDNEY("kidney", Kind.PRIMARY),
    PERICARDIUM("pericardium", Kind.PRIMARY),
    TRIPLE_BURNER("triple_burner", Kind.PRIMARY),
    GALLBLADDER("gallbladder", Kind.PRIMARY),
    LIVER("liver", Kind.PRIMARY),

    // The eight extraordinary vessels. Harder to open, and worth more.
    CONCEPTION_VESSEL("conception_vessel", Kind.EXTRAORDINARY),
    GOVERNING_VESSEL("governing_vessel", Kind.EXTRAORDINARY),
    GIRDLING_VESSEL("girdling_vessel", Kind.EXTRAORDINARY),
    THRUSTING_VESSEL("thrusting_vessel", Kind.EXTRAORDINARY),
    YIN_HEEL_VESSEL("yin_heel_vessel", Kind.EXTRAORDINARY),
    YANG_HEEL_VESSEL("yang_heel_vessel", Kind.EXTRAORDINARY),
    YIN_LINKING_VESSEL("yin_linking_vessel", Kind.EXTRAORDINARY),
    YANG_LINKING_VESSEL("yang_linking_vessel", Kind.EXTRAORDINARY);

    public enum Kind {
        PRIMARY,
        EXTRAORDINARY
    }

    public static final Codec<Meridian> CODEC = StringRepresentable.fromEnum(Meridian::values);

    private static final Meridian[] BY_ORDINAL = values();
    public static final List<Meridian> PRIMARY_MERIDIANS =
            List.of(LUNG, LARGE_INTESTINE, STOMACH, SPLEEN, HEART, SMALL_INTESTINE,
                    BLADDER, KIDNEY, PERICARDIUM, TRIPLE_BURNER, GALLBLADDER, LIVER);
    public static final List<Meridian> EXTRAORDINARY_VESSELS =
            List.of(CONCEPTION_VESSEL, GOVERNING_VESSEL, GIRDLING_VESSEL, THRUSTING_VESSEL,
                    YIN_HEEL_VESSEL, YANG_HEEL_VESSEL, YIN_LINKING_VESSEL, YANG_LINKING_VESSEL);

    private final String id;
    private final Kind kind;

    Meridian(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isExtraordinary() {
        return kind == Kind.EXTRAORDINARY;
    }

    public String translationKey() {
        return "murimcultivation.meridian." + id;
    }

    /**
     * Relative cost to open this node. Extraordinary vessels are meaningfully harder,
     * which is what makes them feel like an achievement rather than a checklist item.
     */
    public double openingCostMultiplier() {
        return isExtraordinary() ? 2.5D : 1.0D;
    }

    public static int count() {
        return BY_ORDINAL.length;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
