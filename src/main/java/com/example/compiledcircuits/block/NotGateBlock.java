package com.example.compiledcircuits.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** FACING is the output; the opposite face is the input. */
public class NotGateBlock extends EndpointBlock {
    private static final net.minecraft.world.phys.shapes.VoxelShape BODY = net.minecraft.world.level.block.Block.box(4, 4, 4, 12, 12, 12);
    private static final net.minecraft.world.phys.shapes.VoxelShape X_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(BODY,
            net.minecraft.world.level.block.Block.box(0, 5, 5, 16, 11, 11));
    private static final net.minecraft.world.phys.shapes.VoxelShape Y_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(BODY,
            net.minecraft.world.level.block.Block.box(5, 0, 5, 11, 16, 11));
    private static final net.minecraft.world.phys.shapes.VoxelShape Z_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(BODY,
            net.minecraft.world.level.block.Block.box(5, 5, 0, 11, 11, 16));
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> X_SHAPE; case Y -> Y_SHAPE; case Z -> Z_SHAPE;
        };
    }
    public NotGateBlock(Properties properties) { super(properties); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }
    @Override public boolean canWireConnect(BlockState state, Direction side) {
        return side.getAxis() == state.getValue(FACING).getAxis();
    }
    @Override public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
