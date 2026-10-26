package com.example.compiledcircuits.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Undirected discovery of a circuit, distinct from its directed signal graph. */
public final class CircuitConnections {
    private CircuitConnections() {}
    public static boolean connects(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, BlockState a, BlockState b, Direction side) {
        if (a.getBlock() instanceof CrossingWireBlock || b.getBlock() instanceof CrossingWireBlock)
            return com.example.compiledcircuits.network.Crossings.connects(level,pos,a,b,side);
        if (a.getBlock() instanceof BundledWireBlock || b.getBlock() instanceof BundledWireBlock)
            return BundleConnections.connects(level, pos, a, b, side);
        return connects(a, IdWireBlock.id(level, pos, a), b, IdWireBlock.id(level, pos.relative(side), b), side);
    }
    public static boolean connects(BlockState a, String left, BlockState b, String right, Direction side) {
        return IdWireBlock.compatible(a, left, b, right) && structural(a, b, side);
    }
    public static boolean connects(BlockState a, BlockState b, Direction side) {
        return connects(a, "", b, "", side);
    }
    private static boolean structural(BlockState a, BlockState b, Direction side) {
        if (a.getBlock() instanceof BundledWireBlock)
            return b.getBlock() instanceof BundledWireBlock || b.getBlock() instanceof BundlePortBlock && BundlePortBlock.cableSide(b) == side.getOpposite();
        if (b.getBlock() instanceof BundledWireBlock) return structural(b, a, side.getOpposite());
        if (a.getBlock() instanceof BundlePortBlock port) {
            if (b.getBlock() instanceof NetworkWireBlock && NetworkWireBlock.face(b, side.getOpposite()) >= 2) return false;
            if (b.getBlock() instanceof BundlePortBlock || port.isExternal() || side != a.getValue(EndpointBlock.FACING)) return false;
            if (!(b.getBlock() instanceof IWireConnectable wire) || !wire.canWireConnect(b, side.getOpposite())) return false;
            return !isTerminal(b) || (port.isInput() ? sends(b, side.getOpposite()) : receives(b, side.getOpposite()));
        }
        if (b.getBlock() instanceof BundlePortBlock) return structural(b, a, side.getOpposite());
        if(a.getBlock() instanceof NetworkWireBlock && NetworkWireBlock.face(a,side)>=2
                || b.getBlock() instanceof NetworkWireBlock && NetworkWireBlock.face(b,side.getOpposite())>=2)return false;
        if (SignalPorts.isControl(a) || SignalPorts.isControl(b)) {
            if (SignalPorts.isControl(a) && SignalPorts.isControl(b)) return false;
            BlockState control = SignalPorts.isControl(a) ? a : b;
            BlockState wire = SignalPorts.isControl(a) ? b : a;
            Direction face = SignalPorts.isControl(a) ? side : side.getOpposite();
            return wire.getBlock() instanceof BasicWireBlock && SignalPorts.has(control, face)
                    && ((IWireConnectable) wire.getBlock()).canWireConnect(wire, face.getOpposite());
        }
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
        return SignalPorts.isControl(state) || state.getBlock() instanceof GatePortBlock || state.getBlock() instanceof NotGateBlock
                || state.getBlock() instanceof DLatchBlock;
    }
    public static boolean sends(BlockState state, Direction side) {
        if (SignalPorts.isControl(state)) return state.getBlock() instanceof SignalSwitchBlock && SignalPorts.has(state, side);
        if (state.getBlock() instanceof GatePortBlock port) return !port.isInput();
        return (state.getBlock() instanceof NotGateBlock || state.getBlock() instanceof DLatchBlock)
                && state.getValue(EndpointBlock.FACING) == side;
    }
    public static boolean receives(BlockState state, Direction side) {
        if (SignalPorts.isControl(state)) return state.getBlock() instanceof SignalLampBlock && SignalPorts.has(state, side);
        if (state.getBlock() instanceof GatePortBlock port) return port.isInput();
        if (state.getBlock() instanceof DLatchBlock)
            return state.getValue(EndpointBlock.FACING).getOpposite() == side || DLatchBlock.enableSide(state) == side;
        return state.getBlock() instanceof NotGateBlock && state.getValue(EndpointBlock.FACING).getOpposite() == side;
    }
}
