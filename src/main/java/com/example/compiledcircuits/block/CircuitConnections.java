package com.example.compiledcircuits.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Undirected discovery of a circuit, distinct from its directed signal graph. */
public final class CircuitConnections {
    private CircuitConnections() {}
    public static boolean connects(BlockState a, BlockState b, Direction side) {
        if (a.getBlock() instanceof GateBodyBlock body) {
            return b.getBlock() instanceof GateBodyBlock other && body.kind() == other.kind()
                    || b.getBlock() instanceof GatePortBlock;
        }
        if (b.getBlock() instanceof GateBodyBlock) return connects(b, a, side.getOpposite());
        if (!(a.getBlock() instanceof IWireConnectable left) || !(b.getBlock() instanceof IWireConnectable right)
                || !left.canWireConnect(a, side) || !right.canWireConnect(b, side.getOpposite())) return false;
        if (isTerminal(a) && isTerminal(b))
            return sends(a, side) && receives(b, side.getOpposite())
                    || receives(a, side) && sends(b, side.getOpposite());
        return true;
    }
    public static boolean isTerminal(BlockState state) {
        return state.getBlock() instanceof GatePortBlock || state.getBlock() instanceof NotGateBlock
                || state.getBlock() instanceof DLatchBlock;
    }
    public static boolean sends(BlockState state, Direction side) {
        if (state.getBlock() instanceof GatePortBlock port) return !port.isInput();
        return (state.getBlock() instanceof NotGateBlock || state.getBlock() instanceof DLatchBlock)
                && state.getValue(EndpointBlock.FACING) == side;
    }
    public static boolean receives(BlockState state, Direction side) {
        if (state.getBlock() instanceof GatePortBlock port) return port.isInput();
        if (state.getBlock() instanceof DLatchBlock)
            return state.getValue(EndpointBlock.FACING).getOpposite() == side || DLatchBlock.enableSide(state) == side;
        return state.getBlock() instanceof NotGateBlock && state.getValue(EndpointBlock.FACING).getOpposite() == side;
    }
}
