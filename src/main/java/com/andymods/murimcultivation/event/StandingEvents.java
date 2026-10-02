package com.andymods.murimcultivation.event;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.npc.DuelService;
import com.andymods.murimcultivation.npc.WanderingWarriorEntity;
import com.andymods.murimcultivation.sect.SectConduct;
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
            return;
        }

        // A warrior beaten by the player who ambushed it yields too. It did not ask for this fight,
        // but it would rather live — and what the ambusher does next is what the sects judge.
        if (event.getEntity() instanceof WanderingWarriorEntity warrior
                && event.getSource().getEntity() instanceof ServerPlayer player
                && warrior.wasAmbushedBy(player)) {
            if (!DuelService.wouldYield(warrior.getHealth(), warrior.getMaxHealth(),
                    event.getAmount(), tuning)) {
                return;
            }
            event.setAmount(DuelService.damageBeforeYielding(
                    warrior.getHealth(), warrior.getMaxHealth(), event.getAmount(), tuning));
            warrior.yieldTo(player, false, MurimConfig.duelTruceTicks());
            player.sendSystemMessage(Component.translatable("murimcultivation.duel.yielded_ambush"));
            return;
        }

        // Anyone else striking a wanderer that was not already fighting is an ambush.
        if (event.getEntity() instanceof WanderingWarriorEntity warrior
                && event.getSource().getEntity() instanceof ServerPlayer player
                && warrior.getTarget() == null) {
            ambush(warrior, player);
        }
    }

    /** The warrior yields: the player has won honourably, and gains by it. */
    private static void warriorYields(WanderingWarriorEntity warrior, ServerPlayer player,
                                      DuelService.Tuning tuning) {
        warrior.yieldTo(player, true, MurimConfig.duelTruceTicks());

        MurimStanding standing = CultivationService.data(player).standing();
        StandingService.wonHonourably(standing, StandingService.Tuning.fromConfig());

        // No sect standing yet. The sects judge what you do with the yield, not the win itself, so
        // that waits until the truce ends (spared) or the warrior dies (not).

        player.sendSystemMessage(Component.translatable("murimcultivation.duel.yielded",
                standing.honour()));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 1.4F);
        CultivationService.syncToClient(player);
    }

    /**
     * Striking a wanderer that was not fighting you is an ambush.
     *
     * <p>Detected on incoming damage rather than on {@code AttackEntityEvent}, which only fires for
     * a melee swing. Arrows, thrown tridents and every martial art reach a warrior without one, so
     * an ambush from range used to cost nothing at all. The source's owning entity is the shooter
     * whatever the projectile, so one check covers all of them.
     *
     * <p>Only the first blow counts. {@link WanderingWarriorEntity#provoke} gives the warrior a target
     * at once — not on its next AI tick, which a multishot crossbow would beat three times over —
     * so later blows land on someone already fighting back.
     */
    private static void ambush(WanderingWarriorEntity warrior, ServerPlayer player) {
        boolean wasYielded = warrior.hasYielded();
        if (!warrior.provoke(player)) {
            return;
        }

        MurimStanding standing = CultivationService.data(player).standing();
        StandingService.ambushed(standing, StandingService.Tuning.fromConfig());
        if (wasYielded) {
            // Striking someone who has already laid down their guard. Counted here once, and again,
            // far more harshly, if it kills them.
            player.sendSystemMessage(Component.translatable("murimcultivation.duel.struck_yielded"));
        } else {
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

        // Whoever it yielded to is judged by how that fight began. Anyone else who kills it was never
        // in a fight with it at all, which makes it murder from ambush.
        boolean fairFight = warrior.yieldedTo(player) && warrior.yieldedInFairFight();
        warrior.sectId().ifPresent(sect ->
                SectService.applyConduct(player, sect, SectConduct.Outcome.of(fairFight, true)));

        player.sendSystemMessage(Component.translatable("murimcultivation.duel.killed_yielded",
                standing.infamy()));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.4F, 0.5F);
        CultivationService.syncToClient(player);
    }

    private StandingEvents() {
    }
}
