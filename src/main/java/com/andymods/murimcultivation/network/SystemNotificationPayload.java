package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.system.SystemNotification;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A System notification, server to client.
 *
 * <p>Uses {@code RegistryFriendlyByteBuf} because {@link Component} needs registry access to
 * serialise — translatable components can carry item and entity arguments.
 */
public record SystemNotificationPayload(SystemNotification notification) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SystemNotificationPayload> TYPE =
            new CustomPacketPayload.Type<>(MurimCultivationMod.id("system_notification"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SystemNotificationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeEnum(payload.notification().kind());
                        ComponentSerialization.STREAM_CODEC.encode(buf, payload.notification().title());
                        ComponentSerialization.STREAM_CODEC.encode(buf, payload.notification().detail());
                    },
                    buf -> {
                        SystemNotification.Kind kind = buf.readEnum(SystemNotification.Kind.class);
                        Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
                        Component detail = ComponentSerialization.STREAM_CODEC.decode(buf);
                        return new SystemNotificationPayload(new SystemNotification(kind, title, detail));
                    });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
