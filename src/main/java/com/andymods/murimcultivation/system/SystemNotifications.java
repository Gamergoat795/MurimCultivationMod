package com.andymods.murimcultivation.system;

import com.andymods.murimcultivation.network.SystemNotificationPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends System notifications to a player's own client. */
public final class SystemNotifications {

    private SystemNotifications() {
    }

    /**
     * Pushes a notification from the server.
     *
     * <p>Server-pushed rather than derived on the client from a state diff, because only the
     * server knows that a quest actually completed — a client watching numbers change cannot
     * tell a completion from a command or a datapack reload.
     */
    public static void send(ServerPlayer player, SystemNotification notification) {
        PacketDistributor.sendToPlayer(player, new SystemNotificationPayload(notification));
    }
}
