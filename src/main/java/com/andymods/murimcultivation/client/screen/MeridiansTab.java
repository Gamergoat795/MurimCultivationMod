package com.andymods.murimcultivation.client.screen;

import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.Meridian;
import com.andymods.murimcultivation.cultivation.MeridianService;
import com.andymods.murimcultivation.cultivation.Realm;
import com.andymods.murimcultivation.cultivation.RealmProgression;
import com.andymods.murimcultivation.network.OpenMeridianPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Qi network as a grid: twelve primary meridians above, eight extraordinary vessels below.
 *
 * <p>Cost and risk are computed with the same pure functions the server uses
 * ({@link MeridianService#costToOpen} and {@link MeridianService#deviationChance}) rather than
 * estimated here, so the number in the tooltip is the number that will actually be charged. That
 * works because the config those functions read is a SERVER config, which is synced to clients.
 */
@OnlyIn(Dist.CLIENT)
public class MeridiansTab implements SystemTab {

    private static final int COLUMNS = 6;
    private static final int CELL = 20;
    private static final int GAP = 3;
    private static final int GROUP_GAP = 10;

    @Override
    public Component title() {
        return Component.translatable("murimcultivation.system.tab.meridians");
    }

    @Override
    public void render(GuiGraphics graphics, CultivationData data, Area area, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        graphics.drawString(minecraft.font, Component.translatable(
                        "murimcultivation.system.meridians.header",
                        data.openMeridianCount(), Meridian.count()),
                area.x(), area.y(), SystemTheme.TEXT, false);

        graphics.drawString(minecraft.font,
                Component.translatable("murimcultivation.system.meridians.primary"),
                area.x(), area.y() + 14, SystemTheme.TEXT_DIM, false);
        drawGroup(graphics, data, area, Meridian.PRIMARY_MERIDIANS, area.y() + 26, mouseX, mouseY);

        int vesselsLabelY = area.y() + 26 + rowsOf(Meridian.PRIMARY_MERIDIANS) * (CELL + GAP) + GROUP_GAP;
        graphics.drawString(minecraft.font,
                Component.translatable("murimcultivation.system.meridians.extraordinary"),
                area.x(), vesselsLabelY, SystemTheme.TEXT_DIM, false);
        drawGroup(graphics, data, area, Meridian.EXTRAORDINARY_VESSELS, vesselsLabelY + 12, mouseX, mouseY);

        graphics.drawString(minecraft.font,
                Component.translatable("murimcultivation.system.meridians.hint"),
                area.x(), area.bottom() - 10, SystemTheme.TEXT_DIM, false);
    }

    private void drawGroup(GuiGraphics graphics, CultivationData data, Area area,
                           List<Meridian> group, int top, int mouseX, int mouseY) {
        for (int i = 0; i < group.size(); i++) {
            Meridian meridian = group.get(i);
            int x = area.x() + (i % COLUMNS) * (CELL + GAP);
            int y = top + (i / COLUMNS) * (CELL + GAP);

            boolean open = data.isMeridianOpen(meridian);
            boolean hovered = SystemTheme.isWithin(mouseX, mouseY, x, y, CELL, CELL);

            graphics.fill(x, y, x + CELL, y + CELL,
                    open ? 0xFF1E3A2A : SystemTheme.TAB_ACTIVE);
            SystemTheme.border(graphics, x, y, CELL, CELL,
                    open ? SystemTheme.TEXT_GOOD : hovered ? SystemTheme.BORDER_BRIGHT : SystemTheme.BORDER);

            // A filled pip for open, hollow for sealed — legible without colour alone.
            Minecraft minecraft = Minecraft.getInstance();
            String pip = open ? "◆" : "◇";
            graphics.drawString(minecraft.font, pip,
                    x + (CELL - minecraft.font.width(pip)) / 2, y + 6,
                    open ? SystemTheme.TEXT_GOOD : SystemTheme.TEXT_DIM, false);

            // Extraordinary vessels get a corner mark, since they cost and risk far more.
            if (meridian.isExtraordinary()) {
                graphics.fill(x + CELL - 4, y + 1, x + CELL - 1, y + 4, SystemTheme.TEXT_WARN);
            }
        }
    }

    @Override
    public List<Component> tooltipAt(CultivationData data, Area area, int mouseX, int mouseY) {
        Meridian hovered = meridianAt(area, mouseX, mouseY);
        if (hovered == null) {
            return List.of();
        }

        Minecraft minecraft = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(hovered.translationKey()));
        lines.add(Component.translatable(hovered.isExtraordinary()
                ? "murimcultivation.system.meridians.kind.extraordinary"
                : "murimcultivation.system.meridians.kind.primary"));

        if (data.isMeridianOpen(hovered)) {
            lines.add(Component.translatable("murimcultivation.system.meridians.already_open"));
            return lines;
        }

        if (minecraft.level != null) {
            var realms = RealmProgression.registry(minecraft.level.registryAccess());
            double scale = CultivationService.realmOf(realms, data)
                    .map(Realm::progressPerSubstage)
                    .orElse(100.0D);
            MeridianService.Tuning tuning = MeridianService.Tuning.fromConfig();

            double cost = MeridianService.costToOpen(scale, data.openMeridianCount(), hovered, tuning);
            double risk = MeridianService.deviationChance(data.purity(), hovered, tuning);

            lines.add(Component.translatable("murimcultivation.system.meridians.cost",
                    String.format(Locale.ROOT, "%.0f", cost),
                    String.format(Locale.ROOT, "%.0f", data.progress())));
            lines.add(Component.translatable("murimcultivation.system.meridians.risk",
                    String.format(Locale.ROOT, "%.0f%%", risk * 100.0D)));
            if (data.progress() < cost) {
                lines.add(Component.translatable("murimcultivation.system.meridians.cannot_afford"));
            }
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(CultivationData data, Area area, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        Meridian clicked = meridianAt(area, (int) mouseX, (int) mouseY);
        if (clicked == null || data.isMeridianOpen(clicked)) {
            return false;
        }
        // Exactly the same request the M keybind sends; every check happens server-side.
        PacketDistributor.sendToServer(new OpenMeridianPayload(clicked));
        return true;
    }

    private Meridian meridianAt(Area area, int mouseX, int mouseY) {
        Meridian found = hitTest(area, Meridian.PRIMARY_MERIDIANS, area.y() + 26, mouseX, mouseY);
        if (found != null) {
            return found;
        }
        int vesselsTop = area.y() + 26 + rowsOf(Meridian.PRIMARY_MERIDIANS) * (CELL + GAP)
                + GROUP_GAP + 12;
        return hitTest(area, Meridian.EXTRAORDINARY_VESSELS, vesselsTop, mouseX, mouseY);
    }

    private Meridian hitTest(Area area, List<Meridian> group, int top, int mouseX, int mouseY) {
        for (int i = 0; i < group.size(); i++) {
            int x = area.x() + (i % COLUMNS) * (CELL + GAP);
            int y = top + (i / COLUMNS) * (CELL + GAP);
            if (SystemTheme.isWithin(mouseX, mouseY, x, y, CELL, CELL)) {
                return group.get(i);
            }
        }
        return null;
    }

    private static int rowsOf(List<Meridian> group) {
        return (group.size() + COLUMNS - 1) / COLUMNS;
    }
}
