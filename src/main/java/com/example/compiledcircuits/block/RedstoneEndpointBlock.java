package com.example.compiledcircuits.block;

import com.example.compiledcircuits.network.CircuitBoundary;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Redstone endpoints keep their functional role; the six wire arms are derived geometry. */
public abstract class RedstoneEndpointBlock extends EndpointBlock {
    private static final Direction[] SIDES = Direction.values();
    private static final VoxelShape[] SHAPES = buildShapes();

    protected RedstoneEndpointBlock(Properties properties) {
        super(properties);
        registerDefaultState(withoutArms(defaultBlockState()));
    }

    public static BooleanProperty connectionProperty(Direction side) {
        return switch (side) {
            case NORTH -> BasicWireBlock.NORTH;
            case SOUTH -> BasicWireBlock.SOUTH;
            case EAST -> BasicWireBlock.EAST;
            case WEST -> BasicWireBlock.WEST;
            case UP -> BasicWireBlock.UP;
            case DOWN -> BasicWireBlock.DOWN;
        };
    }

    public static BlockState withoutArms(BlockState state) {
        for (Direction side : SIDES) state = state.setValue(connectionProperty(side), false);
        return state;
    }

    private static VoxelShape[] buildShapes() {
        VoxelShape[] shapes = new VoxelShape[64];
        for (int mask = 0; mask < shapes.length; mask++) {
            VoxelShape shape = Block.box(5, 5, 5, 11, 11, 11);
            for (Direction side : SIDES) {
                if ((mask & (1 << side.ordinal())) == 0) continue;
                int x = side.getStepX(), y = side.getStepY(), z = side.getStepZ();
                shape = Shapes.or(shape, Block.box(x < 0 ? 0 : 5, y < 0 ? 0 : 5, z < 0 ? 0 : 5,
                        x > 0 ? 16 : 11, y > 0 ? 16 : 11, z > 0 ? 16 : 11));
            }
            shapes[mask] = shape;
        }
        return shapes;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        for (Direction side : SIDES) builder.add(connectionProperty(side));
    }

    private boolean connects(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        BlockState neighbor = level.getBlockState(other);
        // Plain wires become NetworkWireBlock when a face port is installed; ID wires extend it too.
        if (!neighbor.is(ModBlocks.BASIC_WIRE.get()) && !(neighbor.getBlock() instanceof NetworkWireBlock) && !(neighbor.getBlock() instanceof CrossingWireBlock)) return false;
        if (level instanceof ServerLevel server && !CircuitBoundary.allows(server, pos, other)) return false;
        return CircuitConnections.connects(level, pos, state, neighbor, side);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        for (Direction side : SIDES)
            state = state.setValue(connectionProperty(side), connects(state, context.getLevel(), context.getClickedPos(), side));
        return state;
    }

    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
                                            LevelAccessor level, BlockPos pos, BlockPos other) {
        return state.setValue(connectionProperty(side), connects(state, level, pos, side));
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction side : SIDES) if (state.getValue(connectionProperty(side))) mask |= 1 << side.ordinal();
        return SHAPES[mask];
    }

    @Override public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return true;
    }

    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        for (Direction side : SIDES) if (!level.hasChunkAt(pos.relative(side))) return;
        BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        for (Direction side : SIDES)
            rotated = rotated.setValue(connectionProperty(rotation.rotate(side)), state.getValue(connectionProperty(side)));
        return rotated;
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        for (Direction side : SIDES)
            mirrored = mirrored.setValue(connectionProperty(mirror.mirror(side)), state.getValue(connectionProperty(side)));
        return mirrored;
    }
}
