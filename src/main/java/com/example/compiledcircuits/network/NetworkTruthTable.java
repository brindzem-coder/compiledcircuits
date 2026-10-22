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
    private final long[] rows;
    final int words;
    NetworkTruthTable(String graph, List<BlockPos> inputs, List<BlockPos> outputs, long[] rows) {
        this.graph = graph; this.inputs = List.copyOf(inputs); this.outputs = List.copyOf(outputs);
        this.words = (outputs.size() + 63) / 64;
        if (inputs.size() > MAX_INPUTS || outputs.isEmpty() || outputs.size() > LogicProgram.MAX_ELEMENTS
                || new HashSet<>(inputs).size() != inputs.size() || new HashSet<>(outputs).size() != outputs.size()
                || rows.length != (1L << inputs.size()) * words || (long) rows.length * 8 > MAX_BYTES)
            throw new IllegalArgumentException("Invalid or oversized network table");
        this.rows = rows.clone();
    }
    int bytes() { return rows.length * Long.BYTES; }
    boolean output(int key, int output) { return (rows[key * words + output / 64] & (1L << (output % 64))) != 0; }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putString("graph", graph);
        tag.putLongArray("inputs", inputs.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("outputs", outputs.stream().mapToLong(BlockPos::asLong).toArray()); tag.putLongArray("rows", rows.clone());
        return tag;
    }
    static NetworkTruthTable load(CompoundTag tag) {
        if (tag.getInt("version") != 1 || !tag.contains("graph", Tag.TAG_STRING)
                || !(tag.get("inputs") instanceof LongArrayTag in) || in.size() > MAX_INPUTS
                || !(tag.get("outputs") instanceof LongArrayTag out) || out.size() > LogicProgram.MAX_ELEMENTS
                || !(tag.get("rows") instanceof LongArrayTag rows) || rows.size() > MAX_BYTES / 8)
            throw new IllegalArgumentException("Invalid saved network table");
        return new NetworkTruthTable(tag.getString("graph"), Arrays.stream(in.getAsLongArray()).mapToObj(BlockPos::of).toList(),
                Arrays.stream(out.getAsLongArray()).mapToObj(BlockPos::of).toList(), rows.getAsLongArray());
    }
    static final class Builder {
        final LogicProgram program;
        final List<BlockPos> inputs, outputs;
        final int total, words;
        final long[] rows;
        int next;
        long work;
        Builder(LogicProgram program) {
            this.program = program;
            if (program.hasStatefulMemory()) throw new IllegalArgumentException("Whole-network tables require no latches or feedback cycles");
            inputs = program.benchmarkInputs(); outputs = program.tableOutputs();
            if (inputs.size() > MAX_INPUTS) throw new IllegalArgumentException("Table limit: 16 independent inputs");
            if (outputs.isEmpty()) throw new IllegalArgumentException("Network needs at least one output");
            total = 1 << inputs.size(); words = (outputs.size() + 63) / 64;
            if ((long) total * words * 8 > MAX_BYTES) throw new IllegalArgumentException("Table data exceeds 4 MiB");
            if ((long) total * program.fullWork() > MAX_BUILD_WORK) throw new IllegalArgumentException("Table construction exceeds 16000000 work units");
            rows = new long[total * words];
        }
        boolean step() {
            if (next == total) return true;
            Map<BlockPos,Boolean> values = new HashMap<>();
            for (int i = 0; i < inputs.size(); i++) values.put(inputs.get(i), (next & (1 << i)) != 0);
            var result = program.evaluate(values); work += program.lastWork();
            if (work > MAX_BUILD_WORK) throw new IllegalArgumentException("Table construction work limit exceeded");
            for (int i = 0; i < outputs.size(); i++) if (result.getOrDefault(outputs.get(i), false)) rows[next * words + i / 64] |= 1L << (i % 64);
            return ++next == total;
        }
        NetworkTruthTable finish() {
            if (next != total) throw new IllegalStateException("Table is incomplete");
            return new NetworkTruthTable(program.graphSignature(), inputs, outputs, rows);
        }
    }
}
