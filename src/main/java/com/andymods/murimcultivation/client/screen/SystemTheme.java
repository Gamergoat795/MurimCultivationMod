package com.andymods.murimcultivation.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The System window's look: a translucent pane with square borders and a thin accent.
 *
 * <p>Deliberately drawn from fills rather than a texture atlas, for two reasons. It matches the
 * HUD layers already in the mod, and it means the whole window works before any art exists —
 * the M6 polish pass can replace this wholesale without the screen having been blocked on it.
 */
@OnlyIn(Dist.CLIENT)
public final class SystemTheme {

    public static final int PANEL = 0xE0101216;
    public static final int PANEL_INNER = 0x40FFFFFF;
    public static final int BORDER = 0xFF3A4048;
    public static final int BORDER_BRIGHT = 0xFF6E7A88;

    public static final int TAB_ACTIVE = 0xFF1B2028;
    public static final int TAB_INACTIVE = 0xB0101216;

    public static final int TEXT = 0xFFE6E6E6;
    public static final int TEXT_DIM = 0xFF8A8F96;
    public static final int TEXT_ACCENT = 0xFF54C8FF;
    public static final int TEXT_GOOD = 0xFFA5D6A7;
    public static final int TEXT_WARN = 0xFFFFD54F;
    public static final int TEXT_BAD = 0xFFFF6B6B;

    public static final int BAR_TRACK = 0xFF23282F;

    private SystemTheme() {
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        border(graphics, x, y, width, height, BORDER);
    }

    public static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    /** A labelled progress bar. {@code fraction} is clamped, so callers need not. */
    public static void bar(GuiGraphics graphics, int x, int y, int width, int height,
                           double fraction, int color) {
        graphics.fill(x, y, x + width, y + height, BAR_TRACK);
        int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, fraction)) * (width - 2));
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, color);
        }
        border(graphics, x, y, width, height, BORDER);
    }

    public static boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
