package com.andymods.murimcultivation.sect;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Which side of the Murim a sect stands on.
 *
 * <p>The genre's central tension, and the reason reputation is tracked per sect rather than as
 * one number: serving the Alliance should cost you with the Demonic Cult. {@link #opposes} is
 * what a later milestone will use to apply that.
 */
public enum SectAlignment implements StringRepresentable {

    /** The orthodox sects of the Murim Alliance. */
    ORTHODOX("orthodox"),

    /** The Demonic Cult and its branches. */
    DEMONIC("demonic"),

    /** Sects that take no side, and are trusted by neither. */
    NEUTRAL("neutral");

    public static final Codec<SectAlignment> CODEC = StringRepresentable.fromEnum(SectAlignment::values);

    private final String id;

    SectAlignment(String id) {
        this.id = id;
    }

    /** Whether standing with one alignment costs standing with another. */
    public boolean opposes(SectAlignment other) {
        return (this == ORTHODOX && other == DEMONIC) || (this == DEMONIC && other == ORTHODOX);
    }

    public String translationKey() {
        return "murimcultivation.sect_alignment." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
