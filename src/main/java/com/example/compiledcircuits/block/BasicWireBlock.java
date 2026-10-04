package com.example.compiledcircuits.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BasicWireBlock extends Block implements IWireConnectable {

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    // Центральна частина: 5x5x5
    private static final VoxelShape CENTER =
            Block.box(
                    5.5D, 5.5D, 5.5D,
                    10.5D, 10.5D, 10.5D
            );

    private static final VoxelShape NORTH_SHAPE =
            Block.box(
                    5.5D, 5.5D, 0.0D,
                    10.5D, 10.5D, 5.5D
            );

    private static final VoxelShape SOUTH_SHAPE =
            Block.box(
                    5.5D, 5.5D, 10.5D,
                    10.5D, 10.5D, 16.0D
            );

    private static final VoxelShape WEST_SHAPE =
            Block.box(
                    0.0D, 5.5D, 5.5D,
                    5.5D, 10.5D, 10.5D
            );

    private static final VoxelShape EAST_SHAPE =
            Block.box(
                    10.5D, 5.5D, 5.5D,
                    16.0D, 10.5D, 10.5D
            );

    private static final VoxelShape UP_SHAPE =
            Block.box(
                    5.5D, 10.5D, 5.5D,
                    10.5D, 16.0D, 10.5D
            );

    private static final VoxelShape DOWN_SHAPE =
            Block.box(
                    5.5D, 0.0D, 5.5D,
                    10.5D, 5.5D, 10.5D
            );

    // N=1, S=2, E=4, W=8, U=16, D=32. Geometry depends only on these six flags.
    // Initialized after the shared primitives; never exposed or mutated after publication.
    private static final VoxelShape[] SHAPES = buildShapes();

    private static VoxelShape[] buildShapes() {
        VoxelShape[] shapes = new VoxelShape[64];
        for (int mask = 0; mask < shapes.length; mask++) {
            VoxelShape shape = CENTER;

            if ((mask & 1) != 0) {
                shape = Shapes.joinUnoptimized(shape, NORTH_SHAPE, BooleanOp.OR);
            }

            if ((mask & 2) != 0) {
                shape = Shapes.joinUnoptimized(shape, SOUTH_SHAPE, BooleanOp.OR);
            }

            if ((mask & 4) != 0) {
                shape = Shapes.joinUnoptimized(shape, EAST_SHAPE, BooleanOp.OR);
            }

            if ((mask & 8) != 0) {
                shape = Shapes.joinUnoptimized(shape, WEST_SHAPE, BooleanOp.OR);
            }

            if ((mask & 16) != 0) {
                shape = Shapes.joinUnoptimized(shape, UP_SHAPE, BooleanOp.OR);
            }

            if ((mask & 32) != 0) {
                shape = Shapes.joinUnoptimized(shape, DOWN_SHAPE, BooleanOp.OR);
            }

            shapes[mask] = shape;
        }
        return shapes;
    }

    public BasicWireBlock(Properties properties) {
        super(properties);

        registerDefaultState(
                stateDefinition.any()
                        .setValue(NORTH, false)
                        .setValue(SOUTH, false)
                        .setValue(EAST, false)
                        .setValue(WEST, false)
                        .setValue(UP, false)
                        .setValue(DOWN, false)
        );
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel)
            com.example.compiledcircuits.network.NetworkIntegrityManager.scheduleCheck(serverLevel, pos);
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    public void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel)
            com.example.compiledcircuits.network.NetworkIntegrityManager.scheduleCheck(serverLevel, pos);
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(
                NORTH,
                SOUTH,
                EAST,
                WEST,
                UP,
                DOWN
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {

        BlockPos pos = context.getClickedPos();
        LevelAccessor level = context.getLevel();

        return defaultBlockState()
                .setValue(NORTH, canConnectTo(level, pos, Direction.NORTH))
                .setValue(SOUTH, canConnectTo(level, pos, Direction.SOUTH))
                .setValue(EAST, canConnectTo(level, pos, Direction.EAST))
                .setValue(WEST, canConnectTo(level, pos, Direction.WEST))
                .setValue(UP, canConnectTo(level, pos, Direction.UP))
                .setValue(DOWN, canConnectTo(level, pos, Direction.DOWN));
    }

    @Override
    public BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos currentPos,
            BlockPos neighborPos
    ) {

        boolean connected = canConnectTo(level, currentPos, direction);

        return switch (direction) {
            case NORTH -> state.setValue(NORTH, connected);
            case SOUTH -> state.setValue(SOUTH, connected);
            case EAST -> state.setValue(EAST, connected);
            case WEST -> state.setValue(WEST, connected);
            case UP -> state.setValue(UP, connected);
            case DOWN -> state.setValue(DOWN, connected);
        };
    }

    private boolean canConnectTo(
            LevelAccessor level,
            BlockPos currentPos,
            Direction direction
    ) {
        BlockPos neighborPos = currentPos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);

        if (neighborState.getBlock() instanceof IWireConnectable connectable) {
            return connectable.canWireConnect(
                    neighborState,
                    direction.getOpposite()
            );
        }

        return false;
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {

        int mask = (state.getValue(NORTH) ? 1 : 0)
                | (state.getValue(SOUTH) ? 2 : 0)
                | (state.getValue(EAST) ? 4 : 0)
                | (state.getValue(WEST) ? 8 : 0)
                | (state.getValue(UP) ? 16 : 0)
                | (state.getValue(DOWN) ? 32 : 0);
        return SHAPES[mask];
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return Shapes.empty();
    }

    @Override
    public boolean canWireConnect(BlockState state, Direction side) {
        return true;
    }
}
