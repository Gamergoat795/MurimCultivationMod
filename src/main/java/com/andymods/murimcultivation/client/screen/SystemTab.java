package com.andymods.murimcultivation.client.screen;

import com.andymods.murimcultivation.cultivation.CultivationData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * One page of the System window.
 *
 * <p>Tabs are given the content area rather than the whole screen, and return tooltips rather
 * than drawing them, so the frame can render every tooltip last and on top without each tab
 * having to think about layering.
 */
@OnlyIn(Dist.CLIENT)
public interface SystemTab {

    /** The tab's label on the strip. */
    Component title();

    void render(GuiGraphics graphics, CultivationData data, Area area, int mouseX, int mouseY);

    /** Tooltip lines for whatever the mouse is over, or empty. */
    default List<Component> tooltipAt(CultivationData data, Area area, int mouseX, int mouseY) {
        return List.of();
    }

    /** @return whether the click was consumed */
    default boolean mouseClicked(CultivationData data, Area area, double mouseX, double mouseY, int button) {
        return false;
    }

    /** Called when the tab becomes visible, for tabs that keep scroll or selection state. */
    default void onShown() {
    }

    /** The rectangle a tab may draw in. */
    record Area(int x, int y, int width, int height) {

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }
    }
}
