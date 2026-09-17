package com.andymods.murimcultivation.client.screen;

import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.system.QuestCategory;
import com.andymods.murimcultivation.system.QuestObjective;
import com.andymods.murimcultivation.system.QuestReward;
import com.andymods.murimcultivation.system.QuestTracker;
import com.andymods.murimcultivation.system.SystemQuest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * What the System is asking for, with live progress.
 *
 * <p>Progress comes from {@link QuestTracker#currentValue}, the same method the server uses to
 * decide completion. That is why those queries take a {@code Player} rather than a
 * {@code ServerPlayer}: a client-side reimplementation would be a second source of truth, and
 * the two would eventually disagree about whether a quest is done.
 */
@OnlyIn(Dist.CLIENT)
public class QuestsTab implements SystemTab {

    private static final int TITLE_ROW = 13;
    private static final int OBJECTIVE_ROW = 11;
    private static final int QUEST_GAP = 4;

    private int scroll;

    @Override
    public Component title() {
        return Component.translatable("murimcultivation.system.tab.quests");
    }

    @Override
    public void onShown() {
        scroll = 0;
    }

    @Override
    public void render(GuiGraphics graphics, CultivationData data, Area area, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        List<ResourceLocation> available = QuestTracker.available(minecraft.player);
        graphics.drawString(minecraft.font, Component.translatable(
                        "murimcultivation.system.quests.header",
                        available.size(), data.questLog().completed().size()),
                area.x(), area.y(), SystemTheme.TEXT, false);

        if (available.isEmpty()) {
            graphics.drawString(minecraft.font,
                    Component.translatable("murimcultivation.system.quests.none"),
                    area.x(), area.y() + TITLE_ROW + 2, SystemTheme.TEXT_DIM, false);
            return;
        }

        int y = area.y() + TITLE_ROW + 2 - scroll;
        for (ResourceLocation id : available) {
            SystemQuest quest = minecraft.level.registryAccess()
                    .registryOrThrow(MurimRegistries.QUEST)
                    .get(ResourceKey.create(MurimRegistries.QUEST, id));
            if (quest == null) {
                continue;
            }
            y = renderQuest(graphics, minecraft, data, area, id, quest, y);
            if (y > area.bottom()) {
                break;
            }
        }
    }

    private int renderQuest(GuiGraphics graphics, Minecraft minecraft, CultivationData data, Area area,
                            ResourceLocation id, SystemQuest quest, int y) {
        // Clip by hand: rows scrolled above the content area must not draw over the header.
        if (y + TITLE_ROW > area.y() && y < area.bottom()) {
            int accent = quest.category() == QuestCategory.DAILY
                    ? SystemTheme.TEXT_ACCENT : SystemTheme.TEXT_WARN;
            graphics.drawString(minecraft.font, quest.displayName(), area.x() + 4, y, accent, false);
            String tag = "[" + Component.translatable(quest.category().translationKey()).getString() + "]";
            graphics.drawString(minecraft.font, tag,
                    area.right() - minecraft.font.width(tag), y, SystemTheme.TEXT_DIM, false);
        }
        y += TITLE_ROW;

        for (QuestObjective objective : quest.objectives()) {
            if (y + OBJECTIVE_ROW > area.y() && y < area.bottom()) {
                int current = QuestTracker.currentValue(minecraft.player, data, id, objective);
                boolean done = current >= objective.amount();

                graphics.drawString(minecraft.font,
                        Component.literal(done ? "✔ " : "• ").append(objective.description()),
                        area.x() + 10, y, done ? SystemTheme.TEXT_GOOD : SystemTheme.TEXT_DIM, false);

                String count = current + "/" + objective.amount();
                graphics.drawString(minecraft.font, count,
                        area.right() - minecraft.font.width(count), y,
                        done ? SystemTheme.TEXT_GOOD : SystemTheme.TEXT_DIM, false);

                SystemTheme.bar(graphics, area.x() + 10, y + 9, area.width() - 20, 2,
                        objective.amount() <= 0 ? 0.0D : current / (double) objective.amount(),
                        done ? SystemTheme.TEXT_GOOD : SystemTheme.TEXT_ACCENT);
            }
            y += OBJECTIVE_ROW + 2;
        }

        return y + QUEST_GAP;
    }

    @Override
    public List<Component> tooltipAt(CultivationData data, Area area, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !SystemTheme.isWithin(mouseX, mouseY, area.x(), area.y(), area.width(), area.height())) {
            return List.of();
        }

        // Find which quest block the cursor is in, walking the same layout render uses.
        int y = area.y() + TITLE_ROW + 2 - scroll;
        for (ResourceLocation id : QuestTracker.available(minecraft.player)) {
            SystemQuest quest = minecraft.level.registryAccess()
                    .registryOrThrow(MurimRegistries.QUEST)
                    .get(ResourceKey.create(MurimRegistries.QUEST, id));
            if (quest == null) {
                continue;
            }
            int height = TITLE_ROW + quest.objectives().size() * (OBJECTIVE_ROW + 2) + QUEST_GAP;
            if (SystemTheme.isWithin(mouseX, mouseY, area.x(), y, area.width(), height)) {
                List<Component> lines = new ArrayList<>();
                lines.add(quest.displayName());
                lines.add(quest.description());
                if (!quest.rewards().isEmpty()) {
                    lines.add(Component.translatable("murimcultivation.system.quests.rewards"));
                    for (QuestReward reward : quest.rewards()) {
                        lines.add(Component.literal("  ").append(reward.description()));
                    }
                }
                return lines;
            }
            y += height;
        }
        return List.of();
    }

    /** Scrolls the list. The frame forwards wheel events here. */
    public void scrollBy(int amount, Area area, int contentHeight) {
        int maximum = Math.max(0, contentHeight - area.height());
        scroll = Math.max(0, Math.min(maximum, scroll + amount));
    }
}
