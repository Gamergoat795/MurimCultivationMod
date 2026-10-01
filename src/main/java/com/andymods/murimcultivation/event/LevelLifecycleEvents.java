package com.andymods.murimcultivation.event;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.world.QiSources;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * What has to happen when a level comes or goes.
 *
 * <p>No {@code Dist} on purpose. The vein cache is static and shared by the server thread and the
 * client's render thread, so both sides' unloads have to clear their own entries.
 */
@EventBusSubscriber(modid = MurimCultivationMod.MODID)
public final class LevelLifecycleEvents {

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            QiSources.invalidate(level.dimension(), level.isClientSide());
        }
    }

    private LevelLifecycleEvents() {
    }
}
