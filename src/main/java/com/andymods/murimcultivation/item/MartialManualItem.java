package com.andymods.murimcultivation.item;

import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.registry.ModDataComponents;
import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.technique.TechniqueMastery;
import com.andymods.murimcultivation.technique.TechniqueService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * A martial arts manual. Which art it teaches comes from the stack's
 * {@link ModDataComponents#TECHNIQUE} component, not from the item class.
 *
 * <p>One item backs every manual in the game, which is what allows a datapack to define a new
 * technique and hand out a manual for it without writing Java. Reading an unknown art learns
 * it; re-reading one you already know deepens it, so duplicate manuals are worth keeping
 * rather than being dead weight.
 */
public class MartialManualItem extends Item {

    /** Mastery a re-read grants, before the diminishing-returns curve is applied. */
    private static final double REREAD_MASTERY = 6.0D;

    public MartialManualItem(Properties properties) {
        super(properties);
    }

    /** The technique a stack teaches, if it has been given one. */
    public static Optional<ResourceLocation> techniqueOf(ItemStack stack) {
        return Optional.ofNullable(stack.get(ModDataComponents.TECHNIQUE.get()));
    }

    /** Builds a manual stack for a technique. Used by commands, loot and the creative tab. */
    public static ItemStack forTechnique(Item item, ResourceLocation technique) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.TECHNIQUE.get(), technique);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        Optional<ResourceLocation> id = techniqueOf(stack);
        if (id.isEmpty()) {
            // A manual with no technique component: a datapack or command built it wrong.
            serverPlayer.displayClientMessage(
                    Component.translatable("murimcultivation.manual.blank"), true);
            return InteractionResultHolder.fail(stack);
        }

        CultivationData data = CultivationService.data(serverPlayer);
        if (!data.isAwakened()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("murimcultivation.manual.not_awakened"), true);
            return InteractionResultHolder.fail(stack);
        }

        Optional<Technique> technique = TechniqueService.byId(serverPlayer, id.get());
        if (technique.isEmpty()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("murimcultivation.manual.unknown_technique", id.get().toString()), true);
            return InteractionResultHolder.fail(stack);
        }

        boolean learned = TechniqueService.learn(serverPlayer, id.get(), technique.get());
        if (!learned && !deepenUnderstanding(serverPlayer, data, id.get(), technique.get())) {
            // Already known and already fully mastered: keep the book rather than waste it.
            serverPlayer.displayClientMessage(Component.translatable(
                    "murimcultivation.manual.nothing_left", technique.get().displayName()), true);
            return InteractionResultHolder.fail(stack);
        }

        level.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }

    /**
     * A re-read of a known manual advances mastery.
     *
     * @return false if there was nothing left to learn
     */
    private static boolean deepenUnderstanding(ServerPlayer player, CultivationData data,
                                               ResourceLocation id, Technique technique) {
        int before = data.techniqueMastery(id);
        if (before >= TechniqueMastery.MAX) {
            return false;
        }

        data.addTechniqueMasteryProgress(id, TechniqueMastery.gainFrom(before, REREAD_MASTERY));
        player.sendSystemMessage(Component.translatable("murimcultivation.manual.deepened",
                technique.displayName(), data.techniqueMastery(id)));
        CultivationService.syncToClient(player);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        Optional<ResourceLocation> id = techniqueOf(stack);
        if (id.isEmpty()) {
            tooltip.add(Component.translatable("murimcultivation.manual.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        // The technique registry is datapack content and is not reachable from a tooltip
        // without a level, so show the id and let the translated name come from lang.
        tooltip.add(Component.translatable("murimcultivation.manual.teaches",
                        Component.translatable(translationKeyFor(id.get())))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("murimcultivation.manual.use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * The conventional translation key for a technique id, matching what the shipped technique
     * JSONs declare. A datapack that follows the same convention gets working tooltips for free.
     */
    public static String translationKeyFor(ResourceLocation technique) {
        return "murimcultivation.technique." + technique.getPath();
    }
}
