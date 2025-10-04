package com.example.murimcultivation.client;

import com.example.murimcultivation.capability.QiCapability;
import com.example.murimcultivation.capability.QiCapabilityProvider;
import com.example.murimcultivation.network.BreakthroughPacket;
import com.example.murimcultivation.network.NetworkHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class QiOverlay {
    public static final IGuiOverlay QI_OVERLAY = ((gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getCapability(QiCapabilityProvider.QI_CAPABILITY).ifPresent(qiCap -> {
                if (qiCap.hasLearnedQiGathering()) {
                    renderQiBar(guiGraphics, qiCap, screenWidth, screenHeight);
                    renderStageText(guiGraphics, qiCap, screenWidth);
                }
            });
        }
    });

    private static void renderQiBar(GuiGraphics guiGraphics, QiCapability qiCap, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int barWidth = 10;
        int barHeight = 100;
        int x = screenWidth - 30;
        int y = screenHeight / 2 - barHeight / 2;

        guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF000000);

        int fillHeight = (int) ((float) qiCap.getQi() / qiCap.getMaxQi() * barHeight);
        int fillY = y + barHeight - fillHeight;

        int color = getQiColor(qiCap.getStage());
        guiGraphics.fill(x + 1, fillY, x + barWidth - 1, y + barHeight - 1, color);

        guiGraphics.fill(x, y, x + 1, y + barHeight, 0xFFFFFFFF);
        guiGraphics.fill(x + barWidth - 1, y, x + barWidth, y + barHeight, 0xFFFFFFFF);
        guiGraphics.fill(x, y, x + barWidth, y + 1, 0xFFFFFFFF);
        guiGraphics.fill(x, y + barHeight - 1, x + barWidth, y + barHeight, 0xFFFFFFFF);

        String qiText = qiCap.getQi() + "/" + qiCap.getMaxQi();
        guiGraphics.drawString(mc.font, qiText, x - 40, y + barHeight + 5, 0xFFFFFF);

        if (qiCap.getQi() >= qiCap.getMaxQi() && qiCap.getStage() < 2) {
            guiGraphics.drawString(mc.font, "Press X", x - 30, y - 15, 0xFFFFFF00);
            guiGraphics.drawString(mc.font, "Breakthrough!", x - 45, y - 5, 0xFFFFFF00);
        }
    }

    private static void renderStageText(GuiGraphics guiGraphics, QiCapability qiCap, int screenWidth) {
        Minecraft mc = Minecraft.getInstance();
        String stageText = "Stage: " + qiCap.getStageName();
        int textWidth = mc.font.width(stageText);
        guiGraphics.drawString(mc.font, stageText, screenWidth - textWidth - 10, 10, 0xFFFFFF);
    }

    private static int getQiColor(int stage) {
        switch (stage) {
            case 0: return 0xFF4444FF; // Blue
            case 1: return 0xFF44FF44; // Green
            case 2: return 0xFFFF4444; // Red
            default: return 0xFF888888;
        }
    }
}