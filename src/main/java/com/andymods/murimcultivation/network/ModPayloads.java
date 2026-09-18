package com.andymods.murimcultivation.network;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.client.ClientPayloadHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = MurimCultivationMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModPayloads {

    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // Client -> server: intent only. The server decides what actually happens.
        registrar.playToServer(
                ToggleMeditationPayload.TYPE,
                ToggleMeditationPayload.STREAM_CODEC,
                ServerPayloadHandler::handleToggleMeditation);

        registrar.playToServer(
                AttemptBreakthroughPayload.TYPE,
                AttemptBreakthroughPayload.STREAM_CODEC,
                ServerPayloadHandler::handleAttemptBreakthrough);

        registrar.playToServer(
                OpenMeridianPayload.TYPE,
                OpenMeridianPayload.STREAM_CODEC,
                ServerPayloadHandler::handleOpenMeridian);

        registrar.playToServer(
                UseTechniquePayload.TYPE,
                UseTechniquePayload.STREAM_CODEC,
                ServerPayloadHandler::handleUseTechnique);

        registrar.playToServer(
                CycleTechniquePayload.TYPE,
                CycleTechniquePayload.STREAM_CODEC,
                ServerPayloadHandler::handleCycleTechnique);

        registrar.playToServer(
                SetLoadoutPayload.TYPE,
                SetLoadoutPayload.STREAM_CODEC,
                ServerPayloadHandler::handleSetLoadout);

        registrar.playToServer(
                SpendStatPointPayload.TYPE,
                SpendStatPointPayload.STREAM_CODEC,
                ServerPayloadHandler::handleSpendStatPoint);

        registrar.playToServer(
                EquipTitlePayload.TYPE,
                EquipTitlePayload.STREAM_CODEC,
                ServerPayloadHandler::handleEquipTitle);

        registrar.playToServer(
                FocusResponsePayload.TYPE,
                FocusResponsePayload.STREAM_CODEC,
                ServerPayloadHandler::handleFocusResponse);

        // Server -> client.
        //
        // These MUST stay as explicit lambdas rather than ClientPayloadHandler::handleX method
        // references. A method reference is linked when the enclosing line runs, which happens
        // on the dedicated server too, and linking it would load ClientPayloadHandler — a class
        // that touches net.minecraft.client — and crash the server with NoClassDefFoundError.
        // Wrapped in a lambda, the client class is only loaded if the lambda body actually runs,
        // which only ever happens on a client. Do not "simplify" these.
        registrar.playToClient(
                SyncCultivationPayload.TYPE,
                SyncCultivationPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleSyncCultivation(payload, context));

        registrar.playToClient(
                SyncCultivationValuesPayload.TYPE,
                SyncCultivationValuesPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleSyncValues(payload, context));

        registrar.playToClient(
                SystemNotificationPayload.TYPE,
                SystemNotificationPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleSystemNotification(payload, context));

        registrar.playToClient(
                FocusPromptPayload.TYPE,
                FocusPromptPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleFocusPrompt(payload, context));
    }

    private ModPayloads() {
    }
}
