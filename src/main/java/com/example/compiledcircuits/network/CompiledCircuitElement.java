package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class CompiledCircuitElement {

    private final CompoundTag stateData;
    private final boolean needsPersistenceUpgrade;
    private CompiledBlockStateCodec.DecodeResult decoded;
    private boolean diagnosticLogged;

    public boolean needsPersistenceUpgrade() { return needsPersistenceUpgrade; }
    public CompiledBlockStateCodec.DecodeResult resolveState() {
        if (decoded == null) {
            decoded = CompiledBlockStateCodec.readAndResolve(stateData, blockId);
            if (decoded.status() == CompiledBlockStateCodec.Status.EXACT) {
                String required = switch (blockId) {
                    case "compiledcircuits:input_endpoint" -> "INPUT";
                    case "compiledcircuits:output_endpoint" -> "OUTPUT";
                    case "compiledcircuits:basic_wire" -> "WIRE";
                    default -> type.name();
                };
                var block = decoded.state().orElseThrow().getBlock();
                boolean incompatible = type == CircuitElementType.INPUT && !(block instanceof com.example.compiledcircuits.block.InputEndpointBlock)
                        || type == CircuitElementType.OUTPUT && !(block instanceof com.example.compiledcircuits.block.OutputEndpointBlock);
                if (incompatible || !required.equals(type.name())) decoded = CompiledBlockStateCodec.invalid("Element type/block mismatch");
            }
        }
        return decoded;
    }
    public void logUnresolved(int networkId) {
        if (!diagnosticLogged && resolveState().status() == CompiledBlockStateCodec.Status.UNRESOLVED) {
            diagnosticLogged = true;
            org.slf4j.LoggerFactory.getLogger(CompiledCircuitElement.class).warn(
                    "Network {} element {} unresolved: {}", networkId, id, resolveState().reason());
        }
    }
    private static CompoundTag legacyData() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stateDataVersion", 1); tag.putString("stateOrigin", "LEGACY");
        return tag;
    }
    public CompiledCircuitElement(int id, BlockPos pos, CircuitElementType type, CompiledBlockStateSnapshot snapshot) {
        this(id, pos, type, snapshot.blockId(), CompiledBlockStateCodec.write(snapshot), false);
        if (resolveState().status() != CompiledBlockStateCodec.Status.EXACT) throw new IllegalArgumentException(resolveState().reason());
    }
    private CompiledCircuitElement(int id, BlockPos pos, CircuitElementType type, String blockId,
                                   CompoundTag data, boolean upgrade) {
        this.id = id; this.pos = pos.immutable(); this.type = type; this.blockId = blockId;
        this.stateData = data.copy(); this.needsPersistenceUpgrade = upgrade;
    }

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
        this(id, pos, type, blockId, legacyData(), false);
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

        tag.merge(stateData.copy());
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

        CompoundTag data = new CompoundTag();
        for (String key : new String[]{"stateDataVersion", "stateOrigin", "compiledBlockState"}) {
            if (tag.contains(key)) data.put(key, tag.get(key).copy());
        }
        boolean upgrade = data.isEmpty();
        return new CompiledCircuitElement(id, pos, type, tag.getString("blockId"),
                upgrade ? legacyData() : data, upgrade);
    }
}
