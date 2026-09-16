package com.andymods.murimcultivation.client;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.capability.QiCapabilityProvider;
import com.andymods.murimcultivation.network.BreakthroughPacket;
import com.andymods.murimcultivation.network.NetworkHandler;
import com.andymods.murimcultivation.network.MeditatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MurimCultivationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // Handle Meditate Key
                while (KeyBindings.MEDITATE_KEY.consumeClick()) {
                    mc.player.getCapability(QiCapabilityProvider.QI_CAPABILITY).ifPresent(qiCap -> {
                        if (qiCap.hasLearnedQiGathering()) {
                            mc.player.displayClientMessage(Component.literal("Sending meditate packet..."), false);
                            NetworkHandler.sendToServer(new MeditatePacket());
                        } else {
                            mc.player.displayClientMessage(Component.literal("You must learn Qi Gathering first!"), true);
                        }
                    });
                }

                while (KeyBindings.BREAKTHROUGH_KEY.consumeClick()) {
                    mc.player.getCapability(QiCapabilityProvider.QI_CAPABILITY).ifPresent(qiCap -> {
                        if (qiCap.hasLearnedQiGathering()) {
                            if (qiCap.getQi() >= qiCap.getMaxQi() && qiCap.getStage() < 2) {
                                mc.player.displayClientMessage(Component.literal("Attempting breakthrough..."), false);
                                NetworkHandler.sendToServer(new BreakthroughPacket());
                            } else if (qiCap.getStage() >= 2) {
                                mc.player.displayClientMessage(Component.literal("You have reached the maximum stage!"), true);
                            } else {
                                mc.player.displayClientMessage(Component.literal("Not enough Qi! (" + qiCap.getQi() + "/" + qiCap.getMaxQi() + ")"), true);
                            }
                        }
                    });
                }
            }
        }
    }
}
