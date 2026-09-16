package com.andymods.murimcultivation.item;

import com.andymods.murimcultivation.capability.QiCapabilityProvider;
import com.andymods.murimcultivation.network.NetworkHandler;
import com.andymods.murimcultivation.network.SyncQiDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class QiGatheringManualItem extends Item {
    public QiGatheringManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            player.getCapability(QiCapabilityProvider.QI_CAPABILITY).ifPresent(qiCap -> {
                if (!qiCap.hasLearnedQiGathering()) {
                    qiCap.setLearnedQiGathering(true);
                    player.displayClientMessage(Component.literal("You have learned Basic Qi Gathering"), true);

                    if (player instanceof ServerPlayer serverPlayer) {
                        NetworkHandler.sendToPlayer(
                                new SyncQiDataPacket(qiCap.getQi(), qiCap.getMaxQi(), qiCap.getStage(), qiCap.isMeditating(), qiCap.hasLearnedQiGathering()),
                                serverPlayer
                        );
                    }
                } else {
                    player.displayClientMessage(Component.literal("You have already learned this technique"), true);
                }
            });
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}