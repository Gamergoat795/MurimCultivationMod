package com.example.murimcultivation.network;

import com.example.murimcultivation.capability.QiCapabilityProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncQiDataPacket {
    private final int qi;
    private final int maxQi;
    private final int stage;
    private final boolean isMeditating;
    private final boolean hasLearned;

    public SyncQiDataPacket(int qi, int maxQi, int stage, boolean isMeditating, boolean hasLearned) {
        this.qi = qi;
        this.maxQi = maxQi;
        this.stage = stage;
        this.isMeditating = isMeditating;
        this.hasLearned = hasLearned;
    }

    public SyncQiDataPacket(FriendlyByteBuf buf) {
        this.qi = buf.readInt();
        this.maxQi = buf.readInt();
        this.stage = buf.readInt();
        this.isMeditating = buf.readBoolean();
        this.hasLearned = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(qi);
        buf.writeInt(maxQi);
        buf.writeInt(stage);
        buf.writeBoolean(isMeditating);
        buf.writeBoolean(hasLearned);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.getCapability(QiCapabilityProvider.QI_CAPABILITY).ifPresent(qiCap -> {
                    qiCap.setQi(qi);
                    qiCap.setMaxQi(maxQi);
                    qiCap.setStage(stage);
                    qiCap.setMeditating(isMeditating);
                    qiCap.setLearnedQiGathering(hasLearned);
                });
            }
        });
        return true;
    }
}