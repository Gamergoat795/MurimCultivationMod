package com.andymods.murimcultivation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.stream.Stream;

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
     * One key per loadout slot. Slots 2-4 ship unbound so a player assigns them to keys that do
     * not already mean something in their setup. An unbound mapping is still registered and still
     * appears in the controls screen.
     *
     * <p>Slot 1 defaults to G rather than the more ergonomic R because Essential (essential.gg)
     * puts its emote wheel on R. Minecraft lets both mappings fire on a shared key, so the
     * collision is not cosmetic: you would emote and cast at the same time. V was the other
     * candidate and is worse — Simple Voice Chat has it. Anyone who does not run either mod can
     * rebind to R in the controls screen.
     */
    public static final KeyMapping[] TECHNIQUE_SLOTS = {
            techniqueSlot(1, GLFW.GLFW_KEY_G),
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

    /**
     * Every mapping this mod owns. Registration iterates this so a new binding cannot be forgotten.
     *
     * <p>This declaration must stay below every field {@link #buildAll()} reads. Java runs static
     * initialisers in textual order, so a mapping declared after this one would be read as null
     * here, land in the array as null, and never register — the key would silently do nothing. The
     * order of the declarations above is load-bearing, not stylistic.
     */
    public static final KeyMapping[] ALL = buildAll();

    /**
     * Concatenates the individual mappings with {@link #TECHNIQUE_SLOTS}. Adding a binding means
     * naming it in the stream below and nowhere else: no count is written by hand, so there is no
     * array size or copy offset to forget to update.
     */
    private static KeyMapping[] buildAll() {
        KeyMapping[] all = Stream.concat(
                        Stream.of(MEDITATE, BREAKTHROUGH, OPEN_MERIDIAN, CYCLE_TECHNIQUE, OPEN_SYSTEM),
                        Arrays.stream(TECHNIQUE_SLOTS))
                .toArray(KeyMapping[]::new);
        // Catches the one hazard the stream cannot: a mapping declared below ALL reads as null
        // here. Better to say so at class initialisation than to NPE in ClientSetup.
        for (KeyMapping mapping : all) {
            if (mapping == null) {
                throw new IllegalStateException(
                        "A mapping listed in KeyBindings.buildAll() is declared after ALL, so it read as null");
            }
        }
        return all;
    }

    private KeyBindings() {
    }
}
