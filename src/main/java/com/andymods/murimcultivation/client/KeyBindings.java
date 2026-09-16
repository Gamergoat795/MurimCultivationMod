package com.andymods.murimcultivation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String KEY_CATEGORY_MURIM = "key.category.murimcultivation.murim";
    public static final String KEY_MEDITATE = "key.murimcultivation.meditate";
    public static final String KEY_BREAKTHROUGH = "key.murimcultivation.breakthrough";

    public static final KeyMapping MEDITATE_KEY = new KeyMapping(
            KEY_MEDITATE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            KEY_CATEGORY_MURIM
    );

    public static final KeyMapping BREAKTHROUGH_KEY = new KeyMapping(
            KEY_BREAKTHROUGH,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            KEY_CATEGORY_MURIM
    );
}