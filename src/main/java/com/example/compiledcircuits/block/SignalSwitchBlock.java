package com.example.compiledcircuits.block;

import com.example.compiledcircuits.network.NetworkRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A user-controlled compiled input; adjacent redstone never changes its value. */
public final class SignalSwitchBlock extends InputEndpointBlock {
    public SignalSwitchBlock(Properties properties) {
        super(properties); registerDefaultState(defaultBlockState().setValue(BlockStateProperties.POWERED, false));
    }
    @Override public boolean canWireConnect(BlockState state, net.minecraft.core.Direction side) { return SignalPorts.has(state, side); }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }
    @Override public BlockState updateShape(BlockState state, net.minecraft.core.Direction side, BlockState neighbor,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos other) { return state; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(BlockStateProperties.POWERED);
    }
    @Override public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof com.example.compiledcircuits.item.SignalFacePortItem) return net.minecraft.world.InteractionResult.PASS;
        if (player.isSpectator() || !player.mayBuild()) return net.minecraft.world.InteractionResult.FAIL;
        if (level instanceof ServerLevel server) {
            if (!server.mayInteract(player, pos)) return net.minecraft.world.InteractionResult.FAIL;
            level.setBlock(pos, state.cycle(BlockStateProperties.POWERED), Block.UPDATE_ALL);
            NetworkRuntime.inputChanged(server, pos);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!com.example.compiledcircuits.network.CompiledBlockStateMatcher.liveSignalChange(state, replacement))
            super.onRemove(state, level, pos, replacement, moving);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!com.example.compiledcircuits.network.CompiledBlockStateMatcher.liveSignalChange(old, state))
            super.onPlace(state, level, pos, old, moving);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean moved) { }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return net.minecraft.world.phys.shapes.Shapes.block(); }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return net.minecraft.world.phys.shapes.Shapes.block(); }
}
