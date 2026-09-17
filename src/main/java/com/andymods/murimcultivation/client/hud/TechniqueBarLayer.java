package com.andymods.murimcultivation.client.hud;

import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.item.MartialManualItem;
import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.technique.TechniqueMastery;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Optional;

/**
 * The loadout bar: four slots on the left, showing what is bound, its mastery, and whether it
 * can be cast right now.
 *
 * <p>Deliberately answers the three questions a player has mid-fight without opening a menu:
 * is it off cooldown (the slot un-dims and the sweep empties), can I afford it (the Qi cost
 * turns red when it is out of reach), and which slot is selected for the single cast key.
 */
@OnlyIn(Dist.CLIENT)
public class TechniqueBarLayer implements LayeredDraw.Layer {

    private static final int MARGIN = 6;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 2;

    private static final int COLOR_SLOT_BACKDROP = 0xB0000000;
    private static final int COLOR_SLOT_BORDER = 0xFF6E6E6E;
    private static final int COLOR_SLOT_SELECTED = 0xFFFFE34A;
    private static final int COLOR_COOLDOWN_VEIL = 0xC0202020;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_COST_OK = 0xFF54C8FF;
    private static final int COLOR_COST_SHORT = 0xFFFF6B6B;
    private static final int COLOR_MASTERY = 0xFFA5D6A7;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        CultivationData data = CultivationService.data(minecraft.player);
        if (!data.isAwakened() || data.loadout().isEmpty()) {
            return;
        }

        Registry<Technique> techniques =
                minecraft.level.registryAccess().registryOrThrow(MurimRegistries.TECHNIQUE);

        int barHeight = CultivationData.LOADOUT_SIZE * SLOT_SIZE
                + (CultivationData.LOADOUT_SIZE - 1) * SLOT_GAP;
        int y = (guiGraphics.guiHeight() - barHeight) / 2;

        for (int slot = 0; slot < CultivationData.LOADOUT_SIZE; slot++) {
            int slotY = y + slot * (SLOT_SIZE + SLOT_GAP);
            renderSlot(guiGraphics, minecraft, techniques, data, slot, MARGIN, slotY);
        }
    }

    private void renderSlot(GuiGraphics guiGraphics, Minecraft minecraft, Registry<Technique> registry,
                            CultivationData data, int slot, int x, int y) {
        Optional<ResourceLocation> bound = data.techniqueInSlot(slot);

        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, COLOR_SLOT_BACKDROP);
        boolean selected = slot == data.selectedSlot() && bound.isPresent();
        drawBorder(guiGraphics, x, y, SLOT_SIZE, SLOT_SIZE,
                selected ? COLOR_SLOT_SELECTED : COLOR_SLOT_BORDER);

        // The slot number, so a player can see which key maps to which art.
        guiGraphics.drawString(minecraft.font, String.valueOf(slot + 1), x + 2, y + 2, COLOR_TEXT, true);

        if (bound.isEmpty()) {
            return;
        }

        ResourceLocation id = bound.get();
        Optional<Technique> technique =
                registry.getOptional(ResourceKey.create(MurimRegistries.TECHNIQUE, id));

        int mastery = data.techniqueMastery(id);
        int textX = x + SLOT_SIZE + 4;

        // Name plus a star rating, so progress toward mastery is visible without a menu.
        Component name = Component.translatable(MartialManualItem.translationKeyFor(id));
        guiGraphics.drawString(minecraft.font, name, textX, y + 1, COLOR_TEXT, true);
        guiGraphics.drawString(minecraft.font, stars(mastery), textX, y + 11, COLOR_MASTERY, true);

        if (technique.isPresent()) {
            double cost = TechniqueMastery.qiCost(technique.get().qiCost(), mastery);
            boolean affordable = data.qi() >= cost;
            Component costText = Component.literal(String.valueOf(Math.round(cost)));
            guiGraphics.drawString(minecraft.font, costText,
                    textX + minecraft.font.width(name) + 6, y + 1,
                    affordable ? COLOR_COST_OK : COLOR_COST_SHORT, true);
        }

        renderCooldownSweep(guiGraphics, data, id, technique, mastery, x, y);
    }

    /**
     * A veil that shrinks upward as the cooldown runs out, so remaining time is readable at a
     * glance rather than as a number to parse.
     */
    private void renderCooldownSweep(GuiGraphics guiGraphics, CultivationData data, ResourceLocation id,
                                     Optional<Technique> technique, int mastery, int x, int y) {
        int remaining = data.techniqueCooldown(id);
        if (remaining <= 0 || technique.isEmpty()) {
            return;
        }

        int total = TechniqueMastery.cooldownTicks(technique.get().cooldownTicks(), mastery);
        if (total <= 0) {
            return;
        }

        int veilHeight = (int) Math.ceil(SLOT_SIZE * Math.min(1.0D, remaining / (double) total));
        guiGraphics.fill(x + 1, y + SLOT_SIZE - veilHeight, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1,
                COLOR_COOLDOWN_VEIL);
    }

    private static String stars(int mastery) {
        int filled = TechniqueMastery.stars(mastery);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            builder.append(i < filled ? '★' : '☆');
        }
        return builder.toString();
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
