package com.andymods.murimcultivation.technique;

import com.mojang.serialization.Codec;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a technique needs in the cultivator's hands.
 *
 * <p>This is what separates a swordsman from a palm artist. Sword arts demand a blade; palm
 * arts demand hands unencumbered by one. Two players at the same realm with the same Qi
 * should still fight differently depending on what they trained and what they are holding.
 */
public enum HandRequirement implements StringRepresentable {

    /** Footwork and internal arts: usable whatever you are carrying. */
    ANY("any"),

    /** Sword arts. Requires a sword in the main hand. */
    SWORD("sword"),

    /**
     * Palm and fist arts. Requires a main hand not holding a weapon.
     *
     * <p>Deliberately permissive about non-weapons: a cultivator holding a pill or a manual
     * can still throw a palm strike. Only an actual weapon gets in the way.
     */
    FREE_HAND("free_hand");

    public static final Codec<HandRequirement> CODEC = StringRepresentable.fromEnum(HandRequirement::values);

    private final String id;

    HandRequirement(String id) {
        this.id = id;
    }

    /** Whether the player's current grip satisfies this requirement. */
    public boolean isSatisfiedBy(Player player) {
        ItemStack held = player.getMainHandItem();
        return switch (this) {
            case ANY -> true;
            case SWORD -> held.is(ItemTags.SWORDS);
            case FREE_HAND -> !isWeapon(held);
        };
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES);
    }

    /** The message shown when a cast is refused for the wrong grip. */
    public String refusalTranslationKey() {
        return "murimcultivation.technique.refused.hand." + id;
    }

    public String translationKey() {
        return "murimcultivation.hand_requirement." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
