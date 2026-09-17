package com.andymods.murimcultivation.alchemy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The pill cauldron. Right-click with a herb to start it; right-click empty-handed to collect.
 *
 * <p>Deliberately no screen. Two interactions prove the whole brewing loop, and a UI built now
 * would be redesigned by the milestone that gives alchemy its real depth.
 */
public class CauldronBlock extends BaseEntityBlock {

    public static final MapCodec<CauldronBlock> CODEC = simpleCodec(CauldronBlock::new);

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 12.0D, 15.0D);

    public CauldronBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** BaseEntityBlock renders nothing by default, which would make the cauldron invisible. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CauldronBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        // Server-side only: brewing is authoritative state, and the client is told the result.
        return level.isClientSide() ? null
                : createTickerHelper(type, com.andymods.murimcultivation.registry.ModBlockEntities.CAULDRON.get(),
                        CauldronBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (!(level.getBlockEntity(pos) instanceof CauldronBlockEntity cauldron)) {
            return ItemInteractionResult.PASS;
        }

        if (cauldron.hasOutput()) {
            return collect(cauldron, player, level, pos);
        }
        if (cauldron.isBrewing()) {
            player.displayClientMessage(Component.translatable("murimcultivation.cauldron.brewing",
                    (int) Math.round(cauldron.progress() * 100.0D)), true);
            return ItemInteractionResult.CONSUME;
        }
        if (cauldron.tryInsert(stack)) {
            stack.shrink(1);
            player.displayClientMessage(Component.translatable("murimcultivation.cauldron.started"), true);
            return ItemInteractionResult.CONSUME;
        }

        player.displayClientMessage(Component.translatable("murimcultivation.cauldron.no_formula"), true);
        return ItemInteractionResult.CONSUME;
    }

    /** Empty-handed use: collect a finished pill. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                                  Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return net.minecraft.world.InteractionResult.sidedSuccess(true);
        }
        if (!(level.getBlockEntity(pos) instanceof CauldronBlockEntity cauldron)) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        if (cauldron.hasOutput()) {
            collect(cauldron, player, level, pos);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        player.displayClientMessage(cauldron.isBrewing()
                        ? Component.translatable("murimcultivation.cauldron.brewing",
                        (int) Math.round(cauldron.progress() * 100.0D))
                        : Component.translatable("murimcultivation.cauldron.empty"), true);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    private ItemInteractionResult collect(CauldronBlockEntity cauldron, Player player, Level level, BlockPos pos) {
        ItemStack pill = cauldron.takeOutput();
        if (!player.getInventory().add(pill)) {
            player.drop(pill, false);
        }
        player.displayClientMessage(Component.translatable("murimcultivation.cauldron.collected",
                pill.getHoverName()), true);
        return ItemInteractionResult.SUCCESS;
    }

    /** Breaking the cauldron returns whatever was inside rather than eating it. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CauldronBlockEntity cauldron) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                    cauldron.input());
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                    cauldron.takeOutput());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
