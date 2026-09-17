package com.andymods.murimcultivation.client.screen;

import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * The System window: the thing only the protagonist can see.
 *
 * <p>A plain {@link Screen} rather than an {@code AbstractContainerScreen}, because nothing here
 * moves items. A menu type would mean a registration, a server-side menu class and a synced
 * container — all of it overhead for a window that reads already-synced state and sends four
 * small payloads.
 *
 * <p>It also does not pause the game. Cultivation continues while the window is open, which is
 * both correct for a diegetic interface and important in multiplayer, where pausing would not
 * have stopped the world anyway.
 */
@OnlyIn(Dist.CLIENT)
public class SystemScreen extends Screen {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 200;
    private static final int PADDING = 8;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_WIDTH = 74;

    private final List<SystemTab> tabs = List.of(
            new StatusTab(), new MeridiansTab(), new TechniquesTab(), new QuestsTab());

    /** Remembered across openings, so reopening returns you to the tab you were reading. */
    private static int activeTab;

    private int left;
    private int top;

    public SystemScreen() {
        super(Component.translatable("murimcultivation.system.title"));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        activeTab = Math.max(0, Math.min(tabs.size() - 1, activeTab));
        tabs.get(activeTab).onShown();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);

        if (minecraft == null || minecraft.player == null) {
            return;
        }
        CultivationData data = CultivationService.data(minecraft.player);

        SystemTheme.panel(graphics, left, top, WIDTH, HEIGHT);
        graphics.drawString(font, title, left + PADDING, top + 6, SystemTheme.TEXT_ACCENT, false);

        renderTabStrip(graphics, mouseX, mouseY);

        SystemTab.Area area = contentArea();
        SystemTheme.border(graphics, area.x() - 4, area.y() - 4,
                area.width() + 8, area.height() + 8, SystemTheme.BORDER);

        SystemTab tab = tabs.get(activeTab);
        tab.render(graphics, data, area, mouseX, mouseY);

        // Tooltips last, so they sit above every tab's own drawing without each tab having to
        // manage layering.
        List<Component> tooltip = tab.tooltipAt(data, area, mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void renderTabStrip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < tabs.size(); i++) {
            int x = left + PADDING + i * (TAB_WIDTH + 2);
            int y = top + 18;
            boolean active = i == activeTab;
            boolean hovered = SystemTheme.isWithin(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT);

            graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT,
                    active ? SystemTheme.TAB_ACTIVE : SystemTheme.TAB_INACTIVE);
            SystemTheme.border(graphics, x, y, TAB_WIDTH, TAB_HEIGHT,
                    active || hovered ? SystemTheme.BORDER_BRIGHT : SystemTheme.BORDER);

            Component label = tabs.get(i).title();
            graphics.drawString(font, label,
                    x + (TAB_WIDTH - font.width(label)) / 2, y + 4,
                    active ? SystemTheme.TEXT : SystemTheme.TEXT_DIM, false);
        }
    }

    private SystemTab.Area contentArea() {
        int y = top + 18 + TAB_HEIGHT + 8;
        return new SystemTab.Area(left + PADDING + 4, y,
                WIDTH - (PADDING + 4) * 2, HEIGHT - (y - top) - PADDING - 4);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < tabs.size(); i++) {
            int x = left + PADDING + i * (TAB_WIDTH + 2);
            if (SystemTheme.isWithin(mouseX, mouseY, x, top + 18, TAB_WIDTH, TAB_HEIGHT)) {
                if (i != activeTab) {
                    activeTab = i;
                    tabs.get(activeTab).onShown();
                }
                return true;
            }
        }

        if (minecraft != null && minecraft.player != null) {
            CultivationData data = CultivationService.data(minecraft.player);
            if (tabs.get(activeTab).mouseClicked(data, contentArea(), mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tabs.get(activeTab) instanceof QuestsTab quests) {
            quests.scrollBy((int) (-scrollY * 12), contentArea(), contentArea().height() * 3);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Tab and shift-Tab cycle pages, so the window is usable without the mouse.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            int direction = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            activeTab = (activeTab + direction + tabs.size()) % tabs.size();
            tabs.get(activeTab).onShown();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
