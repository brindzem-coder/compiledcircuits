package com.example.compiledcircuits.client;

import com.example.compiledcircuits.block.CircuitElementPredicates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Client-thread physical index. Jobs hold keys, never chunk references. */
public final class ClientCircuitBlockIndex {
    public static final int MAX_PALETTE_CHECKS = 8, MAX_BLOCK_READS = 8192;
    private static ClientLevel activeLevel;
    private static long generation, topologyRevision;
    private static boolean topologyChanged;
    private record SectionJob(long chunk, int sectionY, long generation) {}
    private static final Map<Long, Map<Integer, Set<BlockPos>>> chunks = new HashMap<>();
    private static final Set<SectionJob> pending = new LinkedHashSet<>();
    private static Map<Long, Map<Integer, Set<BlockPos>>> cachedBuckets;
    private static int blockReadsThisTick, paletteChecksThisTick;
    private ClientCircuitBlockIndex() {}
    public static long getGeneration() { return generation; }
    public static long getTopologyRevision() { return topologyRevision; }
    public static long getMembershipRevision() { return ClientCompiledElements.getRevision(); }
    public static int getBlockReadsThisTick() { return blockReadsThisTick; }
    public static int getPaletteChecksThisTick() { return paletteChecksThisTick; }
    public static int getQueuedSections() { return pending.size(); }
    public static boolean contains(BlockPos pos) {
        var sections = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        return sections != null && sections.getOrDefault(pos.getY() >> 4, Set.of()).contains(pos);
    }
    /** Called after renderer traversal so live bucket iterators are never invalidated. */
    public static void removeStale(ClientLevel level, Collection<BlockPos> stale) {
        if (level == null || level != activeLevel || stale.isEmpty()) return;
        boolean changed = false;
        for (BlockPos pos : stale) {
            var sections = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            if (sections == null) continue;
            var positions = sections.get(pos.getY() >> 4);
            if (positions != null) changed |= positions.remove(pos);
        }
        if (changed) { cachedBuckets = null; topologyChanged = true; }
    }
    /** Immutable snapshots for future renderer bucket traversal; no world reads. */
    public static Map<Long, Map<Integer, Set<BlockPos>>> getBuckets() {
        if (cachedBuckets != null) return cachedBuckets;
        Map<Long, Map<Integer, Set<BlockPos>>> result = new HashMap<>();
        chunks.forEach((key, sections) -> {
            Map<Integer, Set<BlockPos>> view = new HashMap<>();
            sections.forEach((y, positions) -> view.put(y, Collections.unmodifiableSet(positions)));
            result.put(key, Collections.unmodifiableMap(view));
        });
        cachedBuckets = Collections.unmodifiableMap(result);
        return cachedBuckets;
    }
    public static void onLevelChanged(ClientLevel level) {
        if (activeLevel == level) return;
        clear(); activeLevel = level;
    }
    public static void clear() {
        activeLevel = null; generation++; topologyRevision++;
        cachedBuckets = null;
        chunks.clear(); pending.clear(); topologyChanged = false;
        blockReadsThisTick = 0; paletteChecksThisTick = 0;
    }
    public static void onChunkLoaded(ClientLevel level, LevelChunk chunk) {
        if (level == null || level != activeLevel) return;
        long key = chunk.getPos().toLong();
        chunks.remove(key); pending.removeIf(job -> job.chunk == key);
        for (int i = 0; i < chunk.getSections().length; i++) {
            pending.add(new SectionJob(key, level.getMinSection() + i, generation));
        }
        cachedBuckets = null;
        topologyChanged = true;
    }
    public static void onChunkUnloaded(ClientLevel level, ChunkPos pos) {
        if (level == null || level != activeLevel) return;
        chunks.remove(pos.toLong()); pending.removeIf(job -> job.chunk == pos.toLong());
        cachedBuckets = null;
        topologyChanged = true;
    }
    public static void onBlockChanged(ClientLevel level, BlockPos pos, BlockState previous, BlockState actual) {
        if (level == null || level != activeLevel || (!CircuitElementPredicates.isCircuit(previous) && !CircuitElementPredicates.isCircuit(actual))) return;
        var sections = chunks.computeIfAbsent(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), key -> new HashMap<>());
        var positions = sections.computeIfAbsent(pos.getY() >> 4, key -> new HashSet<>());
        if (CircuitElementPredicates.isCircuit(actual)) positions.add(pos.immutable());
        else positions.remove(pos);
        cachedBuckets = null;
        topologyChanged = true; // Includes circuit-to-circuit property changes.
    }
    public static void tick(Vec3 camera) {
        blockReadsThisTick = 0; paletteChecksThisTick = 0;
        if (activeLevel == null) return;
        // Sections are scanned atomically on the client thread. No partial section survives a tick,
        // so a setter callback cannot interleave and no mutation overlay is necessary.
        while (!pending.isEmpty() && paletteChecksThisTick < MAX_PALETTE_CHECKS && blockReadsThisTick + 4096 <= MAX_BLOCK_READS) {
            SectionJob job = pending.stream().min(Comparator.comparingDouble(j -> distance(j, camera))).orElseThrow();
            pending.remove(job);
            if (job.generation != generation) continue;
            var chunk = activeLevel.getChunkSource().getChunk(ChunkPos.getX(job.chunk), ChunkPos.getZ(job.chunk), ChunkStatus.FULL, false);
            if (chunk == null) continue;
            int index = job.sectionY - activeLevel.getMinSection();
            if (index < 0 || index >= chunk.getSections().length) continue;
            var section = chunk.getSections()[index];
            paletteChecksThisTick++;
            Set<BlockPos> found = new HashSet<>();
            if (!section.hasOnlyAir() && section.maybeHas(CircuitElementPredicates::isCircuit)) {
                int x0 = ChunkPos.getX(job.chunk) << 4, z0 = ChunkPos.getZ(job.chunk) << 4, y0 = job.sectionY << 4;
                for (int i = 0; i < 4096; i++) {
                    int x = i & 15, z = (i >> 4) & 15, y = i >> 8;
                    blockReadsThisTick++;
                    if (CircuitElementPredicates.isCircuit(section.getBlockState(x, y, z))) found.add(new BlockPos(x0+x, y0+y, z0+z));
                }
            }
            chunks.computeIfAbsent(job.chunk, key -> new HashMap<>()).put(job.sectionY, found);
            cachedBuckets = null;
            topologyChanged = true;
        }
        if (topologyChanged) { topologyRevision++; topologyChanged = false; }
    }
    private static double distance(SectionJob job, Vec3 camera) {
        double x = (ChunkPos.getX(job.chunk) * 16.0 + 8) - camera.x;
        double y = (job.sectionY * 16.0 + 8) - camera.y;
        double z = (ChunkPos.getZ(job.chunk) * 16.0 + 8) - camera.z;
        return x*x + y*y + z*z;
    }
}
