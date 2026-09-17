package com.andymods.murimcultivation.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A spirit vein: a place where the earth's Qi surfaces.
 *
 * <p>Standing near one raises the ambient Qi for cultivation, which {@link QiSources} applies.
 * It exists as a placeable block rather than only as generated terrain so that the mechanic is
 * usable and testable now, before there is any worldgen to place it — and so that a server can
 * build a cultivation chamber deliberately.
 *
 * <p><strong>A skeleton.</strong> There is no generation, no depletion, and no way to find one
 * short of creative mode. Those are the next milestone's work.
 */
public class SpiritVeinBlock extends Block {

    public SpiritVeinBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // A slow upward drift, so a vein reads as active without being a light source.
        if (random.nextInt(4) != 0) {
            return;
        }
        level.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.3D + random.nextDouble() * 0.4D,
                pos.getY() + 1.0D,
                pos.getZ() + 0.3D + random.nextDouble() * 0.4D,
                0.0D, 0.02D + random.nextDouble() * 0.02D, 0.0D);
    }
}
