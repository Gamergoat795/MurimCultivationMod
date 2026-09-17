package com.andymods.murimcultivation.client.screen;

import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.item.MartialManualItem;
import com.andymods.murimcultivation.network.SetLoadoutPayload;
import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.technique.TechniqueMastery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

/**
 * Learned arts, their mastery, and which four are bound.
 *
 * <p>Clicking an unbound art binds it to the first free slot; clicking a bound one unbinds it.
 * Both send the whole new loadout rather than a delta, because the server filters the list to
 * arts the player actually knows — a single authoritative rebuild is simpler to reason about
 * than a set of incremental edits that could drift.
 */
@OnlyIn(Dist.CLIENT)
public class TechniquesTab implements SystemTab {

    private static final int ROW = 14;

    @Override
    public Component title() {
        return Component.translatable("murimcultivation.system.tab.techniques");
    }

    @Override
    public void render(GuiGraphics graphics, CultivationData data, Area area, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        graphics.drawString(minecraft.font, Component.translatable(
                        "murimcultivation.system.techniques.header",
                        data.techniqueMastery().size(), data.loadout().size(), CultivationData.LOADOUT_SIZE),
                area.x(), area.y(), SystemTheme.TEXT, false);

        List<ResourceLocation> known = knownSorted(data);
        if (known.isEmpty()) {
            graphics.drawString(minecraft.font,
                    Component.translatable("murimcultivation.system.techniques.none"),
                    area.x(), area.y() + ROW + 2, SystemTheme.TEXT_DIM, false);
            return;
        }

        int y = area.y() + ROW + 2;
        for (ResourceLocation id : known) {
            if (y > area.bottom() - ROW) {
                break;
            }
            renderRow(graphics, minecraft, data, area, id, y, mouseX, mouseY);
            y += ROW;
        }

        graphics.drawString(minecraft.font,
                Component.translatable("murimcultivation.system.techniques.hint"),
                area.x(), area.bottom() - 10, SystemTheme.TEXT_DIM, false);
    }

    private void renderRow(GuiGraphics graphics, Minecraft minecraft, CultivationData data, Area area,
                           ResourceLocation id, int y, int mouseX, int mouseY) {
        int slot = data.loadout().indexOf(id);
        boolean bound = slot >= 0;
        boolean hovered = SystemTheme.isWithin(mouseX, mouseY, area.x(), y, area.width(), ROW);

        if (hovered) {
            graphics.fill(area.x(), y, area.right(), y + ROW - 1, SystemTheme.PANEL_INNER);
        }

        // The slot number if bound, so the mapping to keys is visible here too.
        String marker = bound ? "[" + (slot + 1) + "]" : "[ ]";
        graphics.drawString(minecraft.font, marker, area.x(), y + 2,
                bound ? SystemTheme.TEXT_ACCENT : SystemTheme.TEXT_DIM, false);

        graphics.drawString(minecraft.font,
                Component.translatable(MartialManualItem.translationKeyFor(id)),
                area.x() + 22, y + 2, bound ? SystemTheme.TEXT : SystemTheme.TEXT_DIM, false);

        int mastery = data.techniqueMastery(id);
        String stars = stars(mastery);
        graphics.drawString(minecraft.font, stars,
                area.right() - minecraft.font.width(stars) - 30, y + 2, SystemTheme.TEXT_GOOD, false);
        String percent = mastery + "%";
        graphics.drawString(minecraft.font, percent,
                area.right() - minecraft.font.width(percent), y + 2, SystemTheme.TEXT_DIM, false);
    }

    @Override
    public List<Component> tooltipAt(CultivationData data, Area area, int mouseX, int mouseY) {
        ResourceLocation hovered = rowAt(data, area, mouseX, mouseY);
        if (hovered == null) {
            return List.of();
        }

        List<Component> lines = new ArrayList<>();
        Optional<Technique> technique = techniqueOf(hovered);
        lines.add(technique.map(Technique::fullDisplayName)
                .orElseGet(() -> Component.translatable(MartialManualItem.translationKeyFor(hovered))));

        int mastery = data.techniqueMastery(hovered);
        technique.ifPresent(value -> {
            lines.add(Component.translatable("murimcultivation.system.techniques.cost",
                    String.format(Locale.ROOT, "%.0f", TechniqueMastery.qiCost(value.qiCost(), mastery))));
            lines.add(Component.translatable("murimcultivation.system.techniques.cooldown",
                    String.format(Locale.ROOT, "%.1f",
                            TechniqueMastery.cooldownTicks(value.cooldownTicks(), mastery) / 20.0D)));
            lines.add(Component.translatable(value.hand().translationKey()));
        });
        lines.add(Component.translatable("murimcultivation.system.techniques.mastery", mastery));
        return lines;
    }

    @Override
    public boolean mouseClicked(CultivationData data, Area area, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        ResourceLocation clicked = rowAt(data, area, (int) mouseX, (int) mouseY);
        if (clicked == null) {
            return false;
        }

        List<ResourceLocation> loadout = new ArrayList<>(data.loadout());
        if (!loadout.remove(clicked) && loadout.size() < CultivationData.LOADOUT_SIZE) {
            loadout.add(clicked);
        }
        PacketDistributor.sendToServer(new SetLoadoutPayload(List.copyOf(loadout)));
        return true;
    }

    private ResourceLocation rowAt(CultivationData data, Area area, int mouseX, int mouseY) {
        List<ResourceLocation> known = knownSorted(data);
        int y = area.y() + ROW + 2;
        for (ResourceLocation id : known) {
            if (y > area.bottom() - ROW) {
                break;
            }
            if (SystemTheme.isWithin(mouseX, mouseY, area.x(), y, area.width(), ROW)) {
                return id;
            }
            y += ROW;
        }
        return null;
    }

    /** Bound arts first, in slot order, then the rest alphabetically — a stable, useful order. */
    private static List<ResourceLocation> knownSorted(CultivationData data) {
        List<ResourceLocation> ordered = new ArrayList<>(data.loadout());
        data.techniqueMastery().keySet().stream()
                .filter(id -> !ordered.contains(id))
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .forEach(ordered::add);
        return ordered;
    }

    private static Optional<Technique> techniqueOf(ResourceLocation id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return Optional.empty();
        }
        return minecraft.level.registryAccess()
                .registryOrThrow(MurimRegistries.TECHNIQUE)
                .getOptional(ResourceKey.create(MurimRegistries.TECHNIQUE, id));
    }

    private static String stars(int mastery) {
        int filled = TechniqueMastery.stars(mastery);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            builder.append(i < filled ? '★' : '☆');
        }
        return builder.toString();
    }
}
