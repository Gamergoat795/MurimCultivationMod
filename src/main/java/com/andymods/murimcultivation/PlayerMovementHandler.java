package com.andymods.murimcultivation;

import com.andymods.murimcultivation.capability.QiCapabilityProvider;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MurimCultivationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerMovementHandler {
    private static final UUID MEDITATION_FREEZE_UUID = UUID.fromString("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d");

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Player player) {
            player.getCapability(QiCapabilityProvider.QI_CAPABILITY).ifPresent(qiCap -> {
                if (qiCap.isMeditating()) {
                    player.setDeltaMovement(Vec3.ZERO);
                    player.hurtMarked = true;

                    player.setOnGround(true);

                    player.setSprinting(false);

                    AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speedAttr != null && speedAttr.getModifier(MEDITATION_FREEZE_UUID) == null) {
                        speedAttr.addTransientModifier(new AttributeModifier(
                                MEDITATION_FREEZE_UUID,
                                "Meditation Freeze",
                                -1.0,
                                AttributeModifier.Operation.MULTIPLY_TOTAL
                        ));
                    }
                } else {
                    AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speedAttr != null && speedAttr.getModifier(MEDITATION_FREEZE_UUID) != null) {
                        speedAttr.removeModifier(MEDITATION_FREEZE_UUID);
                    }
                }
            });
        }
    }
}