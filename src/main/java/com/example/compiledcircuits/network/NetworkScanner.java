package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import java.util.Set;

/** Compatibility value for indexed conflict queries. Compilation uses CompilationJobs exclusively. */
public final class NetworkScanner {
    private NetworkScanner() {}
    public record ScanResult(Set<BlockPos> wires, Set<BlockPos> inputs, Set<BlockPos> outputs, boolean success) {
        public int totalSize() { return wires.size() + inputs.size() + outputs.size(); }
    }
}
