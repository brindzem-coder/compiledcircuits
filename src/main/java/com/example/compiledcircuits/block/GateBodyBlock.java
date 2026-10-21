package com.example.compiledcircuits.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.example.compiledcircuits.network.NetworkIntegrityManager;

/** Same-kind bodies merge across unsealed faces. Face values: 0 bare, 1 input, 2 output, 3 sealed. */
public class GateBodyBlock extends Block implements IWireConnectable {
    public enum Kind { AND, OR, NAND, NOR, XOR, XNOR }
    private final Kind kind;
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty[] PORTS =
            java.util.Arrays.stream(Direction.values()).map(d -> net.minecraft.world.level.block.state.properties.IntegerProperty.create("port_" + d.getName(), 0, 3))
                    .toArray(net.minecraft.world.level.block.state.properties.IntegerProperty[]::new);
    public static int port(BlockState state, Direction side) { return state.getValue(PORTS[side.ordinal()]); }
    public static BlockState withPort(BlockState state, Direction side, int port) { return state.setValue(PORTS[side.ordinal()], port); }
    public GateBodyBlock(Properties properties, Kind kind) {
        super(properties); this.kind = kind;
        BlockState state = defaultBlockState();
        for (var property : PORTS) state = state.setValue(property, 0);
        registerDefaultState(state);
    }
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) { builder.add(PORTS); }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos next) {
        if (neighbor.getBlock() instanceof GateBodyBlock && (port(state, side) == 1 || port(state, side) == 2)
                && (!(level instanceof ServerLevel serverLevel)
                || com.example.compiledcircuits.network.CircuitBoundary.allows(serverLevel,pos,next))) {
            if (level instanceof ServerLevel server) NetworkIntegrityManager.scheduleCheck(server, pos);
            return withPort(state, side, 0);
        }
        return super.updateShape(state, side, neighbor, level, pos, next);
    }
    @Override public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        BlockState result = state;
        for (Direction side : Direction.values()) result = withPort(result, rotation.rotate(side), port(state, side));
        return result;
    }
    @Override public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        BlockState result = state;
        for (Direction side : Direction.values()) result = withPort(result, mirror.mirror(side), port(state, side));
        return result;
    }
    public Kind kind() { return kind; }
    @Override public boolean canWireConnect(BlockState state, Direction side) { return port(state, side) == 1 || port(state, side) == 2; }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server) NetworkIntegrityManager.scheduleCheck(server, pos);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (level instanceof ServerLevel server) NetworkIntegrityManager.scheduleCheck(server, pos);
        super.onRemove(state, level, pos, replacement, moving);
    }
}
