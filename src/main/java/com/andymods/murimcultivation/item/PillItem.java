package com.andymods.murimcultivation.item;

import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.DeviationService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import java.util.function.BiPredicate;

/**
 * A refined pill. What it does is chosen at registration from {@link Effect}, so adding a new
 * pill is a registration rather than a class.
 *
 * <p>Every effect ties into a system that already exists — Qi, purity, Qi Deviation — rather
 * than inventing a parallel one. That is what makes alchemy worth having even as a skeleton:
 * a Deviation Remedy is the answer to the failure state M2 introduced, which until now could
 * only be waited out.
 */
public class PillItem extends Item {

    /** What swallowing a pill does. Returning false means it was not needed and is not consumed. */
    public enum Effect {
        /** Restores a fraction of maximum Qi. */
        QI_RECOVERY("qi_recovery", (player, amount) -> {
            CultivationData data = CultivationService.data(player);
            double capacity = CultivationService.qiCapacity(player);
            if (data.qi() >= capacity) {
                return false;
            }
            data.addQi(capacity * amount, capacity);
            CultivationService.syncValuesToClient(player);
            return true;
        }),

        /** Clears a Qi Deviation outright — the intended answer to a failed breakthrough. */
        DEVIATION_REMEDY("deviation_remedy", (player, amount) ->
                DeviationService.cure(player)),

        /** Refines the foundation directly. Expensive to make, by design. */
        PURITY("purity", (player, amount) -> {
            CultivationData data = CultivationService.data(player);
            if (data.purity() >= CultivationData.MAX_PURITY) {
                return false;
            }
            data.addPurity(amount);
            CultivationService.syncValuesToClient(player);
            return true;
        });

        private final String id;
        private final BiPredicate<ServerPlayer, Double> action;

        Effect(String id, BiPredicate<ServerPlayer, Double> action) {
            this.id = id;
            this.action = action;
        }

        public String descriptionKey() {
            return "murimcultivation.pill." + id + ".description";
        }

        public String wastedKey() {
            return "murimcultivation.pill." + id + ".wasted";
        }
    }

    /**
     * How much purity a Foundation Pill returns.
     *
     * <p>Lives here rather than inline at its registration because the brewing cost in
     * {@code pill_recipe/foundation_pill.json} has to stay above it — below it and brew-and-swallow
     * becomes a free climb to full purity. A test pins that relationship, and it can only pin it
     * against a number it shares.
     */
    public static final double FOUNDATION_PURITY_GRANT = 5.0D;

    private final Effect effect;
    private final double amount;

    public PillItem(Properties properties, Effect effect, double amount) {
        super(properties);
        this.effect = effect;
        this.amount = amount;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!CultivationService.data(serverPlayer).isAwakened()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("murimcultivation.pill.not_awakened"), true);
            return InteractionResultHolder.fail(stack);
        }

        // Refusing rather than consuming means a pill taken by mistake is not simply lost.
        if (!effect.action.test(serverPlayer, amount)) {
            serverPlayer.displayClientMessage(Component.translatable(effect.wastedKey()), true);
            return InteractionResultHolder.fail(stack);
        }

        level.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6F, 1.4F);
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(effect.descriptionKey(), formatAmount())
                .withStyle(ChatFormatting.GRAY));
    }

    private String formatAmount() {
        // Qi recovery is a fraction of capacity; the others are flat values.
        return effect == Effect.QI_RECOVERY
                ? Math.round(amount * 100.0D) + "%"
                : String.valueOf(Math.round(amount));
    }
}
