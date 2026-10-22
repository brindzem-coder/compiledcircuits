package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Forge 47.4.10 placement contract: capture, tentatively place, post, restore or notify.
 * Mirrors ForgeHooks.onPlaceItemIntoWorld without inventing inventory costs or an item use.
 */
final class ProtectedRepairPlacement {
    private ProtectedRepairPlacement() {}
    static boolean place(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, BooleanSupplier stillAllowed) {
        return place(level, pos, state, player, stillAllowed, () -> {});
    }
    static boolean place(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, BooleanSupplier stillAllowed, Runnable initialize) {
        if (!stillAllowed.getAsBoolean() || level.captureBlockSnapshots || level.restoringBlockSnapshots
                || !level.capturedBlockSnapshots.isEmpty() || !level.hasChunkAt(pos)) return false;
        List<BlockSnapshot> snapshots = new ArrayList<>();
        boolean committed = false;
        try {
            level.captureBlockSnapshots = true;
            boolean placed;
            try {
                // A changed wire ID may leave the BlockState identical. Capture its BE NBT too,
                // so claim-protection cancellation restores identity as well as geometry.
                if (state == level.getBlockState(pos) && state.getBlock() instanceof com.example.compiledcircuits.block.IdWireBlock) {
                    snapshots.add(BlockSnapshot.create(level.dimension(), level, pos, Block.UPDATE_ALL));
                    placed = true;
                } else placed = level.setBlock(pos, state, Block.UPDATE_ALL);
                if (placed) initialize.run();
            }
            finally {
                level.captureBlockSnapshots = false;
                snapshots.addAll(level.capturedBlockSnapshots); level.capturedBlockSnapshots.clear();
            }
            if (!placed || snapshots.isEmpty()) return false;
            boolean canceled = snapshots.size() == 1 ? ForgeEventFactory.onBlockPlace(player,snapshots.get(0),Direction.UP)
                    : ForgeEventFactory.onMultiBlockPlace(player,snapshots,Direction.UP);
            if (canceled || !stillAllowed.getAsBoolean()) return false;
            committed = true;
            for (var snapshot : snapshots) {
                var current = level.getBlockState(snapshot.getPos());
                current.onPlace(level,snapshot.getPos(),snapshot.getReplacedBlock(),false);
                level.markAndNotifyBlock(snapshot.getPos(),level.getChunkAt(snapshot.getPos()),snapshot.getReplacedBlock(),current,snapshot.getFlag(),512);
            }
            return true;
        } finally {
            if (!committed) {
                level.restoringBlockSnapshots = true;
                try { for (int i=snapshots.size()-1;i>=0;i--) snapshots.get(i).restore(true,false); }
                finally { level.restoringBlockSnapshots = false; }
            }
        }
    }
}
