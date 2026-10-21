package com.example.compiledcircuits.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Q faces FACING, D is opposite, WDE occupies exactly one perpendicular face. */
public class DLatchBlock extends EndpointBlock {
    public static final IntegerProperty WDE_SIDE = IntegerProperty.create("wde_side", 0, 3);
    private static final net.minecraft.world.phys.shapes.VoxelShape[] SHAPES = new net.minecraft.world.phys.shapes.VoxelShape[24];

    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        Direction output = state.getValue(FACING);
        int index = output.ordinal() * 4 + state.getValue(WDE_SIDE);
        if (SHAPES[index] != null) return SHAPES[index];
        var shape = Block.box(4, 4, 4, 12, 12, 12);
        for (var side : new Direction[]{output, output.getOpposite(), enableSide(state)}) {
            double[] low = {5, 5, 5}, high = {11, 11, 11};
            int axis = side.getAxis() == Direction.Axis.X ? 0 : side.getAxis() == Direction.Axis.Y ? 1 : 2;
            boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            low[axis] = positive ? 12 : 0;
            high[axis] = positive ? 16 : 4;
            shape = net.minecraft.world.phys.shapes.Shapes.or(shape,
                    Block.box(low[0], low[1], low[2], high[0], high[1], high[2]));
        }
        return SHAPES[index] = shape;
    }

    public DLatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WDE_SIDE, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WDE_SIDE);
    }
    public static Direction enableSide(BlockState state) {
        Direction output = state.getValue(FACING);
        int side = state.getValue(WDE_SIDE);
        if (output.getAxis().isVertical()) {
            return switch (side) {
                case 0 -> Direction.EAST; case 1 -> Direction.NORTH;
                case 2 -> Direction.WEST; default -> Direction.SOUTH;
            };
        }
        return switch (side) {
            case 0 -> output.getCounterClockWise(); case 1 -> Direction.UP;
            case 2 -> output.getClockWise(); default -> Direction.DOWN;
        };
    }
    /** Maps a perpendicular world face to the saved enable-port orientation. */
    public static BlockState withEnableSide(BlockState state, Direction side) {
        if (side.getAxis() == state.getValue(FACING).getAxis())
            throw new IllegalArgumentException("WDE must be perpendicular to D/Q");
        for (int i = 0; i < 4; i++) {
            BlockState candidate = state.setValue(WDE_SIDE, i);
            if (enableSide(candidate) == side) return candidate;
        }
        throw new IllegalArgumentException("Invalid WDE face");
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }
    @Override public boolean canWireConnect(BlockState state, Direction side) {
        return side.getAxis() == state.getValue(FACING).getAxis() || side == enableSide(state);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return withEnableSide(state.setValue(FACING, rotation.rotate(state.getValue(FACING))),
                rotation.rotate(enableSide(state)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return withEnableSide(state.setValue(FACING, mirror.mirror(state.getValue(FACING))),
                mirror.mirror(enableSide(state)));
    }
}
