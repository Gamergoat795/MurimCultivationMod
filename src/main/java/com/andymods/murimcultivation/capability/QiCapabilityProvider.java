package com.andymods.murimcultivation.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QiCapabilityProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static Capability<QiCapability> QI_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    private QiCapability qiCapability = null;
    private final LazyOptional<QiCapability> optional = LazyOptional.of(this::createQiCapability);

    private QiCapability createQiCapability() {
        if (this.qiCapability == null) {
            this.qiCapability = new QiCapability();
        }
        return this.qiCapability;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == QI_CAPABILITY) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        createQiCapability().saveNBTData(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createQiCapability().loadNBTData(nbt);
    }
}