package com.andymods.murimcultivation.event;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.EnlightenmentService;
import com.andymods.murimcultivation.cultivation.MeditationService;
import com.andymods.murimcultivation.system.ObjectiveKind;
import com.andymods.murimcultivation.system.QuestTracker;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * The things that break a cultivator's concentration.
 *
 * <p>Keeping these as separate event handlers that all funnel into
 * {@link MeditationService#stop} means each interruption source stays independent: adding
 * another later does not require touching the meditation logic itself.
 */
@EventBusSubscriber(modid = MurimCultivationMod.MODID)
public final class MeditationInterruptEvents {

    /** Taking a hit ends meditation — and, if it nearly killed you, may teach you something. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CultivationData data = CultivationService.data(player);
        if (data.isMeditating()) {
            MeditationService.stop(player, MeditationService.Interruption.DAMAGED);
        }

        if (!data.isAwakened()) {
            return;
        }

        // A blow that leaves the player on the edge of death is the genre's other route to
        // insight. Checked against the incoming amount so it fires on the hit that hurt,
        // not a tick later once health has already been reduced.
        double remaining = player.getHealth() - event.getAmount();
        double threshold = player.getMaxHealth() * MurimConfig.nearDeathHealthFraction();
        if (remaining > 0.0D && remaining <= threshold) {
            EnlightenmentService.rollAfterNearDeath(player, data);
            CultivationService.syncValuesToClient(player);
        }
    }

    /** Swinging at something is not meditating. */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && CultivationService.data(player).isMeditating()) {
            MeditationService.stop(player, MeditationService.Interruption.ATTACKED);
        }
    }

    /** A kill counts toward any quest asking for one. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer
                && CultivationService.data(killer).isAwakened()) {
            QuestTracker.recordProgress(killer, ObjectiveKind.KILL_ENTITIES, 1);
        }
    }

    private MeditationInterruptEvents() {
    }
}
