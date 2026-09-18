package com.andymods.murimcultivation.event;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.technique.TechniqueBuffs;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Keeps derived state correct across the three moments it would otherwise be lost: logging in,
 * respawning, and changing dimension.
 *
 * <p>Realm attribute modifiers are deliberately <em>transient</em> — they are never written to
 * the player's NBT, so they cannot outlive the mod being removed and cannot stack up across
 * saves. The cost of that choice is that they must be re-applied at each of these moments,
 * which is what this class exists to guarantee. Respawn is the one that matters most: a
 * respawned player is a fresh entity with fresh attributes, so without this every cultivation
 * bonus silently disappears on death until the next relog.
 */
@EventBusSubscriber(modid = MurimCultivationMod.MODID)
public final class PlayerLifecycleEvents {

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Meditation is transient state; a player cannot log in mid-meditation.
            CultivationService.data(player).setMeditating(false);
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CultivationService.data(player).setMeditating(false);
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    /**
     * Carries cultivation across the old player entity to the new one.
     *
     * <p>The attachment is declared {@code copyOnDeath()}, which covers dying. This copies
     * unconditionally anyway so that the non-death clone — returning from the End — is covered
     * by the same code path rather than relying on it being handled elsewhere. Copying data
     * that was already copied is harmless; missing the case is silent data loss.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CultivationData original = CultivationService.data(event.getOriginal());
        CultivationData clone = CultivationService.data(event.getEntity());
        clone.copyFrom(original);
        clone.setMeditating(false);
        clone.clearTransientCombatState();
    }

    private static void refresh(ServerPlayer player) {
        // Technique buffs are transient, but their attribute modifiers live on the entity, so a
        // buff interrupted by a respawn or a dimension change must have its modifiers stripped
        // explicitly. Otherwise a player who dies mid-Sword-Force keeps the damage bonus.
        TechniqueBuffs.clearAll(player);
        // Focus state is transient, but setMeditating(false) only resets it when the flag actually
        // changes — and a breakthrough already cleared that flag, so an attempt in flight would
        // otherwise survive a respawn. Reset it explicitly instead of relying on that side effect.
        CultivationService.data(player).resetFocus();
        CultivationService.clampQiToCapacity(player, CultivationService.data(player));
        CultivationService.applyRealmAttributes(player);
        CultivationService.syncToClient(player);
    }

    private PlayerLifecycleEvents() {
    }
}
