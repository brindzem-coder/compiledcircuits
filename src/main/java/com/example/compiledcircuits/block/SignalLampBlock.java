package com.example.compiledcircuits.block;

import com.example.compiledcircuits.network.NetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A light-emitting compiled output, without redstone feedback into neighboring inputs. */
public final class SignalLampBlock extends OutputEndpointBlock implements net.minecraft.world.level.block.EntityBlock {
    @Override public boolean canWireConnect(BlockState state, net.minecraft.core.Direction side) { return SignalPorts.has(state, side); }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }
    @Override public BlockState updateShape(BlockState state, net.minecraft.core.Direction side, BlockState neighbor,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos other) { return state; }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SignalLampEntity(pos, state); }
    public SignalLampBlock(Properties properties) {
        super(properties); registerDefaultState(defaultBlockState().setValue(BlockStateProperties.LIT, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(BlockStateProperties.LIT);
    }
    public static void refresh(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SignalLampBlock)) return;
        var network = NetworkSavedData.get(level.getServer()).findNetworkByOutput(level, pos);
        boolean lit = network != null && network.getEffectiveSignal(pos) > 0;
        if (state.getValue(BlockStateProperties.LIT) != lit)
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!com.example.compiledcircuits.network.CompiledBlockStateMatcher.liveSignalChange(state, replacement))
            super.onRemove(state, level, pos, replacement, moving);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!com.example.compiledcircuits.network.CompiledBlockStateMatcher.liveSignalChange(old, state))
            super.onPlace(state, level, pos, old, moving);
        if (!state.is(old.getBlock()) && level instanceof ServerLevel) level.scheduleTick(pos, this, 1);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) { refresh(level, pos); }
    @Override public boolean isSignalSource(BlockState state) { return false; }
    @Override public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 0; }
    @Override public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 0; }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return net.minecraft.world.phys.shapes.Shapes.block(); }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return net.minecraft.world.phys.shapes.Shapes.block(); }
}
