package com.example.compiledcircuits.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
public final class CompiledBlockStateMatcher {
    public enum Match { MATCH, BLOCK_MISMATCH, STATE_MISMATCH, UNRESOLVED }
    private CompiledBlockStateMatcher() {}
    public static Match match(CompiledCircuitElement element, BlockState actual, boolean exact) {
        var decoded = element.resolveState();
        if (decoded.status() == CompiledBlockStateCodec.Status.UNRESOLVED) return Match.UNRESOLVED;
        if (!element.getBlockId().equals(BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString())) return Match.BLOCK_MISMATCH;
        if (exact && decoded.status() == CompiledBlockStateCodec.Status.EXACT && !decoded.state().orElseThrow().equals(actual)) return Match.STATE_MISMATCH;
        return Match.MATCH;
    }
}
