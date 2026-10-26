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
    private static ClientLaneTraversal laneJob;
    private static int laneMask;
    private static java.util.Map<BlockPos,Integer> laneMasks=java.util.Map.of();
    public static int laneMask(BlockPos pos){return laneMasks.getOrDefault(pos,7);}
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
    public static void clear() { laneJob=null;laneMasks=java.util.Map.of();key = null; positions = Set.of(); job = null; status = Status.EMPTY; bfsNodesThisTick = 0; }
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
        if (ClientCompiledElements.isReadyFor(dimension) && ClientCompiledElements.isBlocked(target)
                && state != null && CircuitElementPredicates.isCircuit(state)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Blocked saved membership. An administrator can inspect /circuit conflicts list."), true);
            clear(); return;
        }
        if (state == null || !CircuitElementPredicates.isCircuit(state) || !ClientCompiledElements.isReadyFor(dimension)) {
            clear(); return;
        }
        int mask=state.getBlock() instanceof com.example.compiledcircuits.block.CrossingWireBlock?1<<com.example.compiledcircuits.block.CrossingWireBlock.hit(state,target,hit.getLocation()).ordinal():7;
        var next=new Key(level,dimension,target,ClientCompiledElements.getRevision(),ClientCircuitBlockIndex.getTopologyRevision());
        if(!next.equals(key)||laneMask!=mask){key=next;laneMask=mask;laneJob=new ClientLaneTraversal(level,target,mask);positions=Set.of();laneMasks=java.util.Map.of();status=Status.PENDING;bfsStarted++;}
        bfsNodesThisTick=0;
        if(laneJob!=null){laneJob.step(NODES_PER_TICK);bfsNodesThisTick=NODES_PER_TICK;
            if(laneJob.done()){status=laneJob.failed?Status.TOO_LARGE:Status.READY;if(!laneJob.failed){laneMasks=java.util.Map.copyOf(laneJob.masks);positions=laneMasks.keySet();}laneJob=null;}}

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
        if (ClientCompiledElements.isBlocked(from) || ClientCompiledElements.isBlocked(to)) return false;
        if (ClientCompiledElements.networkIdAt(from) != null || ClientCompiledElements.networkIdAt(to) != null) return false;
        var current = loadedState(level, from);
        var neighbor = loadedState(level, to);
        return current != null && neighbor != null
                && com.example.compiledcircuits.block.CircuitConnections.connects(level, from, current, neighbor, direction);
    }
}
