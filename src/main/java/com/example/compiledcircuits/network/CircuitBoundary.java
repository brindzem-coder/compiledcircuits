package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Physical adjacency never joins different compilations or crosses into an uncompiled circuit. */
public final class CircuitBoundary {
    private CircuitBoundary() {}
    public static boolean allows(ServerLevel level, BlockPos a, BlockPos b) {
        var data = NetworkSavedData.get(level.getServer());
        String dimension = level.dimension().location().toString();
        if (!data.getBlockingRecords(dimension,a).isEmpty() || !data.getBlockingRecords(dimension,b).isEmpty()) return false;
        var left = data.getMembershipOwners(dimension,a);
        var right = data.getMembershipOwners(dimension,b);
        if (left.isEmpty() && right.isEmpty()) return !data.hasUnknownMembershipReservations();
        return left.size()==1 && right.size()==1 && left.get(0).network()==right.get(0).network();
    }
    public static boolean uncompiled(NetworkSavedData data, String dimension, BlockPos pos) {
        return !data.hasUnknownMembershipReservations() && data.getMembershipOwners(dimension,pos).isEmpty()
                && data.getBlockingRecords(dimension,pos).isEmpty();
    }
    /** Only former outer boundaries need a shape refresh; interior wires are unchanged. */
    public static void refreshWires(ServerLevel level, BlockPos pos, CompiledNetwork retired) {
        if (!uncompiled(NetworkSavedData.get(level.getServer()),level.dimension().location().toString(),pos)) return;
        for (var direction : net.minecraft.core.Direction.values()) {
            var other=pos.relative(direction);
            if (retired.hasElementAt(other) || !level.hasChunkAt(other)) continue;
            if (!(level.getBlockState(other).getBlock() instanceof com.example.compiledcircuits.block.IWireConnectable)) continue;
            queueWire(level,pos); queueWire(level,other);
        }
    }
    private static void queueWire(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return;
        var block = level.getBlockState(pos).getBlock();
        if (block instanceof com.example.compiledcircuits.block.BasicWireBlock
                || block instanceof com.example.compiledcircuits.block.RedstoneEndpointBlock)
            level.scheduleTick(pos,block,1);
    }
    public static void refreshWire(ServerLevel level, BlockPos pos) {
        if (!uncompiled(NetworkSavedData.get(level.getServer()),level.dimension().location().toString(),pos)) return;
        if (CompilationJobs.isWatching(level,pos)) { queueWire(level,pos); return; }
        for (var direction : net.minecraft.core.Direction.values()) if (!level.hasChunkAt(pos.relative(direction))) return;
        var state=level.getBlockState(pos);
        var updated=net.minecraft.world.level.block.Block.updateFromNeighbourShapes(state,level,pos);
        if (updated!=state) level.setBlock(pos,updated,2);
    }
}
