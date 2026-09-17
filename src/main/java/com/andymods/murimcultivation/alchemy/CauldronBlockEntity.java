package com.andymods.murimcultivation.alchemy;

import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * A pill cauldron: one herb in, one pill out, after a wait.
 *
 * <p>No inventory screen and no hopper automation, on purpose. This is the skeleton of an
 * alchemy system, and a right-click to insert plus a right-click to collect is enough to prove
 * the loop works end to end without committing to a UI that a later milestone will redesign
 * anyway.
 */
public class CauldronBlockEntity extends BlockEntity {

    private ItemStack input = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    private int brewTicks;
    private int brewTotal;

    public CauldronBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAULDRON.get(), pos, state);
    }

    /** Advances brewing. Registered as the block's ticker. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CauldronBlockEntity cauldron) {
        if (cauldron.input.isEmpty() || !cauldron.output.isEmpty()) {
            return;
        }

        Optional<PillRecipe> recipe = cauldron.recipeFor(cauldron.input);
        if (recipe.isEmpty()) {
            return;
        }

        cauldron.brewTicks++;
        if (cauldron.brewTicks % 20 == 0 && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.BUBBLE_POP,
                    pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D,
                    3, 0.2D, 0.0D, 0.2D, 0.01D);
        }

        if (cauldron.brewTicks >= recipe.get().brewTicks()) {
            cauldron.output = recipe.get().createResult();
            cauldron.input = ItemStack.EMPTY;
            cauldron.brewTicks = 0;
            cauldron.brewTotal = 0;
            level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.7F, 1.2F);
            cauldron.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /** Why a herb was or was not accepted. Every case has something to say to the player. */
    public enum Insertion {
        ACCEPTED,
        BUSY,
        NO_FORMULA,
        NOT_ENOUGH_PURITY,
    }

    /**
     * Puts a herb in, if the cauldron is idle, a formula exists for it, and the brewer can pay
     * the formula's purity cost.
     *
     * <p>Purity is charged here rather than on collection, so abandoning a brew does not refund
     * it. Committing the herb is the decision; walking away is not a way out of it.
     */
    public Insertion tryInsert(ItemStack stack, ServerPlayer brewer) {
        if (!input.isEmpty() || !output.isEmpty() || level == null) {
            return Insertion.BUSY;
        }
        Optional<PillRecipe> recipe = recipeFor(stack);
        if (recipe.isEmpty()) {
            return Insertion.NO_FORMULA;
        }

        double cost = recipe.get().purityCost();
        CultivationData data = CultivationService.data(brewer);
        // The floor is what keeps refining a real cost: you cannot brew your foundation away to
        // nothing, so a run of pills eventually requires meditating the purity back first.
        if (cost > 0.0D && data.purity() - cost < CultivationData.MIN_PURITY) {
            return Insertion.NOT_ENOUGH_PURITY;
        }
        if (cost > 0.0D) {
            data.addPurity(-cost);
            CultivationService.syncValuesToClient(brewer);
        }

        input = stack.copyWithCount(1);
        brewTicks = 0;
        brewTotal = recipe.get().brewTicks();
        setChanged();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        return Insertion.ACCEPTED;
    }

    /** The purity a herb would cost to brew, for a message before anything is spent. */
    public double purityCostOf(ItemStack stack) {
        return recipeFor(stack).map(PillRecipe::purityCost).orElse(0.0D);
    }

    /** Takes the finished pill, leaving the cauldron idle. */
    public ItemStack takeOutput() {
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = output;
        output = ItemStack.EMPTY;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
        return taken;
    }

    public boolean isBrewing() {
        return !input.isEmpty() && output.isEmpty();
    }

    public boolean hasOutput() {
        return !output.isEmpty();
    }

    /** Brewing progress, 0..1, for particles and a future progress readout. */
    public double progress() {
        return brewTotal <= 0 ? 0.0D : Math.min(1.0D, brewTicks / (double) brewTotal);
    }

    /** The formula for a herb, or empty if nothing brews it. */
    public Optional<PillRecipe> recipeFor(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return Optional.empty();
        }
        for (PillRecipe recipe : level.registryAccess().registryOrThrow(MurimRegistries.PILL_RECIPE)) {
            if (recipe.matches(stack)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    /** The herb currently being worked, for the block's drop-on-break behaviour. */
    public ItemStack input() {
        return input;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains("Input")
                ? ItemStack.parse(registries, tag.getCompound("Input")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        output = tag.contains("Output")
                ? ItemStack.parse(registries, tag.getCompound("Output")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        brewTicks = tag.getInt("BrewTicks");
        brewTotal = tag.getInt("BrewTotal");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!input.isEmpty()) {
            tag.put("Input", (CompoundTag) input.save(registries, new CompoundTag()));
        }
        if (!output.isEmpty()) {
            tag.put("Output", (CompoundTag) output.save(registries, new CompoundTag()));
        }
        tag.putInt("BrewTicks", brewTicks);
        tag.putInt("BrewTotal", brewTotal);
    }

    /** Sent to clients so the block's particles and future readout match the server. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
