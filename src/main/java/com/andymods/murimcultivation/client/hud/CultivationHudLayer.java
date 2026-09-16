package com.andymods.murimcultivation.client.hud;

import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.Realm;
import com.andymods.murimcultivation.cultivation.RealmProgression;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Optional;

/**
 * The cultivator's readout: realm and substage, a vertical Qi gauge, the bar tracking progress
 * to the next step, foundation purity, and a prompt when a breakthrough is available.
 *
 * <p>Nothing renders until the player has awakened — before that they are an ordinary person and
 * should see an ordinary HUD.
 */
@OnlyIn(Dist.CLIENT)
public class CultivationHudLayer implements LayeredDraw.Layer {

    private static final int MARGIN = 6;
    private static final int QI_BAR_WIDTH = 8;
    private static final int QI_BAR_HEIGHT = 96;
    private static final int PROGRESS_BAR_WIDTH = 78;
    private static final int PROGRESS_BAR_HEIGHT = 4;

    private static final int COLOR_BACKDROP = 0xB0000000;
    private static final int COLOR_BORDER = 0xFFDCDCDC;
    private static final int COLOR_QI = 0xFF54C8FF;
    private static final int COLOR_PROGRESS = 0xFFFFC24A;
    private static final int COLOR_PURITY = 0xFF9BE8A0;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_PROMPT = 0xFFFFE34A;
    private static final int COLOR_DEVIATION = 0xFFFF5555;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        // Respect F1 and the debug overlay the way vanilla HUD elements do.
        if (minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        CultivationData data = CultivationService.data(minecraft.player);
        if (!data.isAwakened()) {
            return;
        }

        Registry<Realm> registry = RealmProgression.registry(minecraft.level.registryAccess());
        Optional<Realm> realm = CultivationService.realmOf(registry, data);
        if (realm.isEmpty()) {
            return;
        }

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        renderRealmText(guiGraphics, minecraft, registry, realm.get(), data, screenWidth);
        renderQiGauge(guiGraphics, minecraft, registry, data, screenWidth, screenHeight);
        renderProgressAndPurity(guiGraphics, minecraft, registry, data, screenWidth, screenHeight);
    }

    private void renderRealmText(GuiGraphics guiGraphics, Minecraft minecraft, Registry<Realm> registry,
                                 Realm realm, CultivationData data, int screenWidth) {
        Component realmLine = Component.translatable("murimcultivation.hud.realm",
                realm.displayName(), Component.translatable(data.substage().translationKey()));
        Component ladderLine = Component.translatable("murimcultivation.hud.ladder",
                RealmProgression.ladderPosition(registry, realm), RealmProgression.ladderSize(registry));

        int realmWidth = minecraft.font.width(realmLine);
        int ladderWidth = minecraft.font.width(ladderLine);

        guiGraphics.drawString(minecraft.font, realmLine,
                screenWidth - realmWidth - MARGIN, MARGIN, realm.auraColor() | 0xFF000000, true);
        guiGraphics.drawString(minecraft.font, ladderLine,
                screenWidth - ladderWidth - MARGIN, MARGIN + 11, COLOR_TEXT, true);

        if (data.deviation().isActive()) {
            Component deviation = Component.translatable(data.deviation().translationKey());
            guiGraphics.drawString(minecraft.font, deviation,
                    screenWidth - minecraft.font.width(deviation) - MARGIN, MARGIN + 22, COLOR_DEVIATION, true);
        } else if (data.isMeditating()) {
            Component meditating = Component.translatable("murimcultivation.hud.meditating");
            guiGraphics.drawString(minecraft.font, meditating,
                    screenWidth - minecraft.font.width(meditating) - MARGIN, MARGIN + 22, COLOR_PURITY, true);
        }
    }

    private void renderQiGauge(GuiGraphics guiGraphics, Minecraft minecraft, Registry<Realm> registry,
                               CultivationData data, int screenWidth, int screenHeight) {
        double capacity = CultivationService.qiCapacity(registry, data);
        int x = screenWidth - MARGIN - QI_BAR_WIDTH;
        int y = (screenHeight - QI_BAR_HEIGHT) / 2;

        guiGraphics.fill(x, y, x + QI_BAR_WIDTH, y + QI_BAR_HEIGHT, COLOR_BACKDROP);

        int fillHeight = (int) Math.round(fraction(data.qi(), capacity) * (QI_BAR_HEIGHT - 2));
        if (fillHeight > 0) {
            guiGraphics.fill(x + 1, y + QI_BAR_HEIGHT - 1 - fillHeight,
                    x + QI_BAR_WIDTH - 1, y + QI_BAR_HEIGHT - 1, COLOR_QI);
        }
        drawBorder(guiGraphics, x, y, QI_BAR_WIDTH, QI_BAR_HEIGHT, COLOR_BORDER);

        Component qiText = Component.literal(format(data.qi()) + "/" + format(capacity));
        guiGraphics.drawString(minecraft.font, qiText,
                x - minecraft.font.width(qiText) - 4, y + QI_BAR_HEIGHT / 2 - 4, COLOR_QI, true);
    }

    private void renderProgressAndPurity(GuiGraphics guiGraphics, Minecraft minecraft, Registry<Realm> registry,
                                         CultivationData data, int screenWidth, int screenHeight) {
        int x = screenWidth - MARGIN - PROGRESS_BAR_WIDTH;
        int y = (screenHeight + QI_BAR_HEIGHT) / 2 + 6;

        // Progress toward the next substage, or toward the breakthrough at Peak.
        double completion = CultivationService.stepCompletion(registry, data);
        drawLabelledBar(guiGraphics, minecraft, x, y,
                Component.translatable("murimcultivation.hud.progress"), completion, COLOR_PROGRESS);

        // Foundation purity: the number that decides whether a breakthrough is survivable.
        double purityFraction = data.purity() / CultivationData.MAX_PURITY;
        drawLabelledBar(guiGraphics, minecraft, x, y + 14,
                Component.translatable("murimcultivation.hud.purity"), purityFraction, COLOR_PURITY);

        if (CultivationService.canAdvance(registry, data) && data.substage().isLast()
                && !data.deviation().isActive()) {
            Component prompt = Component.translatable("murimcultivation.hud.breakthrough_ready");
            guiGraphics.drawString(minecraft.font, prompt,
                    screenWidth - minecraft.font.width(prompt) - MARGIN, y + 30, COLOR_PROMPT, true);
        }
    }

    private void drawLabelledBar(GuiGraphics guiGraphics, Minecraft minecraft, int x, int y,
                                 Component label, double fraction, int color) {
        guiGraphics.drawString(minecraft.font, label, x, y, COLOR_TEXT, true);

        int barY = y + 10;
        guiGraphics.fill(x, barY, x + PROGRESS_BAR_WIDTH, barY + PROGRESS_BAR_HEIGHT, COLOR_BACKDROP);
        int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, fraction)) * PROGRESS_BAR_WIDTH);
        if (filled > 0) {
            guiGraphics.fill(x, barY, x + filled, barY + PROGRESS_BAR_HEIGHT, color);
        }
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static double fraction(double value, double max) {
        return max <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, value / max));
    }

    private static String format(double value) {
        return String.valueOf((long) Math.floor(value));
    }
}
