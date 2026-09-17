package com.andymods.murimcultivation.technique;

import net.minecraft.server.level.ServerPlayer;

/**
 * What a technique actually does when it fires.
 *
 * <p>Behaviours are code, not data. A datapack chooses a behaviour and supplies its numbers;
 * it cannot author new behaviour. Implementations receive the resolved mastery so they can ask
 * {@link TechniquePower} and {@link TechniqueMastery} for scaled values.
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
     * @param mastery the player's mastery of this technique, 0–100
     * @return whether the technique did something
     */
    boolean execute(ServerPlayer player, Technique technique, int mastery);
}
