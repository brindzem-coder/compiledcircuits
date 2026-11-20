package com.example.compiledcircuits.block;

import com.example.compiledcircuits.network.KeyboardSessions;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** An input endpoint used to invalidate its compiled network when a keyboard session changes. */
public final class KeyboardBlock extends InputEndpointBlock implements EntityBlock {
    public KeyboardBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new KeyboardEntity(pos,state); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(0,0,0,16,8,16); }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return getShape(state,level,pos,context); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer server) {
            if (player.isSpectator() || !level.mayInteract(player,pos)) return InteractionResult.FAIL;
            if (player.isShiftKeyDown()) {
                if (player.mayBuild() && com.example.compiledcircuits.network.NetworkOperations.canModify(server)
                        && level.getBlockEntity(pos) instanceof KeyboardEntity keyboard) com.example.compiledcircuits.network.KeyboardCameraSessions.open(server,pos);
            } else KeyboardSessions.open(server,pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos other, boolean moved) { }
}
