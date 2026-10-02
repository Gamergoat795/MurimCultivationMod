package com.andymods.murimcultivation.event;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.Realm;
import com.andymods.murimcultivation.npc.BountyService;
import com.andymods.murimcultivation.npc.WanderingWarriorEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Sends bounty hunters after the notorious. See {@link BountyService} for who and how hard.
 *
 * <p>Its own subscriber rather than a branch in {@code CultivationTickEvents}, which is about
 * cultivating and early-returns for reasons — deviation, meditation — that have nothing to do with
 * whether someone is wanted.
 */
@EventBusSubscriber(modid = MurimCultivationMod.MODID)
public final class BountyEvents {

    /** How long a contract runs before the hunter gives up, in ticks. Five minutes. */
    private static final int CONTRACT_TICKS = 6000;

    /** How far to look for a hunter already on someone's trail before sending another. */
    private static final double ONE_AT_A_TIME_RADIUS = 128.0D;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.isCreative()) {
            return;
        }
        BountyService.Tuning tuning = BountyService.Tuning.fromConfig();
        if (player.tickCount % Math.max(1, tuning.checkIntervalTicks()) != 0) {
            return;
        }

        CultivationData data = CultivationService.data(player);
        int infamy = data.standing().infamy();
        if (!data.isAwakened() || !BountyService.sendsHunter(infamy, player.getRandom().nextDouble(), tuning)) {
            return;
        }
        boolean alreadyHunted = !player.level().getEntitiesOfClass(WanderingWarriorEntity.class,
                player.getBoundingBox().inflate(ONE_AT_A_TIME_RADIUS), hunter -> hunter.isHunting(player)).isEmpty();
        if (alreadyHunted) {
            return;
        }

        int playerTier = CultivationService.realmOf(player).map(Realm::tier).orElse(1);
        int hunterTier = BountyService.hunterRealmTier(playerTier, infamy, tuning);
        if (WanderingWarriorEntity.sendHunter(player, hunterTier, CONTRACT_TICKS)) {
            player.sendSystemMessage(Component.translatable("murimcultivation.bounty.sent"));
            player.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6F, 1.2F);
        }
    }

    private BountyEvents() {
    }
}
