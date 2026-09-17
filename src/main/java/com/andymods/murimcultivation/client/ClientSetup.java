package com.andymods.murimcultivation.client;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.client.hud.CultivationHudLayer;
import com.andymods.murimcultivation.client.hud.SystemToastLayer;
import com.andymods.murimcultivation.client.hud.TechniqueBarLayer;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = MurimCultivationMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    /**
     * Registers every key mapping by iterating {@link KeyBindings#ALL}.
     *
     * <p>Listing them individually here is how a binding ends up declared but never registered,
     * which makes the key silently do nothing and its handler dead code.
     */
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : KeyBindings.ALL) {
            event.register(mapping);
        }
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(MurimCultivationMod.id("cultivation_hud"), new CultivationHudLayer());
        event.registerAboveAll(MurimCultivationMod.id("technique_bar"), new TechniqueBarLayer());
        event.registerAboveAll(MurimCultivationMod.id("system_toasts"), new SystemToastLayer());
    }

    private ClientSetup() {
    }
}
