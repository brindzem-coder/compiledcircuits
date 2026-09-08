package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class CompiledCircuitElement {

    private final int id;
    private final BlockPos pos;
    private final CircuitElementType type;
    private final String blockId;

    public CompiledCircuitElement(
            int id,
            BlockPos pos,
            CircuitElementType type,
            String blockId
    ) {
        this.id = id;
        this.pos = pos.immutable();
        this.type = type;
        this.blockId = blockId;
    }

    public int getId() {
        return id;
    }

    public BlockPos getPos() {
        return pos;
    }

    public CircuitElementType getType() {
        return type;
    }

    public String getBlockId() {
        return blockId;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putInt("id", id);
        tag.putLong("pos", pos.asLong());
        tag.putString("type", type.name());
        tag.putString("blockId", blockId);

        return tag;
    }

    public static CompiledCircuitElement load(CompoundTag tag) {
        int id = tag.getInt("id");
        BlockPos pos = BlockPos.of(tag.getLong("pos"));

        CircuitElementType type;
        try {
            type = CircuitElementType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            type = CircuitElementType.OTHER;
        }

        return new CompiledCircuitElement(
                id,
                pos,
                type,
                tag.getString("blockId")
        );
    }
}
