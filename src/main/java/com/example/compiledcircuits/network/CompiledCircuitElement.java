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
                    case "compiledcircuits:input_endpoint", "compiledcircuits:signal_switch" -> "INPUT";
                    case "compiledcircuits:output_endpoint", "compiledcircuits:signal_lamp" -> "OUTPUT";
                    case "compiledcircuits:basic_wire", "compiledcircuits:network_wire", "compiledcircuits:id_wire",
                    "compiledcircuits:bundled_wire", "compiledcircuits:bundle_input", "compiledcircuits:bundle_output",
                    "compiledcircuits:bundle_network_input", "compiledcircuits:bundle_network_output" -> "WIRE";
                    case "compiledcircuits:and_gate", "compiledcircuits:or_gate", "compiledcircuits:not_gate", "compiledcircuits:d_latch",
                        "compiledcircuits:nand_gate", "compiledcircuits:nor_gate", "compiledcircuits:xor_gate", "compiledcircuits:xnor_gate" -> "GATE";
                    case "compiledcircuits:gate_input" -> "GATE_INPUT";
                    case "compiledcircuits:gate_output" -> "GATE_OUTPUT";
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

    public CompoundTag crossingLanes() { return stateData.getCompound("crossingLanes").copy(); }
    public CompoundTag bundleFaces() { return stateData.getCompound("bundleFaces").copy(); }
    public String channelId() { return stateData.getString("channelId"); }
    public String wireId() { return stateData.getString("wireId"); }
    public int wireColor() { return stateData.getInt("wireColor"); }
    public CompiledCircuitElement captureWire(net.minecraft.world.level.BlockGetter level) {
        if (level.getBlockEntity(pos) instanceof com.example.compiledcircuits.block.CrossingWireEntity wire)
            stateData.put("crossingLanes", wire.snapshot(Crossings.mask(this)));
        if (level.getBlockEntity(pos) instanceof com.example.compiledcircuits.block.IdWireEntity wire) {
            String id = com.example.compiledcircuits.network.WireIds.normalize(wire.wireId());
            if (id.equals("0")) throw new IllegalArgumentException("Unconfigured wire");
            stateData.putString("wireId", id); stateData.putInt("wireColor", wire.color());
        }
        if (level.getBlockEntity(pos) instanceof com.example.compiledcircuits.block.BundlePortEntity port)
            stateData.putString("channelId", WireIds.normalize(port.channel()));
        if (level.getBlockEntity(pos) instanceof com.example.compiledcircuits.block.BundleWireEntity wire)
            stateData.put("bundleFaces", wire.portData());
        if (blockId.equals("compiledcircuits:id_wire") && wireId().isEmpty()) throw new IllegalArgumentException("Unconfigured ID wire");
        return this;
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
        for (String key : new String[]{"stateDataVersion", "stateOrigin", "compiledBlockState", "wireId", "wireColor", "channelId", "bundleFaces", "crossingLanes"}) {
            if (tag.contains(key)) data.put(key, tag.get(key).copy());
        }
        boolean upgrade = data.isEmpty();
        var element = new CompiledCircuitElement(id, pos, type, tag.getString("blockId"),
                upgrade ? legacyData() : data, upgrade);
        if (element.blockId.equals("compiledcircuits:id_wire")) {
            try {
                String wireId = WireIds.normalize(element.wireId());
                if (wireId.equals("0") || !wireId.equals(element.wireId())) throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) { element.decoded = CompiledBlockStateCodec.invalid("Missing or invalid wire ID"); }
        }
        if (element.blockId.startsWith("compiledcircuits:bundle_")) {
            try { if (!WireIds.normalize(element.channelId()).equals(element.channelId())) throw new IllegalArgumentException(); }
            catch (IllegalArgumentException invalid) { element.decoded = CompiledBlockStateCodec.invalid("Missing or invalid bundle channel"); }
        }
        if (element.blockId.equals("compiledcircuits:bundled_wire")) {
            try { com.example.compiledcircuits.block.BundleWireEntity.decode(element.bundleFaces()); }
            catch (IllegalArgumentException invalid) { element.decoded = CompiledBlockStateCodec.invalid("Invalid bundle face data"); }
        }
        if(element.blockId.equals("compiledcircuits:crossing_wire")){
            var state=element.resolveState().state().orElse(null);
            int mask=state==null?0:com.example.compiledcircuits.block.CrossingWireBlock.mask(state);
            boolean valid=mask!=0&&Integer.bitCount(mask)<=2&&element.type==CircuitElementType.WIRE;
            if(state!=null)for(var axis:net.minecraft.core.Direction.Axis.values())if(com.example.compiledcircuits.block.CrossingWireBlock.kind(state,axis)==2){
                String laneId=element.crossingLanes().getCompound(axis.getName()).getString("id");try{valid&=!laneId.equals("0")&&WireIds.normalize(laneId).equals(laneId);}catch(IllegalArgumentException error){valid=false;}
            }
            if(!valid)element.decoded=CompiledBlockStateCodec.invalid("Invalid crossing lane data");
        }
        return element;
    }
}
