package com.andymods.murimcultivation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class KeyBindings {

    public static final String CATEGORY = "key.categories.murimcultivation";

    public static final KeyMapping MEDITATE = new KeyMapping(
            "key.murimcultivation.meditate",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);

    public static final KeyMapping BREAKTHROUGH = new KeyMapping(
            "key.murimcultivation.breakthrough",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY);

    /**
     * Opens the next sealed meridian. A stand-in for the interactive meridian grid that the
     * System window will provide; keeping it as a binding means the mechanic is playable now.
     */
    public static final KeyMapping OPEN_MERIDIAN = new KeyMapping(
            "key.murimcultivation.open_meridian",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY);

    /** Every mapping this mod owns. Registration iterates this so a new binding cannot be forgotten. */
    public static final KeyMapping[] ALL = { MEDITATE, BREAKTHROUGH, OPEN_MERIDIAN };

    private KeyBindings() {
    }
}
