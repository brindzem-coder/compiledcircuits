package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import static com.example.compiledcircuits.network.NetworkOperations.Code;

/** Server-thread jobs; loaded-only reads, bounded preparation and constant-size publication.
 * A subscription is a revision witness: ANY write or FULL transition in an observed chunk
 * permanently invalidates the job, including changes to rejected frontier positions.
 */
public final class CompilationJobs {
    public static final int MAX_JOBS = 8, MAX_TOTAL_ELEMENTS = 100000, MAX_CHUNKS = 8192;
    public static final int WORK_PER_JOB_TICK = 2048, WORK_PER_TICK = 8192, QUANTUM = 64;
    public static final int MAX_TICKS = 2400, MAX_FRONTIER_WORK = 350003;
    public static final long NANOS_PER_JOB_TICK = 2_000_000, NANOS_PER_TICK = 5_000_000,
            MAX_LIFETIME_NANOS = 180_000_000_000L;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<MinecraftServer, Manager> MANAGERS = new IdentityHashMap<>();
    private record Chunk(String dimension, long pos) {}
    private record Node(BlockPos pos, BlockState state) {}
    private enum Phase {
        SCANNING, VALIDATING, ASSEMBLING, PREPARING, INDEXING, FINAL_VALIDATING, COMMITTING, CLEANUP, DONE;
        final String workMetric = "scan.phase." + name() + ".work";
        final String nanosMetric = "scan.phase." + name() + ".nanos";
    }
    private CompilationJobs() {}

    public static NetworkOperations.Result submit(CommandSourceStack source, ServerPlayer player,
                                                   NetworkSavedData data, BlockPos start, String name) {
        if (!source.getServer().isSameThread()) return result(Code.FORBIDDEN, "Server thread required.");
        Manager manager = MANAGERS.computeIfAbsent(source.getServer(), Manager::new);
        if (manager.actors.containsKey(player) || manager.jobs.size() >= MAX_JOBS)
            return result(Code.BUSY, "A compilation is already running or the server queue is full.");
        PerformanceDiagnostics.add("scan.calls", 1);
        // Constant-size preflight, after the service's request/work limiter and before job allocation.
        var level = player.serverLevel();
        if (level.isOutsideBuildHeight(start) || !level.getWorldBorder().isWithinBounds(start))
            return result(Code.INVALID_START, "Selection is outside the build area.");
        if (!NetworkRuntime.isChunkAvailable(level, ChunkPos.asLong(start.getX() >> 4, start.getZ() >> 4)))
            return result(Code.INCOMPLETE_UNLOADED, "The selected chunk is not fully loaded. Visit the area normally, then retry.");
        if (!(level.getChunkSource().getChunkNow(start.getX() >> 4, start.getZ() >> 4).getBlockState(start).getBlock() instanceof IWireConnectable))
            return result(Code.INVALID_START, "Selection is not a circuit element.");
        if (!data.getMembershipOwners(level.dimension().location().toString(), start).isEmpty()
                || !data.getBlockingRecords(level.dimension().location().toString(), start).isEmpty())
            return result(Code.CONFLICT, "The selected position is already reserved.");
        Job job = new Job(manager, source, player, data, start, name);
        manager.actors.put(player, job); manager.jobs.addLast(job);
        PerformanceDiagnostics.add("scan.queued", 1);
        PerformanceDiagnostics.max("scan.activeJobs.peak", manager.jobs.size());
        return result(Code.QUEUED, "Compilation queued. Keep the circuit loaded and unchanged; changing the selection cancels it.");
    }
    private static NetworkOperations.Result result(Code code, String message) {
        return new NetworkOperations.Result(code, message, List.of());
    }
    public static boolean isBusy(ServerPlayer player) {
        var manager = MANAGERS.get(player.getServer()); return manager != null && manager.actors.containsKey(player);
    }
    public static void cancel(ServerPlayer player) {
        var manager = MANAGERS.get(player.getServer());
        if (manager != null) { var job = manager.actors.get(player); if (job != null) job.fail(Code.CANCELLED, "Selection or player session changed."); }
    }
    public static void blockChanged(ServerLevel level, BlockPos pos) {
        chunkChanged(level, ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    }
    public static void chunkChanged(ServerLevel level, long pos) {
        var manager = MANAGERS.get(level.getServer()); if (manager == null) return;
        var jobs = manager.watchers.get(new Chunk(level.dimension().location().toString(), pos));
        if (jobs != null) for (var job : jobs) job.changed = true; // at most MAX_JOBS, no world reads
    }
    public static void tick(MinecraftServer server) {
        var manager = MANAGERS.get(server); if (manager != null) manager.tick();
    }
    public static void stop(MinecraftServer server) {
        var manager = MANAGERS.remove(server); if (manager == null) return;
        // Shutdown has no future ticks; release temporary claims before another session can use data.
        for (var job : manager.jobs) {
            job.fail(Code.CANCELLED, "Server stopped.");
            while (job.phase != Phase.DONE) job.cleanup();
        }
    }
    private static final class Manager {
        final MinecraftServer server;
        final ArrayDeque<Job> jobs = new ArrayDeque<>();
        final Map<ServerPlayer, Job> actors = new IdentityHashMap<>();
        final Map<Chunk, Set<Job>> watchers = new HashMap<>();
        int retainedElements, lastTick = Integer.MIN_VALUE;
        Manager(MinecraftServer server) { this.server = server; }
        void tick() {
            int tick = server.getTickCount(); if (lastTick == tick) return; lastTick = tick;
            long began = System.nanoTime(); int work = 0, idle = 0;
            for (var job : jobs) { job.tickWork = 0; job.tickNanos = 0; }
            while (!jobs.isEmpty() && work < WORK_PER_TICK && System.nanoTime() - began < NANOS_PER_TICK) {
                var job = jobs.removeFirst();
                if (job.tickWork >= WORK_PER_JOB_TICK || job.tickNanos >= NANOS_PER_JOB_TICK) {
                    jobs.addLast(job); if (++idle >= jobs.size()) break; continue;
                }
                idle = 0;
                for (int i = 0; i < QUANTUM && work < WORK_PER_TICK && job.tickWork < WORK_PER_JOB_TICK
                        && job.tickNanos < NANOS_PER_JOB_TICK && System.nanoTime() - began < NANOS_PER_TICK; i++) {
                    long shared = ServerWorkBudget.begin(server, ServerWorkBudget.Lane.SCAN);
                    if (shared == 0) { jobs.addLast(job); return; }
                    long started = System.nanoTime(); var phase = job.phase;
                    try { job.step(); }
                    catch (IllegalArgumentException | IllegalStateException invalid) { job.fail(invalid instanceof NetworkSavedData.AdmissionException admission?admission.code:Code.CONFLICT, invalid.getMessage()); }
                    finally { ServerWorkBudget.end(server, ServerWorkBudget.Lane.SCAN, shared); }
                    long elapsed = System.nanoTime() - started;
                    job.tickWork++; work++;
                    PerformanceDiagnostics.add(phase.workMetric, 1);
                    PerformanceDiagnostics.add(phase.nanosMetric, elapsed);
                    PerformanceDiagnostics.add("compile.job.cpuNanos", elapsed);
                    PerformanceDiagnostics.max("scan.step.maxNanos", elapsed);
                    job.tickNanos += System.nanoTime() - started;
                    if (job.phase == Phase.DONE) break;
                }
                PerformanceDiagnostics.max("scan.job.tickWork", job.tickWork);
                if (job.phase == Phase.DONE) actors.remove(job.player); else jobs.addLast(job);
            }
            PerformanceDiagnostics.max("scan.tickWork", work);
            PerformanceDiagnostics.max("scan.tickNanos", System.nanoTime() - began);
        }
    }
    private static final class Job {
        final Manager manager;
        final CommandSourceStack source;
        final ServerPlayer player;
        final ServerLevel level;
        final NetworkSavedData data;
        final BlockPos start;
        final String dimension, name;
        final int startedTick;
        final long startedNanos = System.nanoTime();
        final ArrayDeque<Node> frontier = new ArrayDeque<>();
        final NavigableMap<BlockPos, BlockState> snapshots = new TreeMap<>(Comparator
                .comparingInt((BlockPos p) -> p.getX()).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        final Set<Chunk> watched = new LinkedHashSet<>();
        final CompiledNetwork.Builder builder = new CompiledNetwork.Builder();
        Phase phase = Phase.SCANNING;
        boolean changed, initialized, notified;
        int direction, frontierWork, elementId, tickWork, retained;
        int minX, maxX, minZ, maxZ;
        long tickNanos;
        Node current;
        Iterator<Map.Entry<BlockPos, BlockState>> validation;
        Iterator<CompiledCircuitElement> preparation;
        Iterator<Chunk> chunkValidation, cleanupChunks;
        CompiledNetwork candidate;
        NetworkSavedData.PreparedAdmission admission;
        Job(Manager manager, CommandSourceStack source, ServerPlayer player, NetworkSavedData data, BlockPos start, String name) {
            this.manager = manager; this.source = source; this.player = player; this.level = player.serverLevel();
            this.data = data; this.start = start.immutable(); this.name = name == null ? null : name.trim();
            dimension = level.dimension().location().toString(); startedTick = manager.server.getTickCount();
            minX = maxX = start.getX(); minZ = maxZ = start.getZ();
        }
        void step() {
            if (phase == Phase.CLEANUP) { cleanup(); return; }
            if (!NetworkOperations.canModify(player)) { fail(Code.FORBIDDEN, "Building permission was lost."); return; }
            if (player.serverLevel() != level || !start.equals(NetworkSelectionData.get(player))
                    || NetworkSavedData.get(manager.server) != data) { fail(Code.CANCELLED, "Compilation context changed."); return; }
            if (manager.server.getTickCount() - startedTick > MAX_TICKS || System.nanoTime() - startedNanos > MAX_LIFETIME_NANOS) {
                fail(Code.TIMEOUT, "Compilation exceeded its lifetime; retry with stable loaded chunks."); return;
            }
            if (changed) { fail(Code.CHANGED_DURING_SCAN, "An observed chunk changed. Retry once the circuit is stable."); return; }
            switch (phase) {
                case SCANNING -> scan();
                case VALIDATING -> {
                    if (validation.hasNext()) {
                        var item = validation.next(); var state = read(item.getKey()); if (state == null) return;
                        if (state != item.getValue()) { fail(Code.CHANGED_DURING_SCAN, "Element state changed."); return; }
                    } else { validation = snapshots.entrySet().iterator(); phase = Phase.ASSEMBLING; }
                }
                case ASSEMBLING -> {
                    if (validation.hasNext()) {
                        var item = validation.next(); var state = item.getValue();
                        CircuitElementType type = state.getBlock() instanceof InputEndpointBlock ? CircuitElementType.INPUT
                                : state.getBlock() instanceof OutputEndpointBlock ? CircuitElementType.OUTPUT : CircuitElementType.WIRE;
                        builder.add(new CompiledCircuitElement(++elementId, item.getKey(), type, CompiledBlockStateCodec.capture(state)));
                    } else {
                        if (builder.inputsEmpty() || builder.outputsEmpty()) { fail(Code.INVALID_START, "Circuit needs an input and an output."); return; }
                        candidate = builder.seal(name, dimension); admission = data.prepareCompilation(candidate);
                        preparation = candidate.getElements().iterator(); phase = Phase.PREPARING;
                    }
                }
                case PREPARING -> {
                    if (preparation.hasNext()) admission.stage(preparation.next()); else phase = Phase.INDEXING;
                }
                case INDEXING -> {
                    if (!admission.preparedRuntime.indexNext()) { chunkValidation = watched.iterator(); phase = Phase.FINAL_VALIDATING; }
                }
                case FINAL_VALIDATING -> {
                    if (chunkValidation.hasNext()) {
                        if (!NetworkRuntime.isChunkAvailable(level, chunkValidation.next().pos))
                            fail(Code.INCOMPLETE_UNLOADED, "A dependency chunk is no longer available.");
                    } else phase = Phase.COMMITTING;
                }
                case COMMITTING -> {
                    if (!level.getWorldBorder().isWithinBounds(new BlockPos(minX, start.getY(), minZ))
                            || !level.getWorldBorder().isWithinBounds(new BlockPos(maxX, start.getY(), maxZ))) {
                        fail(Code.INVALID_ARGUMENT, "World border no longer contains the circuit."); return;
                    }
                    // Every O(N) collection/index was prepared above; no world callbacks occur here.
                    long began = System.nanoTime(); admission.publish();
                    PerformanceDiagnostics.max("scan.commit.maxNanos", System.nanoTime() - began);
                    PerformanceDiagnostics.add("scan.success", 1);
                    PerformanceDiagnostics.add("compile.completed", 1);
                    PerformanceDiagnostics.add("compile.job.wallNanos", System.nanoTime() - startedNanos);
                    source.sendSuccess(() -> Component.literal("Compiled " + candidate.getName() + " (#" + candidate.getId() + ")."), false);
                    notified = true; admission = null; beginCleanup();
                }
                default -> throw new IllegalStateException("Invalid job phase");
            }
        }
        BlockState read(BlockPos pos) {
            if (level.isOutsideBuildHeight(pos)) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            if (!level.getWorldBorder().isWithinBounds(pos)) { fail(Code.INVALID_ARGUMENT, "Circuit frontier crosses the world border."); return null; }
            var chunk = new Chunk(dimension, ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            if (!watched.contains(chunk)) {
                if (watched.size() >= MAX_CHUNKS) { fail(Code.TOO_LARGE, "Too many dependency chunks."); return null; }
                watched.add(chunk); manager.watchers.computeIfAbsent(chunk, k -> new HashSet<>()).add(this);
            }
            if (!NetworkRuntime.isChunkAvailable(level, chunk.pos)) { fail(Code.INCOMPLETE_UNLOADED, "A possible continuation is in an unavailable chunk. Visit the area normally, then retry."); return null; }
            PerformanceDiagnostics.add("scan.blockReads", 1);
            return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos);
        }
        void enqueue(BlockPos pos, BlockState state) {
            if (snapshots.containsKey(pos)) return;
            if (snapshots.size() >= data.capacity().networkLimit()) { fail(Code.TOO_LARGE, "Circuit exceeds configured network limit " + data.capacity().networkLimit() + "."); return; }
            if (manager.retainedElements >= MAX_TOTAL_ELEMENTS) { fail(Code.BUSY, "Server compilation memory budget is full."); return; }
            pos = pos.immutable(); snapshots.put(pos, state); frontier.addLast(new Node(pos, state));
            manager.retainedElements++; retained++;
            PerformanceDiagnostics.max("scan.retainedElements.peak", manager.retainedElements);
            minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
            PerformanceDiagnostics.add("scan.visited", 1); PerformanceDiagnostics.max("scan.queuePeak", frontier.size());
        }
        void scan() {
            if (++frontierWork > MAX_FRONTIER_WORK) { fail(Code.TOO_LARGE, "Frontier work limit exceeded."); return; }
            if (!initialized) {
                initialized = true; var state = read(start); if (state == null) return;
                if (!(state.getBlock() instanceof IWireConnectable)) { fail(Code.INVALID_START, "Selection is no longer a circuit element."); return; }
                enqueue(start, state); return;
            }
            if (current == null) {
                current = frontier.pollFirst(); direction = 0;
                if (current == null) { validation = snapshots.entrySet().iterator(); phase = Phase.VALIDATING; }
                else PerformanceDiagnostics.add("scan.queuePops", 1);
                return;
            }
            Direction dir = DIRECTIONS[direction++];
            if (((IWireConnectable)current.state.getBlock()).canWireConnect(current.state, dir)) {
                BlockPos next = current.pos.relative(dir);
                if (!level.isOutsideBuildHeight(next) && !snapshots.containsKey(next)) {
                    BlockState state = read(next); if (state == null) return;
                    if (state.getBlock() instanceof IWireConnectable neighbor && neighbor.canWireConnect(state, dir.getOpposite())) enqueue(next, state);
                }
            }
            if (direction == DIRECTIONS.length) current = null;
        }
        void fail(Code code, String message) {
            if (phase == Phase.CLEANUP || phase == Phase.DONE) return;
            PerformanceDiagnostics.add("scan.failure", 1); PerformanceDiagnostics.add("scan.result." + code, 1);
            if (!notified && !player.isRemoved()) source.sendFailure(Component.literal(code + ": " + message));
            notified = true; if (admission != null) admission.beginRollback(); beginCleanup();
        }
        void beginCleanup() { phase = Phase.CLEANUP; cleanupChunks = watched.iterator(); }
        void cleanup() {
            if (admission != null && admission.rollbackNext()) return;
            admission = null;
            if (cleanupChunks.hasNext()) {
                Chunk chunk = cleanupChunks.next(); var jobs = manager.watchers.get(chunk);
                if (jobs != null) { jobs.remove(this); if (jobs.isEmpty()) manager.watchers.remove(chunk); }
                cleanupChunks.remove(); return;
            }
            manager.retainedElements -= retained; phase = Phase.DONE;
        }
    }
}
