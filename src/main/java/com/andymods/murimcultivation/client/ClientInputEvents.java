package com.andymods.murimcultivation.client;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.client.hud.FocusPromptLayer;
import com.andymods.murimcultivation.client.screen.SystemScreen;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.MeridianService;
import com.andymods.murimcultivation.cultivation.focus.FocusPrompt;
import com.andymods.murimcultivation.network.AttemptBreakthroughPayload;
import com.andymods.murimcultivation.network.CycleTechniquePayload;
import com.andymods.murimcultivation.network.FocusResponsePayload;
import com.andymods.murimcultivation.network.OpenMeridianPayload;
import com.andymods.murimcultivation.network.ToggleMeditationPayload;
import com.andymods.murimcultivation.network.UseTechniquePayload;
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

        while (KeyBindings.OPEN_SYSTEM.consumeClick()) {
            if (!data.isAwakened()) {
                minecraft.player.displayClientMessage(
                        Component.translatable("murimcultivation.message.not_awakened"), true);
                continue;
            }
            minecraft.setScreen(new SystemScreen());
        }

        while (KeyBindings.MEDITATE.consumeClick()) {
            if (!data.isAwakened()) {
                minecraft.player.displayClientMessage(
                        Component.translatable("murimcultivation.message.not_awakened"), true);
                continue;
            }
            // While a breath-rhythm prompt is up, this key answers it rather than ending the
            // session. No second binding: during meditation there is nothing else B could mean,
            // and the alternative costs a keybind that would collide with something of someone's.
            // The server decides what the answer was worth — all the client reports is where its
            // marker was, and an answer naming a prompt the server has retired is discarded.
            FocusPrompt prompt = FocusPromptLayer.current();
            if (prompt != null) {
                double position = FocusPromptLayer.markerPosition();
                PacketDistributor.sendToServer(new FocusResponsePayload(prompt.id(), position));
                FocusPromptLayer.resolve(
                        position >= prompt.windowStart() && position <= prompt.windowEnd());
                continue;
            }
            PacketDistributor.sendToServer(ToggleMeditationPayload.INSTANCE);
        }

        while (KeyBindings.OPEN_MERIDIAN.consumeClick()) {
            if (!data.isAwakened()) {
                minecraft.player.displayClientMessage(
                        Component.translatable("murimcultivation.message.not_awakened"), true);
                continue;
            }
            // The client only picks which node to ask for; the server decides whether it opens.
            MeridianService.nextSealed(data).ifPresentOrElse(
                    meridian -> PacketDistributor.sendToServer(new OpenMeridianPayload(meridian)),
                    () -> minecraft.player.displayClientMessage(
                            Component.translatable("murimcultivation.meridian.network_complete"), true));
        }

        // Loadout slots. The client sends only which slot was pressed.
        for (int slot = 0; slot < KeyBindings.TECHNIQUE_SLOTS.length; slot++) {
            while (KeyBindings.TECHNIQUE_SLOTS[slot].consumeClick()) {
                if (!data.isAwakened()) {
                    minecraft.player.displayClientMessage(
                            Component.translatable("murimcultivation.message.not_awakened"), true);
                    continue;
                }
                PacketDistributor.sendToServer(new UseTechniquePayload(slot));
            }
        }

        while (KeyBindings.CYCLE_TECHNIQUE.consumeClick()) {
            if (!data.isAwakened()) {
                minecraft.player.displayClientMessage(
                        Component.translatable("murimcultivation.message.not_awakened"), true);
                continue;
            }
            PacketDistributor.sendToServer(CycleTechniquePayload.INSTANCE);
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
