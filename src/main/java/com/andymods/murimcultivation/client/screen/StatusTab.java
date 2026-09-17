package com.andymods.murimcultivation.client.screen;

import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.Realm;
import com.andymods.murimcultivation.cultivation.RealmProgression;
import com.andymods.murimcultivation.network.EquipTitlePayload;
import com.andymods.murimcultivation.network.SpendStatPointPayload;
import com.andymods.murimcultivation.system.StatType;
import com.andymods.murimcultivation.system.Title;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Where you stand: realm, the three numbers, the four stats, and the titles you have earned. */
@OnlyIn(Dist.CLIENT)
public class StatusTab implements SystemTab {

    private static final int ROW = 12;
    private static final int STAT_ROW = 16;
    private static final int PLUS_SIZE = 11;

    @Override
    public Component title() {
        return Component.translatable("murimcultivation.system.tab.status");
    }

    @Override
    public void render(GuiGraphics graphics, CultivationData data, Area area, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        Registry<Realm> realms = RealmProgression.registry(minecraft.level.registryAccess());
        Optional<Realm> realm = CultivationService.realmOf(realms, data);

        int y = area.y();

        // Realm and position on the ladder.
        Component realmLine = realm
                .map(value -> Component.translatable("murimcultivation.system.status.realm",
                        value.fullDisplayName(),
                        Component.translatable(data.substage().translationKey())))
                .orElseGet(() -> Component.translatable("murimcultivation.system.status.no_realm"));
        graphics.drawString(minecraft.font, realmLine, area.x(), y,
                realm.map(value -> value.auraColor() | 0xFF000000).orElse(SystemTheme.TEXT), false);
        y += ROW;

        realm.ifPresent(value -> graphics.drawString(minecraft.font,
                Component.translatable("murimcultivation.hud.ladder",
                        RealmProgression.ladderPosition(realms, value), RealmProgression.ladderSize(realms)),
                area.x(), area.y() + ROW, SystemTheme.TEXT_DIM, false));
        y += ROW - 2;

        // The three numbers, each as a bar so their relationship is visible at a glance.
        int barWidth = area.width() / 2 - 8;
        y = drawValueBar(graphics, minecraft, area.x(), y, barWidth,
                Component.translatable("murimcultivation.system.status.qi"),
                data.qi(), CultivationService.qiCapacity(realms, data), SystemTheme.TEXT_ACCENT);
        y = drawValueBar(graphics, minecraft, area.x(), y, barWidth,
                Component.translatable("murimcultivation.hud.progress"),
                data.progress(), CultivationService.progressForNextStep(realms, data), SystemTheme.TEXT_WARN);
        y = drawValueBar(graphics, minecraft, area.x(), y, barWidth,
                Component.translatable("murimcultivation.hud.purity"),
                data.purity(), CultivationData.MAX_PURITY, SystemTheme.TEXT_GOOD);

        renderStats(graphics, minecraft, data, area, y + 4, mouseX, mouseY);
        renderTitles(graphics, minecraft, data, area);
    }

    private int drawValueBar(GuiGraphics graphics, Minecraft minecraft, int x, int y, int width,
                             Component label, double value, double max, int color) {
        graphics.drawString(minecraft.font, label, x, y, SystemTheme.TEXT_DIM, false);
        String readout = max >= Double.MAX_VALUE / 2
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.0f / %.0f", value, max);
        graphics.drawString(minecraft.font, readout,
                x + width - minecraft.font.width(readout), y, SystemTheme.TEXT, false);
        SystemTheme.bar(graphics, x, y + 10, width, 4, max <= 0.0D ? 0.0D : value / max, color);
        return y + 18;
    }

    // --- Stats ------------------------------------------------------------------------

    private void renderStats(GuiGraphics graphics, Minecraft minecraft, CultivationData data,
                             Area area, int top, int mouseX, int mouseY) {
        int x = area.x() + area.width() / 2 + 6;
        int width = area.width() / 2 - 6;

        graphics.drawString(minecraft.font, Component.translatable("murimcultivation.system.status.stats",
                data.systemProgress().unspentPoints()), x, area.y(), SystemTheme.TEXT, false);

        StatType[] stats = StatType.values();
        for (int i = 0; i < stats.length; i++) {
            StatType stat = stats[i];
            int rowY = area.y() + ROW + 2 + i * STAT_ROW;
            int points = data.systemProgress().pointsIn(stat);

            graphics.drawString(minecraft.font, Component.translatable(stat.translationKey()),
                    x, rowY + 2, SystemTheme.TEXT, false);
            String value = String.valueOf(points);
            graphics.drawString(minecraft.font, value,
                    x + width - PLUS_SIZE - 6 - minecraft.font.width(value), rowY + 2,
                    points > 0 ? SystemTheme.TEXT_GOOD : SystemTheme.TEXT_DIM, false);

            // The spend button only offers itself when it would actually work, so a player is
            // never invited to click something that will be refused.
            boolean canSpend = data.systemProgress().unspentPoints() > 0
                    && points < StatType.MAX_POINTS_PER_STAT;
            int buttonX = x + width - PLUS_SIZE;
            int colour = !canSpend ? SystemTheme.TEXT_DIM
                    : SystemTheme.isWithin(mouseX, mouseY, buttonX, rowY, PLUS_SIZE, PLUS_SIZE)
                    ? SystemTheme.TEXT_ACCENT : SystemTheme.TEXT;
            graphics.fill(buttonX, rowY, buttonX + PLUS_SIZE, rowY + PLUS_SIZE, SystemTheme.TAB_ACTIVE);
            SystemTheme.border(graphics, buttonX, rowY, PLUS_SIZE, PLUS_SIZE,
                    canSpend ? SystemTheme.BORDER_BRIGHT : SystemTheme.BORDER);
            graphics.drawString(minecraft.font, "+", buttonX + 4, rowY + 2, colour, false);
        }
    }

    @Override
    public boolean mouseClicked(CultivationData data, Area area, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int x = area.x() + area.width() / 2 + 6;
        int width = area.width() / 2 - 6;
        StatType[] stats = StatType.values();

        for (int i = 0; i < stats.length; i++) {
            int rowY = area.y() + ROW + 2 + i * STAT_ROW;
            if (SystemTheme.isWithin(mouseX, mouseY, x + width - PLUS_SIZE, rowY, PLUS_SIZE, PLUS_SIZE)) {
                // The server re-checks affordability and the cap; this is only the request.
                PacketDistributor.sendToServer(new SpendStatPointPayload(stats[i], 1));
                return true;
            }
        }

        return clickTitle(data, area, mouseX, mouseY);
    }

    @Override
    public List<Component> tooltipAt(CultivationData data, Area area, int mouseX, int mouseY) {
        int x = area.x() + area.width() / 2 + 6;
        int width = area.width() / 2 - 6;
        StatType[] stats = StatType.values();

        for (int i = 0; i < stats.length; i++) {
            int rowY = area.y() + ROW + 2 + i * STAT_ROW;
            if (SystemTheme.isWithin(mouseX, mouseY, x, rowY, width, PLUS_SIZE)) {
                return List.of(
                        Component.translatable(stats[i].translationKey()),
                        Component.translatable(stats[i].descriptionKey()));
            }
        }
        return List.of();
    }

    // --- Titles -----------------------------------------------------------------------

    private void renderTitles(GuiGraphics graphics, Minecraft minecraft, CultivationData data, Area area) {
        int y = titlesTop(area);
        graphics.drawString(minecraft.font, Component.translatable("murimcultivation.system.status.titles"),
                area.x(), y, SystemTheme.TEXT, false);
        y += ROW;

        List<ResourceLocation> owned = new ArrayList<>(data.systemProgress().titles());
        if (owned.isEmpty()) {
            graphics.drawString(minecraft.font,
                    Component.translatable("murimcultivation.system.status.no_titles"),
                    area.x(), y, SystemTheme.TEXT_DIM, false);
            return;
        }

        Optional<ResourceLocation> worn = data.systemProgress().equippedTitle();
        for (int i = 0; i < owned.size() && y < area.bottom() - ROW; i++) {
            ResourceLocation id = owned.get(i);
            boolean isWorn = worn.filter(id::equals).isPresent();
            Component name = titleOf(minecraft, id)
                    .map(Title::fullDisplayName)
                    .orElseGet(() -> Component.literal(id.getPath()));
            graphics.drawString(minecraft.font,
                    Component.literal(isWorn ? "◆ " : "◇ ").append(name),
                    area.x(), y, isWorn ? SystemTheme.TEXT_GOOD : SystemTheme.TEXT_DIM, false);
            y += ROW;
        }
    }

    private boolean clickTitle(CultivationData data, Area area, double mouseX, double mouseY) {
        int y = titlesTop(area) + ROW;
        List<ResourceLocation> owned = new ArrayList<>(data.systemProgress().titles());
        Optional<ResourceLocation> worn = data.systemProgress().equippedTitle();

        for (ResourceLocation id : owned) {
            if (SystemTheme.isWithin(mouseX, mouseY, area.x(), y, area.width() / 2, ROW)) {
                // Clicking the worn title takes it off; clicking another swaps to it.
                boolean isWorn = worn.filter(id::equals).isPresent();
                PacketDistributor.sendToServer(new EquipTitlePayload(
                        isWorn ? Optional.empty() : Optional.of(id)));
                return true;
            }
            y += ROW;
        }
        return false;
    }

    private static int titlesTop(Area area) {
        return area.y() + ROW * 2 + 18 * 3 + 8;
    }

    private static Optional<Title> titleOf(Minecraft minecraft, ResourceLocation id) {
        if (minecraft.level == null) {
            return Optional.empty();
        }
        return minecraft.level.registryAccess()
                .registryOrThrow(MurimRegistries.TITLE)
                .getOptional(ResourceKey.create(MurimRegistries.TITLE, id));
    }
}
