package com.andymods.murimcultivation.client;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.network.AttemptBreakthroughPayload;
import com.andymods.murimcultivation.network.ToggleMeditationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns key presses into server requests.
 *
 * <p>The client only ever checks enough to give immediate feedback for an obviously pointless
 * press; it never decides the outcome. The server re-validates everything.
 */
@EventBusSubscriber(modid = MurimCultivationMod.MODID, value = Dist.CLIENT)
public final class ClientInputEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        CultivationData data = CultivationService.data(minecraft.player);

        while (KeyBindings.MEDITATE.consumeClick()) {
            if (!data.isAwakened()) {
                minecraft.player.displayClientMessage(
                        Component.translatable("murimcultivation.message.not_awakened"), true);
                continue;
            }
            PacketDistributor.sendToServer(ToggleMeditationPayload.INSTANCE);
        }

        while (KeyBindings.BREAKTHROUGH.consumeClick()) {
            if (!data.isAwakened()) {
                minecraft.player.displayClientMessage(
                        Component.translatable("murimcultivation.message.not_awakened"), true);
                continue;
            }
            PacketDistributor.sendToServer(AttemptBreakthroughPayload.INSTANCE);
        }
    }

    private ClientInputEvents() {
    }
}
