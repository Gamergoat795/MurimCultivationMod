package com.example.murimcultivation.network;

import com.example.murimcultivation.MurimCultivationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static SimpleChannel INSTANCE;
    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(MurimCultivationMod.MODID, "messages"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(MeditatePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(MeditatePacket::new)
                .encoder(MeditatePacket::toBytes)
                .consumerMainThread(MeditatePacket::handle)
                .add();

        net.messageBuilder(SyncQiDataPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncQiDataPacket::new)
                .encoder(SyncQiDataPacket::toBytes)
                .consumerMainThread(SyncQiDataPacket::handle)
                .add();

        net.messageBuilder(BreakthroughPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(BreakthroughPacket::new)
                .encoder(BreakthroughPacket::toBytes)
                .consumerMainThread(BreakthroughPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}