package com.example.compiledcircuits.block;

import net.minecraft.world.level.block.state.BlockState;
public final class CircuitElementPredicates {
    private CircuitElementPredicates() {}
    public static boolean isCircuit(BlockState state) { return state.getBlock() instanceof IWireConnectable; }
}
