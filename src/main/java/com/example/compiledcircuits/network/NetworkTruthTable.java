package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import java.util.*;

/** Immutable persisted whole-network function. Rows contain only external output bits. */
final class NetworkTruthTable {
    static final int MAX_INPUTS = 16, MAX_BYTES = 4 * 1024 * 1024;
    static final long MAX_BUILD_WORK = 16_000_000;
    final String graph;
    final List<BlockPos> inputs, outputs;
    final List<BundleFace> faceInputs, faceOutputs;
    private final long[] rows;
    final int words;
    NetworkTruthTable(String graph, List<BlockPos> inputs, List<BlockPos> outputs, long[] rows) { this(graph, inputs, outputs, List.of(), List.of(), rows); }
    NetworkTruthTable(String graph, List<BlockPos> inputs, List<BlockPos> outputs, List<BundleFace> faceInputs, List<BundleFace> faceOutputs, long[] rows) {
        this.graph = graph; this.inputs = List.copyOf(inputs); this.outputs = List.copyOf(outputs);
        this.faceInputs = List.copyOf(faceInputs); this.faceOutputs = List.copyOf(faceOutputs);
        int inputCount = inputs.size() + faceInputs.size(), outputCount = outputs.size() + faceOutputs.size();
        this.words = (outputCount + 63) / 64;
        if (inputCount > MAX_INPUTS || outputCount == 0 || outputCount > LogicProgram.MAX_ELEMENTS
                || new HashSet<>(inputs).size() != inputs.size() || new HashSet<>(outputs).size() != outputs.size()
                || new HashSet<>(faceInputs).size() != faceInputs.size() || new HashSet<>(faceOutputs).size() != faceOutputs.size()
                || rows.length != (1L << inputCount) * words || (long) rows.length * 8 > MAX_BYTES)
            throw new IllegalArgumentException("Invalid or oversized network table");
        this.rows = rows.clone();
    }
    int bytes() { return rows.length * Long.BYTES; }
    boolean output(int key, int output) { return (rows[key * words + output / 64] & (1L << (output % 64))) != 0; }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("version", 2); tag.putString("graph", graph);
        tag.putLongArray("inputs", inputs.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("outputs", outputs.stream().mapToLong(BlockPos::asLong).toArray()); tag.putLongArray("rows", rows.clone());
        tag.put("faceInputs", saveFaces(faceInputs)); tag.put("faceOutputs", saveFaces(faceOutputs));
        return tag;
    }
    static NetworkTruthTable load(CompoundTag tag) {
        if ((tag.getInt("version") != 1 && tag.getInt("version") != 2) || !tag.contains("graph", Tag.TAG_STRING)
                || !(tag.get("inputs") instanceof LongArrayTag in) || in.size() > MAX_INPUTS
                || !(tag.get("outputs") instanceof LongArrayTag out) || out.size() > LogicProgram.MAX_ELEMENTS
                || !(tag.get("rows") instanceof LongArrayTag rows) || rows.size() > MAX_BYTES / 8)
            throw new IllegalArgumentException("Invalid saved network table");
        return new NetworkTruthTable(tag.getString("graph"), Arrays.stream(in.getAsLongArray()).mapToObj(BlockPos::of).toList(),
                Arrays.stream(out.getAsLongArray()).mapToObj(BlockPos::of).toList(), loadFaces(tag, "faceInputs", MAX_INPUTS), loadFaces(tag, "faceOutputs", LogicProgram.MAX_ELEMENTS), rows.getAsLongArray());
    }
    private static ListTag saveFaces(List<BundleFace> faces) {
        var list = new ListTag();
        for (var face : faces) { var tag = new CompoundTag(); tag.putLong("pos", face.pos().asLong()); tag.putInt("side", face.side().ordinal()); list.add(tag); }
        return list;
    }
    private static List<BundleFace> loadFaces(CompoundTag tag, String key, int limit) {
        if (tag.getInt("version") == 1) return List.of();
        if (!(tag.get(key) instanceof ListTag list) || list.size() > limit || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid saved table faces");
        var result = new ArrayList<BundleFace>();
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i); int side = entry.getInt("side");
            if (!entry.contains("pos", Tag.TAG_LONG) || !entry.contains("side", Tag.TAG_INT) || side < 0 || side >= 6) throw new IllegalArgumentException("Invalid table face");
            result.add(new BundleFace(BlockPos.of(entry.getLong("pos")), net.minecraft.core.Direction.values()[side]));
        }
        return result;
    }
    static final class Builder {
        final LogicProgram program;
        final List<BlockPos> inputs, outputs;
        final List<BundleFace> faceInputs, faceOutputs;
        final int total, words;
        final long[] rows;
        int next;
        long work;
        Builder(LogicProgram program) {
            this.program = program;
            if (program.hasStatefulMemory()) throw new IllegalArgumentException("Whole-network tables require no latches or feedback cycles");
            inputs = program.benchmarkInputs(); outputs = program.tableOutputs();
            faceInputs = program.tableFaceInputs(); faceOutputs = program.tableFaceOutputs();
            int inputCount = inputs.size() + faceInputs.size(), outputCount = outputs.size() + faceOutputs.size();
            if (inputCount > MAX_INPUTS) throw new IllegalArgumentException("Table limit: 16 independent inputs");
            if (outputCount == 0) throw new IllegalArgumentException("Network needs at least one output");
            total = 1 << inputCount; words = (outputCount + 63) / 64;
            if ((long) total * words * 8 > MAX_BYTES) throw new IllegalArgumentException("Table data exceeds 4 MiB");
            if ((long) total * program.fullWork() > MAX_BUILD_WORK) throw new IllegalArgumentException("Table construction exceeds 16000000 work units");
            rows = new long[total * words];
        }
        boolean step() {
            if (next == total) return true;
            Map<BlockPos,Boolean> values = new HashMap<>();
            for (int i = 0; i < inputs.size(); i++) values.put(inputs.get(i), (next & (1 << i)) != 0);
            Map<BundleFace, Boolean> faceValues = new HashMap<>();
            for (int i = 0; i < faceInputs.size(); i++) faceValues.put(faceInputs.get(i), (next & (1 << (inputs.size() + i))) != 0);
            var result = program.evaluate(values, faceValues); work += program.lastWork();
            if (work > MAX_BUILD_WORK) throw new IllegalArgumentException("Table construction work limit exceeded");
            for (int i = 0; i < outputs.size(); i++) if (result.getOrDefault(outputs.get(i), false)) rows[next * words + i / 64] |= 1L << (i % 64);
            for (int i = 0; i < faceOutputs.size(); i++) if (program.faceOutput(faceOutputs.get(i))) {
                int bit = outputs.size() + i; rows[next * words + bit / 64] |= 1L << (bit % 64);
            }
            return ++next == total;
        }
        NetworkTruthTable finish() {
            if (next != total) throw new IllegalStateException("Table is incomplete");
            return new NetworkTruthTable(program.graphSignature(), inputs, outputs, faceInputs, faceOutputs, rows);
        }
    }
}
