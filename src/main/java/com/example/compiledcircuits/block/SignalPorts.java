package com.example.compiledcircuits.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Signal controls use the six endpoint face flags as persistent, explicit ports. */
public final class SignalPorts {
    private SignalPorts() {}
    public static boolean isControl(BlockState state) {
        return state.getBlock() instanceof SignalSwitchBlock || state.getBlock() instanceof SignalLampBlock;
    }
    public static boolean has(BlockState state, Direction side) {
        return state.getValue(RedstoneEndpointBlock.connectionProperty(side));
    }
    public static BlockState with(BlockState state, Direction side, boolean enabled) {
        return state.setValue(RedstoneEndpointBlock.connectionProperty(side), enabled);
    }
}
