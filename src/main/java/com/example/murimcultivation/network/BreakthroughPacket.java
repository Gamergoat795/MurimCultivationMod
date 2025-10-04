package com.example.murimcultivation.network;

import com.example.murimcultivation.ServerEvents;
import com.example.murimcultivation.capability.QiCapabilityProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BreakthroughPacket {

    public BreakthroughPacket() {
    }

    public BreakthroughPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                player.getCapability(QiCapabilityProvider.QI_CAPABILITY).ifPresent(qiCap -> {
                    if (qiCap.getQi() >= qiCap.getMaxQi() && qiCap.getStage() < 2) {
                        qiCap.breakthrough();
                        ServerEvents.updatePlayerAttributes(player, qiCap);
                        player.displayClientMessage(
                                Component.literal("Breakthrough successful! You are now " + qiCap.getStageName()),
                                true
                        );
                        NetworkHandler.sendToPlayer(
                                new SyncQiDataPacket(qiCap.getQi(), qiCap.getMaxQi(), qiCap.getStage(), qiCap.isMeditating(), qiCap.hasLearnedQiGathering()),
                                player
                        );
                    } else {
                        player.displayClientMessage(
                                Component.literal("You need more Qi to breakthrough!"),
                                true
                        );
                    }
                });
            }
        });
        return true;
    }
}