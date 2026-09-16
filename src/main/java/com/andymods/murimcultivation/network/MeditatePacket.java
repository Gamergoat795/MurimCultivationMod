package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.capability.QiCapabilityProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MeditatePacket {

    public MeditatePacket() {
    }

    public MeditatePacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                player.getCapability(QiCapabilityProvider.QI_CAPABILITY).ifPresent(qiCap -> {
                    boolean newState = !qiCap.isMeditating();
                    qiCap.setMeditating(newState);

                    if (newState) {
                        player.displayClientMessage(Component.literal("You begin to meditate..."), true);
                    } else {
                        player.displayClientMessage(Component.literal("You stop meditating."), true);
                    }

                    NetworkHandler.sendToPlayer(
                            new SyncQiDataPacket(qiCap.getQi(), qiCap.getMaxQi(), qiCap.getStage(), qiCap.isMeditating(), qiCap.hasLearnedQiGathering()),
                            player
                    );
                });
            }
        });
        return true;
    }
}