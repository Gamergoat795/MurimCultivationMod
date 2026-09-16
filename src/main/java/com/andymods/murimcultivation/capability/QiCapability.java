package com.andymods.murimcultivation.capability;

import net.minecraft.nbt.CompoundTag;

public class QiCapability {
    private int qi = 0;
    private int maxQi = 100;
    private int stage = 0; // 0 = Third Rate, 1 = Second Rate, 2 = First Rate
    private boolean hasLearnedQiGathering = false;
    private boolean isMeditating = false;

    public int getQi() {
        return qi;
    }

    public void setQi(int qi) {
        this.qi = Math.max(0, Math.min(qi, maxQi));
    }

    public void addQi(int amount) {
        this.qi = Math.min(qi + amount, maxQi);
    }

    public int getMaxQi() {
        return maxQi;
    }

    public void setMaxQi(int maxQi) {
        this.maxQi = maxQi;
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public boolean hasLearnedQiGathering() {
        return hasLearnedQiGathering;
    }

    public void setLearnedQiGathering(boolean learned) {
        this.hasLearnedQiGathering = learned;
    }

    public boolean isMeditating() {
        return isMeditating;
    }

    public void setMeditating(boolean meditating) {
        this.isMeditating = meditating;
    }

    public String getStageName() {
        switch (stage) {
            case 0: return "Third Rate";
            case 1: return "Second Rate";
            case 2: return "First Rate";
            default: return "Unknown";
        }
    }

    public void breakthrough() {
        if (qi >= maxQi && stage < 2) {
            stage++;
            qi = 0;
            maxQi += 50;
        }
    }

    public void saveNBTData(CompoundTag nbt) {
        nbt.putInt("qi", qi);
        nbt.putInt("maxQi", maxQi);
        nbt.putInt("stage", stage);
        nbt.putBoolean("hasLearnedQiGathering", hasLearnedQiGathering);
        nbt.putBoolean("isMeditating", isMeditating);
    }

    public void loadNBTData(CompoundTag nbt) {
        qi = nbt.getInt("qi");
        maxQi = nbt.getInt("maxQi");
        stage = nbt.getInt("stage");
        hasLearnedQiGathering = nbt.getBoolean("hasLearnedQiGathering");
        isMeditating = nbt.getBoolean("isMeditating");
    }

    public void copyFrom(QiCapability source) {
        this.qi = source.qi;
        this.maxQi = source.maxQi;
        this.stage = source.stage;
        this.hasLearnedQiGathering = source.hasLearnedQiGathering;
        this.isMeditating = source.isMeditating;
    }
}