package com.example.compiledcircuits.block;

import com.example.compiledcircuits.network.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps face ports compatible with the existing network-port tools. */
public final class IdWireBlock extends NetworkWireBlock implements EntityBlock {
    public IdWireBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new IdWireEntity(pos, state); }
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target, BlockGetter level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        var stack = new net.minecraft.world.item.ItemStack(com.example.compiledcircuits.registry.ModItems.ID_WIRE.get());
        if (level.getBlockEntity(pos) instanceof IdWireEntity wire && !wire.wireId().isEmpty())
            com.example.compiledcircuits.item.IdWireItem.configure(stack, wire.wireId(), wire.color());
        return stack;
    }
    public static String id(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof IdWireBlock ? level.getBlockEntity(pos) instanceof IdWireEntity wire ? wire.wireId() : "" : "0";
    }
    public static boolean compatible(BlockState a, String left, BlockState b, String right) {
        if (a.getBlock() instanceof IdWireBlock && left.isEmpty() || b.getBlock() instanceof IdWireBlock && right.isEmpty()) return false;
        return !(a.getBlock() instanceof IdWireBlock && b.getBlock() instanceof IdWireBlock) || left.equals(right);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !server.captureBlockSnapshots && !server.restoringBlockSnapshots) {
            if (level.getBlockEntity(pos) instanceof IdWireEntity wire) wire.reconcile();
            server.scheduleTick(pos, this, 1);
        }
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server && !server.restoringBlockSnapshots && !server.captureBlockSnapshots)
            WireCatalog.get(server.getServer()).remove(server, pos);
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (level.getBlockEntity(pos) instanceof IdWireEntity wire) wire.reconcile();
        super.tick(state, level, pos, random);
        for (Direction side : Direction.values()) if (level.hasChunkAt(pos.relative(side))) CircuitBoundary.refreshWire(level, pos.relative(side));
    }
}
