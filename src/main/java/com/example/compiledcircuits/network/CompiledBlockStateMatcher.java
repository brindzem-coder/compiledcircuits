package com.example.compiledcircuits.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
public final class CompiledBlockStateMatcher {
    public enum Match { MATCH, BLOCK_MISMATCH, STATE_MISMATCH, UNRESOLVED }
    private CompiledBlockStateMatcher() {}
    public static Match match(CompiledCircuitElement element, net.minecraft.world.level.BlockGetter level, BlockState actual, boolean exact) {
        var result = match(element, actual, exact);
        if (result == Match.MATCH && actual.getBlock() instanceof com.example.compiledcircuits.block.IdWireBlock
                && !element.wireId().equals(com.example.compiledcircuits.block.IdWireBlock.id(level, element.getPos(), actual))) return Match.STATE_MISMATCH;
        return result;
    }
    public static Match match(CompiledCircuitElement element, BlockState actual, boolean exact) {
        if ((element.getBlockId().equals("compiledcircuits:id_wire") || actual.getBlock() instanceof com.example.compiledcircuits.block.IdWireBlock)
                && !element.getBlockId().equals(BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString())) return Match.BLOCK_MISMATCH;
        var decoded = element.resolveState();
        if (decoded.status() == CompiledBlockStateCodec.Status.UNRESOLVED) return Match.UNRESOLVED;
        // Wire arms are derived geometry. Only ports are persistent functional state;
        // a portless network wire is equivalent to the original basic wire.
        var saved = decoded.state().orElse(null);
        if (saved != null && saved.getBlock() instanceof com.example.compiledcircuits.block.BasicWireBlock
                && actual.getBlock() instanceof com.example.compiledcircuits.block.BasicWireBlock
                && (saved.getBlock() instanceof com.example.compiledcircuits.block.NetworkWireBlock
                    || actual.getBlock() instanceof com.example.compiledcircuits.block.NetworkWireBlock)) {
            for (var side : net.minecraft.core.Direction.values()) {
                int expectedPort = saved.getBlock() instanceof com.example.compiledcircuits.block.NetworkWireBlock
                        ? Math.max(0, com.example.compiledcircuits.block.NetworkWireBlock.face(saved, side) - 1) : 0;
                int actualPort = actual.getBlock() instanceof com.example.compiledcircuits.block.NetworkWireBlock
                        ? Math.max(0, com.example.compiledcircuits.block.NetworkWireBlock.face(actual, side) - 1) : 0;
                if (expectedPort != actualPort) return Match.STATE_MISMATCH;
            }
            return Match.MATCH;
        }
        if (!element.getBlockId().equals(BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString())) return Match.BLOCK_MISMATCH;
        boolean functionalState = actual.getBlock() instanceof com.example.compiledcircuits.block.NetworkWireBlock || actual.getBlock() instanceof com.example.compiledcircuits.block.NotGateBlock
                || actual.getBlock() instanceof com.example.compiledcircuits.block.DLatchBlock
                || actual.getBlock() instanceof com.example.compiledcircuits.block.GateBodyBlock;
        if ((exact || functionalState) && decoded.status() == CompiledBlockStateCodec.Status.EXACT && !decoded.state().orElseThrow().equals(actual)) return Match.STATE_MISMATCH;
        return Match.MATCH;
    }
}
