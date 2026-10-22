package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.BlockSnapshot;

public final class BundlePortPlacement {
    public static InteractionResult apply(UseOnContext context, int kind, String id) {
        var level = context.getLevel(); var pos = context.getClickedPos(); var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BundledWireBlock)) return InteractionResult.PASS;
        var player = context.getPlayer(); var side = context.getClickedFace();
        if (player == null || !player.mayBuild() || !player.mayUseItemAt(pos, side, context.getItemInHand())) return InteractionResult.FAIL;
        if (level instanceof ServerLevel server && player instanceof ServerPlayer actor) {
            if (!(level.getBlockEntity(pos) instanceof BundleWireEntity wire) || !server.mayInteract(actor, pos)) return InteractionResult.FAIL;
            var desired = new BundleWireEntity.Port(kind, id);
            var next = desired.equals(wire.port(side)) ? null : desired;
            if (!NetworkOperations.canModify(actor) || server.restoringBlockSnapshots) return InteractionResult.FAIL;
            if (server.captureBlockSnapshots) {
                // ItemStack.useOn already owns the Forge placement transaction. Capture
                // the BE before changing it, even when the cable's BlockState stays equal.
                // Forge then handles protection events, rollback and notifications.
                server.capturedBlockSnapshots.add(BlockSnapshot.create(server.dimension(), server, pos, Block.UPDATE_ALL));
                wire.setPort(side, next);
            } else if (!ProtectedRepairPlacement.place(server, pos, state, actor,
                    () -> NetworkOperations.canModify(actor), () -> wire.setPort(side, next))) {
                return InteractionResult.FAIL;
            }
            wire.refreshConnections();
            NetworkIntegrityManager.scheduleCheck(server, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
