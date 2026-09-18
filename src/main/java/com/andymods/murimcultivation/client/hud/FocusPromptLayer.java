package com.andymods.murimcultivation.client.hud;

import com.andymods.murimcultivation.client.screen.SystemTheme;
import com.andymods.murimcultivation.cultivation.focus.FocusPrompt;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the breath-rhythm bar: a marker sweeping a rail, with the window to hit lit up.
 *
 * <p>Sits bottom-centre above the hotbar, the one band the other three layers leave free — the
 * technique bar owns left-centre, the cultivation readout owns right-centre, and toasts own the
 * top.
 *
 * <p><strong>The clock here is cosmetic.</strong> The marker's position is drawn from elapsed
 * client ticks, but the verdict is decided server-side from the server's own measurement of how
 * long it waited. That split is deliberate: {@code SystemToastLayer} ages its queue inside
 * {@code render}, after the {@code hideGui} early-return, which means holding F1 or F3 stops its
 * clock. Harmless for a decorative toast, and it would defeat this feature entirely — so nothing
 * that decides an outcome is measured here.
 */
@OnlyIn(Dist.CLIENT)
public class FocusPromptLayer implements LayeredDraw.Layer {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 9;
    /** Clear of the hotbar and its selection border. */
    private static final int BOTTOM_MARGIN = 62;
    private static final int MARKER_WIDTH = 3;
    /** How long a verdict flash stays on screen after the prompt resolves. */
    private static final float VERDICT_TICKS = 16.0F;
    /**
     * How far past the sweep the bar keeps drawing before giving up on an answer.
     *
     * <p>Comfortably longer than the server's own grace window, so the bar is never the thing that
     * ends a prompt the server would still have accepted.
     */
    private static final float LAPSE_GRACE_TICKS = 20.0F;

    private static FocusPrompt prompt;
    private static float elapsedTicks;
    private static float verdictRemaining;
    private static boolean verdictWasGood;

    /** A prompt arrived from the server. Called on the client thread. */
    public static void begin(FocusPrompt incoming) {
        prompt = incoming;
        elapsedTicks = 0.0F;
        verdictRemaining = 0.0F;
    }

    /**
     * The player answered, or the prompt lapsed. The bar stops and flashes.
     *
     * <p>The flash colour is the client's own guess, not the server's ruling — the authoritative
     * outcome arrives as an actionbar message from {@code FocusService}. Drawing something
     * immediately matters more than drawing something provably correct, because a bar that keeps
     * sweeping after you pressed feels broken.
     */
    public static void resolve(boolean lookedGood) {
        prompt = null;
        verdictRemaining = VERDICT_TICKS;
        verdictWasGood = lookedGood;
    }

    /** Drops any in-flight prompt. Called on login, respawn and dimension change. */
    public static void clear() {
        prompt = null;
        verdictRemaining = 0.0F;
    }

    /** The prompt awaiting an answer, or {@code null}. Read by the input handler. */
    public static FocusPrompt current() {
        return prompt;
    }

    /** Where the marker is now, 0..1. Sent to the server as the player's claimed position. */
    public static double markerPosition() {
        if (prompt == null || prompt.sweepTicks() <= 0) {
            return 0.0D;
        }
        return Math.min(1.0D, elapsedTicks / prompt.sweepTicks());
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || (prompt == null && verdictRemaining <= 0.0F)) {
            return;
        }
        if (minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) {
            // Advance anyway, so hiding the GUI cannot freeze the bar and hand the player an
            // unlimited window. The server would reject a late answer regardless; this just keeps
            // what is drawn honest about it.
            advance(deltaTracker);
            return;
        }
        advance(deltaTracker);

        int x = (guiGraphics.guiWidth() - WIDTH) / 2;
        int y = guiGraphics.guiHeight() - BOTTOM_MARGIN;

        if (prompt == null) {
            renderVerdict(guiGraphics, x, y);
            return;
        }

        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, SystemTheme.BAR_TRACK);

        int windowLeft = x + (int) Math.round(prompt.windowStart() * WIDTH);
        int windowRight = x + (int) Math.round(prompt.windowEnd() * WIDTH);
        guiGraphics.fill(windowLeft, y + 1, Math.max(windowLeft + 1, windowRight), y + HEIGHT - 1,
                SystemTheme.TEXT_GOOD & 0x60FFFFFF);
        SystemTheme.border(guiGraphics, windowLeft, y, Math.max(2, windowRight - windowLeft), HEIGHT,
                SystemTheme.TEXT_GOOD);

        int markerX = x + (int) Math.round(markerPosition() * (WIDTH - MARKER_WIDTH));
        guiGraphics.fill(markerX, y - 2, markerX + MARKER_WIDTH, y + HEIGHT + 2,
                SystemTheme.TEXT_ACCENT);

        SystemTheme.border(guiGraphics, x, y, WIDTH, HEIGHT, SystemTheme.BORDER);

        Component label = Component.translatable("murimcultivation.hud.circulation");
        guiGraphics.drawString(minecraft.font, label,
                x + (WIDTH - minecraft.font.width(label)) / 2, y - 13, SystemTheme.TEXT, true);
    }

    private void renderVerdict(GuiGraphics guiGraphics, int x, int y) {
        int alpha = (int) (255 * Math.min(1.0F, verdictRemaining / VERDICT_TICKS));
        int colour = (alpha << 24) | ((verdictWasGood ? SystemTheme.TEXT_GOOD : SystemTheme.TEXT_BAD)
                & 0xFFFFFF);
        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, (Math.min(alpha, 160) << 24));
        SystemTheme.border(guiGraphics, x, y, WIDTH, HEIGHT, colour);
    }

    /** Ages both clocks by real elapsed ticks, so the bar reads the same at 30fps and at 240. */
    private void advance(DeltaTracker deltaTracker) {
        float delta = deltaTracker.getGameTimeDeltaTicks();
        if (prompt != null) {
            elapsedTicks += delta;
            // The server scores a lapsed prompt and moves on without telling the client, so the
            // bar has to retire itself or it would sweep forever. Purely visual: the verdict was
            // already decided server-side the moment the deadline passed.
            if (elapsedTicks > prompt.sweepTicks() + LAPSE_GRACE_TICKS) {
                resolve(false);
            }
        } else if (verdictRemaining > 0.0F) {
            verdictRemaining = Math.max(0.0F, verdictRemaining - delta);
        }
    }
}
