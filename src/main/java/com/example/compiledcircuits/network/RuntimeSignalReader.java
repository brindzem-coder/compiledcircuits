package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import java.util.HashSet;
import java.util.Set;

/** A signal view which cannot request or generate a chunk, including indirect reads. */
public final class RuntimeSignalReader implements SignalGetter {
    private final ServerLevel level;
    private boolean available = true;
    public RuntimeSignalReader(ServerLevel level) { this.level = level; }
    public ServerLevel level() { return level; }
    public boolean available() { return available; }
    public int readInput(BlockPos input) {
        if (!canRead(input)) return 0;
        int signal = 0;
        // Visit every side even after HIGH. Only solid/weak-power-checking neighbors
        // require the second ring; air must not invent an unavailable dependency.
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = input.relative(direction);
            BlockState state = getBlockState(neighbor);
            if (state.shouldCheckWeakPower(this, neighbor, direction))
                for (Direction indirect : Direction.values()) canRead(neighbor.relative(indirect));
            signal = Math.max(signal, getSignal(neighbor, direction));
        }
        return signal;
    }
    public static Set<Long> dependencyChunks(BlockPos input) {
        Set<Long> result = new HashSet<>();
        result.add(chunk(input));
        // 1.20.1 SignalGetter: neighbor signal, then strong power around that neighbor.
        for (Direction a : Direction.values()) {
            BlockPos neighbor = input.relative(a);
            result.add(chunk(neighbor));
            for (Direction b : Direction.values()) result.add(chunk(neighbor.relative(b)));
        }
        return result;
    }
    public static long chunk(BlockPos pos) { return net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4); }
    private boolean canRead(BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return true;
        boolean loaded = NetworkRuntime.isChunkAvailable(level, chunk(pos));
        available &= loaded;
        return loaded;
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        com.example.compiledcircuits.diagnostics.PerformanceDiagnostics.add("runtime.blockReads", 1);
        if (!canRead(pos) || level.isOutsideBuildHeight(pos)) return Blocks.AIR.defaultBlockState();
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos);
    }
    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        if (!canRead(pos) || level.isOutsideBuildHeight(pos)) return null;
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4).getBlockEntity(pos);
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public int getHeight() { return level.getHeight(); }
    @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
}
