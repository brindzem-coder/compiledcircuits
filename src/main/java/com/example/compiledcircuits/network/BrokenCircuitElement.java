package com.example.compiledcircuits.network;

import net.minecraft.nbt.CompoundTag;

public final class BrokenCircuitElement {
    private final int elementId;
    private final String actualBlockId;
    private final long detectedAt;

    public BrokenCircuitElement(int elementId, String actualBlockId, long detectedAt) {
        this.elementId = elementId;
        this.actualBlockId = actualBlockId;
        this.detectedAt = detectedAt;
    }

    public int getElementId() { return elementId; }
    public String getActualBlockId() { return actualBlockId; }
    public long getDetectedAt() { return detectedAt; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("elementId", elementId);
        tag.putString("actualBlockId", actualBlockId);
        tag.putLong("detectedAt", detectedAt);
        return tag;
    }

    public static BrokenCircuitElement load(CompoundTag tag) {
        return new BrokenCircuitElement(tag.getInt("elementId"),
                tag.getString("actualBlockId"), tag.getLong("detectedAt"));
    }
}
