package com.example.compiledcircuits.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Undirected discovery of a circuit, distinct from its directed signal graph. */
public final class CircuitConnections {
    private CircuitConnections() {}
    public static boolean connects(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, BlockState a, BlockState b, Direction side) {
        return connects(a, IdWireBlock.id(level, pos, a), b, IdWireBlock.id(level, pos.relative(side), b), side);
    }
    public static boolean connects(BlockState a, String left, BlockState b, String right, Direction side) {
        return IdWireBlock.compatible(a, left, b, right) && structural(a, b, side);
    }
    public static boolean connects(BlockState a, BlockState b, Direction side) {
        return connects(a, "", b, "", side);
    }
    private static boolean structural(BlockState a, BlockState b, Direction side) {
        if(a.getBlock() instanceof NetworkWireBlock && NetworkWireBlock.face(a,side)>=2
                || b.getBlock() instanceof NetworkWireBlock && NetworkWireBlock.face(b,side.getOpposite())>=2)return false;
        if (a.getBlock() instanceof GateBodyBlock body) {
            return b.getBlock() instanceof GateBodyBlock other && body.kind() == other.kind() && GateBodyBlock.port(a, side) != 3 && GateBodyBlock.port(b, side.getOpposite()) != 3
                    || b.getBlock() instanceof GatePortBlock
                    || b.getBlock() instanceof BasicWireBlock && body.canWireConnect(a, side);
        }
        if (b.getBlock() instanceof GateBodyBlock) return structural(b, a, side.getOpposite());
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
