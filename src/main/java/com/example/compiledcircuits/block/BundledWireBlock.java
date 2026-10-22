package com.example.compiledcircuits.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;

/** Geometry only: channels are resolved by the compiler, never ticked in the world. */
public final class BundledWireBlock extends BasicWireBlock implements net.minecraft.world.level.block.EntityBlock {
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) { return new BundleWireEntity(pos, state); }
    private static final net.minecraft.world.phys.shapes.VoxelShape[] SHAPES = shapes();
    private static net.minecraft.world.phys.shapes.VoxelShape[] shapes() {
        var result = new net.minecraft.world.phys.shapes.VoxelShape[64];
        var arms = new net.minecraft.world.phys.shapes.VoxelShape[]{
            box(5,5,0,11,11,5), box(5,5,11,11,11,16), box(11,5,5,16,11,11),
            box(0,5,5,5,11,11), box(5,11,5,11,16,11), box(5,0,5,11,5,11)};
        for (int mask = 0; mask < 64; mask++) {
            var shape = box(4.8,5,4.8,11.2,11,11.2);
            for (int i = 0; i < 6; i++) if ((mask & (1 << i)) != 0) shape = net.minecraft.world.phys.shapes.Shapes.or(shape, arms[i]);
            result[mask] = shape;
        }
        return result;
    }
    public BundledWireBlock(Properties properties) { super(properties); }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        int mask = (state.getValue(NORTH) ? 1 : 0) | (state.getValue(SOUTH) ? 2 : 0)
                | (state.getValue(EAST) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0)
                | (state.getValue(UP) ? 16 : 0) | (state.getValue(DOWN) ? 32 : 0);
        var shape = SHAPES[mask];
        if (level.getBlockEntity(pos) instanceof BundleWireEntity wire) for (var side : wire.ports().keySet()) {
            double[] low = {2.5,2.5,2.5}, high = {13.5,13.5,13.5};
            int axis = side.getAxis() == Direction.Axis.X ? 0 : side.getAxis() == Direction.Axis.Y ? 1 : 2;
            low[axis] = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 11 : 0;
            high[axis] = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 16 : 5;
            shape = net.minecraft.world.phys.shapes.Shapes.or(shape, box(low[0],low[1],low[2],high[0],high[1],high[2]));
        }
        return shape;
    }
    @Override protected boolean canConnectTo(LevelAccessor level, BlockPos pos, Direction side) {
        if (level instanceof net.minecraft.server.level.ServerLevel server
                && !com.example.compiledcircuits.network.CircuitBoundary.allows(server, pos, pos.relative(side))) return false;
        if (BundleConnections.port(level, pos, side) != null) return true;
        var neighbor = level.getBlockState(pos.relative(side));
        return BundleConnections.connects(level, pos, defaultBlockState(), neighbor, side);
    }
}
