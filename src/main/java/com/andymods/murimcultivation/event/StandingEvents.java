package com.andymods.murimcultivation.event;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.npc.DuelService;
import com.andymods.murimcultivation.npc.WanderingWarriorEntity;
import com.andymods.murimcultivation.sect.SectService;
import com.andymods.murimcultivation.standing.MurimStanding;
import com.andymods.murimcultivation.standing.StandingService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * How conduct in a fight moves honour and infamy.
 *
 * <p>A separate subscriber from {@link MeditationInterruptEvents} even though both listen to
 * {@code AttackEntityEvent} and {@code LivingDeathEvent}. That file's own javadoc argues for keeping
 * each concern an independent handler funnelling into one service, and honour is plainly not
 * meditation interruption. NeoForge is happy to have two subscribers on one event, and the
 * {@code event/} package is organised by subject — the same reasoning M5.6 used for level lifecycle.
 *
 * <p><strong>The rule these handlers implement: conduct, not outcome.</strong> Nothing here rewards
 * winning or punishes losing. Ambushing costs, killing someone who has yielded costs a great deal,
 * and beating someone who agreed to fight you pays — and losing a duel you asked for honestly does
 * nothing at all, which is why there is no handler for it.
 */
@EventBusSubscriber(modid = MurimCultivationMod.MODID)
public final class StandingEvents {

    /**
     * Turns a killing blow in a duel into a yield.
     *
     * <p>Both directions, and the second is the one that makes the fixed-difficulty world fair:
     * beat a warrior low enough and it yields rather than dying; lose to one and it spares you
     * rather than killing you. Without the second half, meeting someone above your realm would be a
     * death sentence and no player would ever duel upward.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DuelService.Tuning tuning = DuelService.Tuning.fromConfig();

        // A warrior being beaten by the player it agreed to fight.
        if (event.getEntity() instanceof WanderingWarriorEntity warrior
                && event.getSource().getEntity() instanceof ServerPlayer player
                && warrior.isDuellingWith(player)) {
            if (!DuelService.wouldYield(warrior.getHealth(), warrior.getMaxHealth(),
                    event.getAmount(), tuning)) {
                return;
            }
            event.setAmount(DuelService.damageBeforeYielding(
                    warrior.getHealth(), warrior.getMaxHealth(), event.getAmount(), tuning));
            warriorYields(warrior, player, tuning);
            return;
        }

        // The player being beaten by the warrior they challenged.
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof WanderingWarriorEntity warrior
                && warrior.isDuellingWith(player)) {
            if (!DuelService.wouldYield(player.getHealth(), player.getMaxHealth(),
                    event.getAmount(), tuning)) {
                return;
            }
            event.setAmount(DuelService.damageBeforeYielding(
                    player.getHealth(), player.getMaxHealth(), event.getAmount(), tuning));
            warrior.standDown(MurimConfig.duelTruceTicks(), false);
            player.sendSystemMessage(Component.translatable("murimcultivation.duel.spared"));
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 0.7F);
            // Deliberately no standing change. Losing a fight you asked for honestly is not
            // misconduct, and an honour system that punished it would only be measuring strength.
        }
    }

    /** The warrior yields: the player has won honourably, and gains by it. */
    private static void warriorYields(WanderingWarriorEntity warrior, ServerPlayer player,
                                      DuelService.Tuning tuning) {
        warrior.standDown(MurimConfig.duelTruceTicks(), true);

        MurimStanding standing = CultivationService.data(player).standing();
        StandingService.wonHonourably(standing, StandingService.Tuning.fromConfig());

        // If they served a sect, beating one of their people is noticed — by them, and by whoever
        // opposes them. This is the first thing in the game to feed SectService.addReputation from
        // play, so the opposed-alignment penalty written in M5a finally has occasion to fire.
        warrior.sectId().ifPresent(sect ->
                SectService.addReputation(player, sect, MurimConfig.duelSectReputation()));

        player.sendSystemMessage(Component.translatable("murimcultivation.duel.yielded",
                standing.honour()));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 1.4F);
        CultivationService.syncToClient(player);
    }

    /**
     * Striking a wanderer that was not fighting you is an ambush.
     *
     * <p>Only the first blow counts. Retaliation gives the warrior a target, so by the second swing
     * this no longer fires — which is the intended reading: the ambush is the decision to start,
     * not every hit in the fight that follows.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof WanderingWarriorEntity warrior)) {
            return;
        }
        if (warrior.isDuellingWith(player) || warrior.getTarget() != null) {
            return;
        }

        MurimStanding standing = CultivationService.data(player).standing();
        if (warrior.hasYielded()) {
            // Striking someone who has already laid down their guard. Handled as an ambush here and
            // again, far more harshly, if it kills them.
            StandingService.ambushed(standing, StandingService.Tuning.fromConfig());
            player.sendSystemMessage(Component.translatable("murimcultivation.duel.struck_yielded"));
        } else {
            StandingService.ambushed(standing, StandingService.Tuning.fromConfig());
            player.sendSystemMessage(Component.translatable("murimcultivation.duel.ambushed",
                    standing.infamy()));
        }
        CultivationService.syncToClient(player);
    }

    /**
     * Killing a wanderer that had yielded.
     *
     * <p>The worst act available, and the reason the yield rule is worth having: a mechanic that
     * lets you spare someone only means something because refusing to is possible and costly.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WanderingWarriorEntity warrior)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !warrior.hasYielded()) {
            return;
        }

        MurimStanding standing = CultivationService.data(player).standing();
        StandingService.killedTheYielded(standing, StandingService.Tuning.fromConfig());

        // And their sect remembers it, which is the fastest route to being unwelcome everywhere.
        warrior.sectId().ifPresent(sect ->
                SectService.addReputation(player, sect, -MurimConfig.duelSectReputation() * 2));

        player.sendSystemMessage(Component.translatable("murimcultivation.duel.killed_yielded",
                standing.infamy()));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.4F, 0.5F);
        CultivationService.syncToClient(player);
    }

    private StandingEvents() {
    }
}
