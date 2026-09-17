package com.andymods.murimcultivation.client.hud;

import com.andymods.murimcultivation.system.SystemNotification;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The System's notification strip.
 *
 * <p>Deliberately not vanilla's toast system: these read as a diegetic interface the character
 * is looking at, so they sit top-centre in the mod's own square-bordered style rather than
 * sliding in from the corner like an advancement.
 *
 * <p>A bounded queue, because a datapack that completes twenty quests at once should show a few
 * and drop the rest rather than blanketing the screen for a minute.
 */
@OnlyIn(Dist.CLIENT)
public class SystemToastLayer implements LayeredDraw.Layer {

    private static final int MAX_VISIBLE = 4;
    private static final int MAX_QUEUED = 16;
    private static final int LIFETIME_TICKS = 140;
    private static final int FADE_TICKS = 20;

    private static final int WIDTH = 180;
    private static final int HEIGHT = 26;
    private static final int GAP = 3;
    private static final int TOP_MARGIN = 10;

    private static final Deque<Entry> QUEUE = new ArrayDeque<>();

    /** Accepts a notification from the server. Called on the client thread. */
    public static void push(SystemNotification notification) {
        if (QUEUE.size() >= MAX_QUEUED) {
            QUEUE.pollFirst();
        }
        QUEUE.addLast(new Entry(notification, LIFETIME_TICKS));
    }

    /** Drops everything, so a relog or a dimension change does not replay stale lines. */
    public static void clear() {
        QUEUE.clear();
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || QUEUE.isEmpty()) {
            return;
        }
        if (minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        tick(deltaTracker);

        List<Entry> visible = new ArrayList<>(QUEUE).subList(0, Math.min(MAX_VISIBLE, QUEUE.size()));
        int x = (guiGraphics.guiWidth() - WIDTH) / 2;

        for (int i = 0; i < visible.size(); i++) {
            renderEntry(guiGraphics, minecraft, visible.get(i), x, TOP_MARGIN + i * (HEIGHT + GAP));
        }
    }

    private void tick(DeltaTracker deltaTracker) {
        // Age by real elapsed ticks rather than one per frame, so the strip lasts the same
        // wall-clock time at 30fps and at 240fps.
        float elapsed = deltaTracker.getGameTimeDeltaTicks();
        QUEUE.forEach(entry -> entry.remaining -= elapsed);
        QUEUE.removeIf(entry -> entry.remaining <= 0.0F);
    }

    private void renderEntry(GuiGraphics guiGraphics, Minecraft minecraft, Entry entry, int x, int y) {
        int alpha = (int) (255 * Math.min(1.0F, entry.remaining / (float) FADE_TICKS));
        if (alpha <= 4) {
            return;
        }

        int backdrop = (Math.min(alpha, 200) << 24);
        int accent = (alpha << 24) | (accentOf(entry.notification.kind()) & 0xFFFFFF);
        int text = (alpha << 24) | 0xFFFFFF;

        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, backdrop);
        // A coloured spine on the left instead of an icon: no new art needed, and it still
        // distinguishes a reward from a warning at a glance.
        guiGraphics.fill(x, y, x + 2, y + HEIGHT, accent);
        drawBorder(guiGraphics, x, y, WIDTH, HEIGHT, accent);

        guiGraphics.drawString(minecraft.font, entry.notification.title(), x + 7, y + 5, text, true);
        if (!entry.notification.detail().getString().isEmpty()) {
            guiGraphics.drawString(minecraft.font, entry.notification.detail(),
                    x + 7, y + 15, (alpha << 24) | 0xB0B0B0, true);
        }
    }

    private static int accentOf(SystemNotification.Kind kind) {
        return switch (kind) {
            case QUEST -> 0x54C8FF;
            case ADVANCEMENT -> 0xFFD54F;
            case REWARD -> 0xA5D6A7;
            case WARNING -> 0xFF6B6B;
        };
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static final class Entry {
        private final SystemNotification notification;
        private float remaining;

        private Entry(SystemNotification notification, float remaining) {
            this.notification = notification;
            this.remaining = remaining;
        }
    }
}
