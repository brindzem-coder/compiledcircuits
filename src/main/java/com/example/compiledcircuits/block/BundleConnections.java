package com.example.compiledcircuits.block;

import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class BundleConnections {
    private BundleConnections() {}
    public static BundleWireEntity.Port port(BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof BundleWireEntity wire ? wire.port(side) : null;
    }
    public static boolean connects(BlockGetter level, BlockPos pos, BlockState a, BlockState b, Direction side) {
        if (!(a.getBlock() instanceof BundledWireBlock)) return connects(level, pos.relative(side), b, a, side.getOpposite());
        var port = port(level, pos, side);
        if (b.getBlock() instanceof BundledWireBlock)
            return port == null && port(level, pos.relative(side), side.getOpposite()) == null;
        if (port == null) return b.getBlock() instanceof BundlePortBlock && BundlePortBlock.cableSide(b) == side.getOpposite();
        if (port.external() || b.getBlock() instanceof BundlePortBlock || b.getBlock() instanceof GateBodyBlock) return false;
        if (!(b.getBlock() instanceof IWireConnectable wire) || !wire.canWireConnect(b, side.getOpposite())) return false;
        return !CircuitConnections.isTerminal(b) || (port.input() ? CircuitConnections.sends(b, side.getOpposite()) : CircuitConnections.receives(b, side.getOpposite()));
    }
}
