package com.example.murimcultivation;

import com.example.murimcultivation.capability.QiCapability;
import com.example.murimcultivation.capability.QiCapabilityProvider;
import com.example.murimcultivation.network.NetworkHandler;
import com.example.murimcultivation.network.SyncQiDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;
import java.util.UUID;

public class ServerEvents {
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("8e3b9c6d-7f2a-4d1b-9e5c-3a7b4f8d2e1c");
    private static final UUID DAMAGE_MODIFIER_UUID = UUID.fromString("7d4e8f9a-6c3b-4e2d-8f7a-2b6c4d8e9f1a");
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("9f7e6d5c-4b3a-2e1d-8c7b-6a5d4e3f2c1b");

    private int tickCounter = 0;
    private final Random random = new Random();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide()) {
            ServerPlayer player = (ServerPlayer) event.player;

            player.getCapability(QiCapabilityProvider.QI_CAPABILITY).ifPresent(qiCap -> {
                if (qiCap.isMeditating()) {
                    tickCounter++;
                    if (tickCounter >= 20) {
                        tickCounter = 0;
                        int qiGain = random.nextInt(20) + 1;
                        qiCap.addQi(qiGain);
                        NetworkHandler.sendToPlayer(new SyncQiDataPacket(qiCap.getQi(), qiCap.getMaxQi(), qiCap.getStage(), qiCap.isMeditating(), qiCap.hasLearnedQiGathering()), player);
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(QiCapabilityProvider.QI_CAPABILITY).ifPresent(qiCap -> {
                updatePlayerAttributes(player, qiCap);
                NetworkHandler.sendToPlayer(new SyncQiDataPacket(qiCap.getQi(), qiCap.getMaxQi(), qiCap.getStage(), qiCap.isMeditating(), qiCap.hasLearnedQiGathering()), player);
            });
        }
    }

    public static void updatePlayerAttributes(ServerPlayer player, QiCapability qiCap) {
        int stage = qiCap.getStage();

        // Remove old modifiers
        var healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        var damageAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);

        if (healthAttr != null) {
            healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
            if (stage > 0) {
                healthAttr.addPermanentModifier(new AttributeModifier(
                        HEALTH_MODIFIER_UUID,
                        "Cultivation Health Bonus",
                        stage * 10.0,
                        AttributeModifier.Operation.ADDITION
                ));
            }
        }

        if (damageAttr != null) {
            damageAttr.removeModifier(DAMAGE_MODIFIER_UUID);
            if (stage > 0) {
                damageAttr.addPermanentModifier(new AttributeModifier(
                        DAMAGE_MODIFIER_UUID,
                        "Cultivation Damage Bonus",
                        stage * 2.0,
                        AttributeModifier.Operation.ADDITION
                ));
            }
        }

        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_UUID);
            if (stage > 0) {
                speedAttr.addPermanentModifier(new AttributeModifier(
                        SPEED_MODIFIER_UUID,
                        "Cultivation Speed Bonus",
                        stage * 0.05,
                        AttributeModifier.Operation.ADDITION
                ));
            }
        }

        player.setHealth(player.getMaxHealth());
    }
}