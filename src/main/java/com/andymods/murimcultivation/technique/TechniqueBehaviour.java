package com.andymods.murimcultivation.technique;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a technique actually does when it fires.
 *
 * <p>Behaviours are code, not data. A datapack chooses a behaviour and supplies its numbers;
 * it cannot author new behaviour. Implementations receive the resolved mastery so they can ask
 * {@link TechniquePower} and {@link TechniqueMastery} for scaled values.
 *
 * <p>The technique's own id is passed in because sustained techniques key their buff state and
 * attribute modifiers by it. Deriving it from the technique's fields instead would be a guess
 * that breaks the moment a datapack names something unconventionally.
 *
 * <p>Returning {@code false} means the technique could not do anything useful — no target in
 * range, nothing to heal. {@link TechniqueService} then refunds the Qi and clears the cooldown,
 * so a whiffed cast costs the player nothing but the moment.
 */
@FunctionalInterface
public interface TechniqueBehaviour {

    /**
     * Fires the technique. Called server-side only, after every gate has passed and the Qi
     * has been deducted.
     *
     * <p>The caster is a {@link LivingEntity} so sect martial artists fight with the very same
     * arts players learn, rather than look-alikes that drift out of step with the datapack.
     * Only the arts in {@link TechniqueBehaviours#npcUsable} are ever offered to a non-player;
     * the rest keep state only a player carries and return {@code false} for anyone else.
     *
     * @param caster  who is casting: a player, or a martial artist
     * @param id      the technique's registry id
     * @param mastery the caster's mastery of this technique, 0–100
     * @return whether the technique did something
     */
    boolean execute(LivingEntity caster, ResourceLocation id, Technique technique, int mastery);
}
