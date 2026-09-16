package com.andymods.murimcultivation.item;

import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.Realm;
import com.andymods.murimcultivation.cultivation.RealmProgression;
import com.andymods.murimcultivation.cultivation.Substage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
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
import java.util.Optional;

/**
 * The manual that makes an ordinary person a cultivator.
 *
 * <p>Reading it is the origin beat of the whole mod: before it you have no Qi, no HUD and no
 * techniques; after it you are standing on the bottom rung of the Murim with everything ahead
 * of you. The book is consumed, because absorbing its contents is the point.
 */
public class QiGatheringManualItem extends Item {

    public QiGatheringManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // All state changes are server-side; the client just gets told what happened.
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        CultivationData data = CultivationService.data(serverPlayer);
        if (data.isAwakened()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("murimcultivation.message.already_awakened"), true);
            return InteractionResultHolder.fail(stack);
        }

        Registry<Realm> registry = CultivationService.realmRegistry(serverPlayer);
        Optional<Holder.Reference<Realm>> lowest = RealmProgression.lowest(registry);
        if (lowest.isEmpty()) {
            // A datapack has removed every realm; there is nowhere to place the player.
            serverPlayer.displayClientMessage(
                    Component.translatable("murimcultivation.message.no_realms_defined"), true);
            return InteractionResultHolder.fail(stack);
        }

        data.setAwakened(true);
        data.setSubstage(Substage.EARLY);
        CultivationService.setRealm(serverPlayer, lowest.get().key(), Substage.EARLY);

        serverPlayer.sendSystemMessage(Component.translatable("murimcultivation.message.awakened",
                lowest.get().value().fullDisplayName()));
        level.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);

        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("murimcultivation.tooltip.qi_gathering_manual")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("murimcultivation.tooltip.qi_gathering_manual.use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
