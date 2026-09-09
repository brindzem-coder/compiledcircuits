package com.example.compiledcircuits.client;

import com.example.compiledcircuits.block.CircuitElementPredicates;
import com.example.compiledcircuits.block.IWireConnectable;
import com.example.compiledcircuits.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.*;

/** Main-thread, read-only hover cache. Rendering only consumes the published set. */
public final class ClientHoveredCircuit {
    public static final int MAX_NODES = 50_000, NODES_PER_TICK = 1024;
    public enum Status { EMPTY, PENDING, READY, TOO_LARGE }
    @FunctionalInterface
    interface Edges { boolean connects(BlockPos from, Direction direction); }
    private record Key(Object level, String dimension, BlockPos target, long membership, long topology) {}
    private static Key key;
    private static Set<BlockPos> positions = Set.of();
    private static Status status = Status.EMPTY;
    private static Job job;
    private static long bfsStarted;
    private static int bfsNodesThisTick;
    private static final class Job {
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> visited = new HashSet<>();
        final Edges edges;
        Job(BlockPos seed, Edges edges) { this.edges = edges; queue.add(seed); visited.add(seed); }
    }
    private ClientHoveredCircuit() {}
    public static boolean contains(BlockPos pos) { return positions.contains(pos); }
    public static Set<BlockPos> getPositions() { return positions; }
    public static Status getStatus() { return status; }
    public static long getBfsStarted() { return bfsStarted; }
    public static int getBfsNodesThisTick() { return bfsNodesThisTick; }
    public static void clear() { key = null; positions = Set.of(); job = null; status = Status.EMPTY; bfsNodesThisTick = 0; }
    public static void reset() { clear(); bfsStarted = 0; }

    public static void tick() {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null || minecraft.screen != null
                || !(player.getMainHandItem().is(ModItems.NETWORK_SELECTOR.get())
                || player.getOffhandItem().is(ModItems.NETWORK_SELECTOR.get()))
                || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            clear(); return;
        }
        String dimension = level.dimension().location().toString();
        BlockPos target = hit.getBlockPos().immutable();
        var state = loadedState(level, target);
        if (state == null || !CircuitElementPredicates.isCircuit(state) || !ClientCompiledElements.isReadyFor(dimension)) {
            clear(); return;
        }
        Integer network = ClientCompiledElements.networkIdAt(target);
        update(level, dimension, target, ClientCompiledElements.getRevision(), ClientCircuitBlockIndex.getTopologyRevision(),
                network == null ? null : ClientCompiledElements.positionsForNetwork(network),
                (from, direction) -> connects(level, from, direction));
    }

    // The graph boundary keeps bounded collection testable without a Minecraft client world.
    static void update(Object level, String dimension, BlockPos target, long membership, long topology,
                       Set<BlockPos> compiledPositions, Edges edges) {
        bfsNodesThisTick = 0;
        Key next = new Key(level, dimension, target.immutable(), membership, topology);
        if (!next.equals(key)) {
            key = next; positions = Set.of(); job = null;
            if (compiledPositions != null) {
                positions = compiledPositions; // immutable cached membership view, no copy/BFS
                status = Status.READY;
            } else {
                job = new Job(next.target, edges); status = Status.PENDING; bfsStarted++;
            }
        }
        if (job == null) return;
        while (bfsNodesThisTick < NODES_PER_TICK && !job.queue.isEmpty()) {
            BlockPos from = job.queue.removeFirst(); bfsNodesThisTick++;
            for (Direction direction : Direction.values()) {
                BlockPos to = from.relative(direction);
                if (job.visited.contains(to) || !job.edges.connects(from, direction)) continue;
                if (job.visited.size() == MAX_NODES) {
                    job = null; status = Status.TOO_LARGE; return;
                }
                job.visited.add(to); job.queue.addLast(to);
            }
        }
        if (job.queue.isEmpty()) {
            positions = Collections.unmodifiableSet(job.visited);
            job = null; status = Status.READY;
        }
    }
    private static BlockState loadedState(ClientLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return null;
        var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        return chunk == null ? null : chunk.getBlockState(pos);
    }
    private static boolean connects(ClientLevel level, BlockPos from, Direction direction) {
        BlockPos to = from.relative(direction);
        if (ClientCompiledElements.networkIdAt(from) != null || ClientCompiledElements.networkIdAt(to) != null) return false;
        var current = loadedState(level, from);
        var neighbor = loadedState(level, to);
        return current != null && neighbor != null
                && current.getBlock() instanceof IWireConnectable a && neighbor.getBlock() instanceof IWireConnectable b
                && a.canWireConnect(current, direction) && b.canWireConnect(neighbor, direction.getOpposite());
    }
}
