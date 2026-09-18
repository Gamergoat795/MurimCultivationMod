package com.andymods.murimcultivation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
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
     * Casts whatever slot the cycle key has selected. Together with {@link #CYCLE_TECHNIQUE} this
     * is the default way to use every art you have bound, and it is the only technique key that
     * ships with a key on it.
     *
     * <p>G rather than the more ergonomic R because Essential (essential.gg) puts its emote wheel
     * on R. Minecraft lets both mappings fire on a shared key, so the collision is not cosmetic:
     * you would emote and cast at the same time. V was the other candidate and is worse — Simple
     * Voice Chat has it. Anyone who runs neither can rebind to R in the controls screen.
     */
    public static final KeyMapping USE_SELECTED_TECHNIQUE = new KeyMapping(
            "key.murimcultivation.use_selected_technique",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    /**
     * One key per loadout slot, for players who would rather reach an art directly than cycle to
     * it. All four ship unbound: they are shortcuts over the select-and-cast pair above, not the
     * only way in, so claiming four keys by default would be taking keys from other mods to
     * duplicate something that already works. An unbound mapping is still registered and still
     * appears in the controls screen.
     *
     * <p>Slot 1 used to default to G, which made it the only reachable slot — and since
     * {@code learnall} bound arts in registry order, that one reachable slot was as likely as not
     * to hold something gated far above the player's realm. Four uniformly optional shortcuts
     * behind one working cast key is the honest arrangement.
     */
    public static final KeyMapping[] TECHNIQUE_SLOTS = {
            techniqueSlot(1, InputConstants.UNKNOWN.getValue()),
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

    /**
     * A mapping's current key in square brackets, or empty if it has none.
     *
     * <p>Reads the live mapping rather than the default, because a player who has rebound a key
     * needs to be told the key they actually have. This is also why the server never names a key
     * in its own messages: keybinds are client state, so only the client can say what they are.
     */
    public static Component keyLabel(KeyMapping mapping) {
        if (mapping.isUnbound()) {
            return Component.empty();
        }
        return Component.literal("[").append(mapping.getTranslatedKeyMessage()).append("]");
    }

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
                        Stream.of(MEDITATE, BREAKTHROUGH, OPEN_MERIDIAN, USE_SELECTED_TECHNIQUE,
                                CYCLE_TECHNIQUE, OPEN_SYSTEM),
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
