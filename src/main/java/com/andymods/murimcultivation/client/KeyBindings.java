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

    /**
     * One key per loadout slot. Slot 1 defaults to R; slots 2-4 ship unbound so a player
     * assigns them to keys that do not already mean something in their setup. An unbound
     * mapping is still registered and still appears in the controls screen.
     */
    public static final KeyMapping[] TECHNIQUE_SLOTS = {
            techniqueSlot(1, GLFW.GLFW_KEY_R),
            techniqueSlot(2, InputConstants.UNKNOWN.getValue()),
            techniqueSlot(3, InputConstants.UNKNOWN.getValue()),
            techniqueSlot(4, InputConstants.UNKNOWN.getValue()),
    };

    /** Opens the System window. */
    public static final KeyMapping OPEN_SYSTEM = new KeyMapping(
            "key.murimcultivation.open_system",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY);

    /** Advances the selected slot, for players who would rather use one cast key than four. */
    public static final KeyMapping CYCLE_TECHNIQUE = new KeyMapping(
            "key.murimcultivation.cycle_technique",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY);

    private static KeyMapping techniqueSlot(int slot, int defaultKey) {
        return new KeyMapping(
                "key.murimcultivation.technique_slot_" + slot,
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                defaultKey,
                CATEGORY);
    }

    /** Every mapping this mod owns. Registration iterates this so a new binding cannot be forgotten. */
    public static final KeyMapping[] ALL = buildAll();

    private static KeyMapping[] buildAll() {
        KeyMapping[] all = new KeyMapping[5 + TECHNIQUE_SLOTS.length];
        all[0] = MEDITATE;
        all[1] = BREAKTHROUGH;
        all[2] = OPEN_MERIDIAN;
        all[3] = CYCLE_TECHNIQUE;
        all[4] = OPEN_SYSTEM;
        System.arraycopy(TECHNIQUE_SLOTS, 0, all, 5, TECHNIQUE_SLOTS.length);
        return all;
    }

    private KeyBindings() {
    }
}
