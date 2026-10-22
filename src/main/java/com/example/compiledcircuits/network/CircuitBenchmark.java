package com.example.compiledcircuits.network;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.networking.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Bounded, opt-in measurements of an isolated compiled snapshot. Never runs the live program. */
@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID)
public final class CircuitBenchmark {
    public static final int MAX_INPUTS = 16, REPEATS = 3;
    private static final Map<MinecraftServer, Session> sessions = new WeakHashMap<>();
    private static final class Session {
        Job job;
        final LinkedHashMap<Integer, Job> results = new LinkedHashMap<>();
        final Map<UUID, Integer> requests = new HashMap<>();
    }
    static final class Stats {
        long nanos, work, minTime = Long.MAX_VALUE, maxTime = -1, minWork = Long.MAX_VALUE, maxWork = -1;
        int count, fastest, slowest, easiest, hardest;
        void add(int combination, long time, long operations) {
            count++; nanos += time; work += operations;
            if (time < minTime) { minTime = time; fastest = combination; }
            if (time > maxTime) { maxTime = time; slowest = combination; }
            if (operations < minWork) { minWork = operations; easiest = combination; }
            if (operations > maxWork) { maxWork = operations; hardest = combination; }
        }
    }
    static final class Job {
        final CompiledNetwork network;
        final UUID player;
        final boolean optimized, sequence;
        NetworkTruthTable tableSnapshot;
        final boolean tableMode;
        List<String> previousReport = List.of();
        long[] sequenceTimes, sequenceWork;
        boolean[] sequenceFailed;
        int sequenceStep;
        int originalNodes, evaluationNodes;
        int tables, tableBytes, tableNodes;
        final long started = System.nanoTime();
        final Iterator<CompiledCircuitElement> elements;
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Map<String, Boolean> memory;
        final Stats stats = new Stats();
        LogicProgram program;
        boolean[] baseline;
        List<BlockPos> inputs = List.of();
        int total, next, warmup, failed, firstFailure = -1, repeat;
        long time, work;
        boolean combinationFailed, done, stateful;
        String status = "Preparing snapshot";
        Job(CompiledNetwork n, UUID player) {
            this(n, player, n.isOptimized());
        }
        Job(CompiledNetwork n, UUID player, boolean optimized) {
            this(n, player, optimized, false);
        }
        Job(CompiledNetwork n, UUID player, boolean optimized, boolean sequence) {
            this(n, player, optimized, sequence, true);
        }
        Job(CompiledNetwork n, UUID player, boolean optimized, boolean sequence, boolean useTable) {
            this.optimized = optimized; this.sequence = sequence; this.tableSnapshot = useTable ? n.networkTable() : null; this.tableMode = tableSnapshot != null;
            network = n; this.player = player; elements = n.getElements().iterator(); memory = new HashMap<>(n.nodeMemory());
        }
        void step() {
            if (program == null) {
                for (int i = 0; i < 128 && elements.hasNext(); i++) {
                    var e = elements.next(); states.put(e.getPos(), e.resolveState().state().orElseThrow());
                }
                if (elements.hasNext()) return;
                program = LogicProgram.compile(states); program.importNodeMemory(memory);
                if (tableSnapshot != null) program.installTable(tableSnapshot, states.keySet());
                if (optimized) program.optimize(states.keySet());
                originalNodes = program.nodeCount(); evaluationNodes = program.evaluationNodeCount();
                tables = program.truthTableCount(); tableBytes = program.truthTableBytes(); tableNodes = program.truthTableNodes();
                stateful = program.hasStatefulMemory();
                baseline = program.benchmarkState(); inputs = program.benchmarkInputs();
                if (inputs.size() > MAX_INPUTS) {
                    finish("Too many independent inputs: " + inputs.size() + " (maximum 16)"); return;
                }
                total = (1 << inputs.size()) * (sequence ? 2 : 1);
                if (sequence) {
                    sequenceTimes = new long[total]; sequenceWork = new long[total]; sequenceFailed = new boolean[total];
                }
                status = "Warming up"; return;
            }
            if (warmup < 128) {
                int step = warmup++ % total;
                evaluate(sequence ? sequenceMask(step) : step, false);
                if (warmup == 128) { program.benchmarkReset(baseline); status = "Measuring"; }
                return;
            }
            if (sequence) {
                evaluate(sequenceMask(sequenceStep), true);
                sequenceTimes[sequenceStep] += time; sequenceWork[sequenceStep] += work;
                sequenceFailed[sequenceStep] |= combinationFailed;
                if (repeat == REPEATS - 1) {
                    if (sequenceFailed[sequenceStep]) { failed++; if (firstFailure < 0) firstFailure = sequenceStep; }
                    else stats.add(sequenceStep, sequenceTimes[sequenceStep] / REPEATS, sequenceWork[sequenceStep] / REPEATS);
                    next++;
                }
                time = work = 0; combinationFailed = false;
                if (++sequenceStep == total) {
                    sequenceStep = 0;
                    if (++repeat == REPEATS) finish("Complete");
                    else program.benchmarkReset(baseline);
                }
                return;
            }
            evaluate(next, true);
            if (++repeat == REPEATS) {
                if (combinationFailed) { failed++; if (firstFailure < 0) firstFailure = next; }
                else stats.add(next, time / REPEATS, work / REPEATS);
                next++; repeat = 0; time = work = 0; combinationFailed = false;
                if (next == total) finish("Complete");
            }
        }
        // Gray code changes one input at a time; each vector is followed by one unchanged sample.
        static int sequenceMask(int step) { int vector = step / 2; return vector ^ (vector >>> 1); }
        private void evaluate(int mask, boolean measured) {
            Map<BlockPos, Boolean> values = new HashMap<>();
            for (int i = 0; i < inputs.size(); i++) values.put(inputs.get(i), (mask & (1 << i)) != 0);
            if (!sequence) program.benchmarkReset(baseline); // Preparation is outside the timed region.
            long start = System.nanoTime();
            try { program.evaluate(values); }
            catch (LogicProgram.UnstableFeedback failure) { if (measured) combinationFailed = true; }
            finally { if (measured) { time += System.nanoTime() - start; work += program.lastWork() + program.lastChecks(); } }
        }
        void finish(String message) {
            done = true; status = message; program = null; baseline = null; tableSnapshot = null;
            states.clear(); memory.clear(); sequenceTimes = sequenceWork = null; sequenceFailed = null;
        }
        String bits(int mask) {
            String prefix = sequence ? "step " + mask + ": " : "";
            if (sequence) mask = sequenceMask(mask);
            if (inputs.isEmpty()) return prefix + "(no inputs)";
            var s = new StringBuilder(prefix);
            for (int i = 0; i < inputs.size(); i++) s.append((mask & (1 << i)) == 0 ? '0' : '1');
            return s.toString();
        }
        List<String> lines() {
            List<String> lines = new ArrayList<>();
            lines.add("Network #" + network.getId() + " | " + status);
            lines.add("Inputs: " + inputs.size() + (sequence ? " | sequence steps: " : " | combinations: ") + next + "/" + total
                    + " | repeats: " + REPEATS + (sequence && !done ? " | pass: " + (repeat + 1) + " step: " + sequenceStep : ""));
            lines.add((tableMode ? "Whole-network table" : optimized ? "Optimized graph" : "Original graph") + " | evaluation steps: " + originalNodes + " -> " + evaluationNodes);
            lines.add("Truth tables: " + tables + " | replaced nodes: " + tableNodes + " | data: " + tableBytes + " bytes");
            lines.add("Isolated benchmark. Live: " + network.tableStatus());
            lines.add(sequence ? "Sequence: Gray code + unchanged samples; memory carries between steps." : stateful
                    ? "Memory/feedback: same captured state for EVERY sample."
                    : "Combinational circuit: all input combinations.");
            if (stats.count > 0) {
                lines.add(String.format(Locale.ROOT, "Time avg/min/max: %.3f / %.3f / %.3f us", stats.nanos / (double) stats.count / 1000, stats.minTime / 1000.0, stats.maxTime / 1000.0));
                lines.add("Fastest: " + bits(stats.fastest) + " | slowest: " + bits(stats.slowest));
                lines.add(String.format(Locale.ROOT, "Work avg/min/max: %.1f / %d / %d", stats.work / (double) stats.count, stats.minWork, stats.maxWork));
                lines.add("Least work: " + bits(stats.easiest) + " | most work: " + bits(stats.hardest));
            }
            lines.addAll(previousReport);
            lines.add("Successful: " + stats.count + " | unstable/limited: " + failed + (firstFailure < 0 ? "" : " | first: " + bits(firstFailure)));
            lines.add("Work = evaluated nodes/inputs + table reads/outputs + skip bit checks.");
            lines.add("Time includes skip checks; not CPU %. Min/max average 3 matching samples.");
            lines.add("No world I/O or input preparation timed. Failed combinations excluded from averages.");
            for (int first = 0; first < Math.min(MAX_INPUTS, inputs.size()); first += 4) {
                var row = new StringBuilder("Bits left to right: ");
                for (int i = first; i < Math.min(first + 4, inputs.size()); i++) {
                    var p = inputs.get(i); row.append(i).append("=(").append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()).append(") ");
                }
                lines.add(row.toString());
            }
            return lines;
        }
    }
    public static int start(CommandSourceStack source) {
        return start(source, false);
    }
    public static int start(CommandSourceStack source, boolean baseline) {
        return start(source, baseline, false);
    }
    public static int start(CommandSourceStack source, boolean baseline, boolean sequence) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        var server = source.getServer(); var data = NetworkSavedData.get(server);
        var pos = NetworkSelectionData.get(player);
        var n = pos == null ? null : data.findNetworkContaining(player.serverLevel(), pos);
        if (n == null) { source.sendFailure(Component.literal("Select a compiled network with the selector first.")); return 0; }
        if (n.getElements().size() > LogicProgram.MAX_ELEMENTS) { source.sendFailure(Component.literal("Benchmark limit: 8192 elements.")); return 0; }
        var session = sessions.computeIfAbsent(server, k -> new Session());
        if (session.job != null) { source.sendFailure(Component.literal("A benchmark is already running on this server.")); return 0; }
        var job = new Job(n, player.getUUID(), !baseline && n.isOptimized(), sequence, !baseline);
        var previous = session.results.get(n.getId());
        if (previous != null && previous.network == n && previous.done && previous.status.equals("Complete")
                && previous.stats.count > 0 && previous.sequence == sequence && previous.optimized == job.optimized
                && previous.tableMode != job.tableMode) {
            job.previousReport = List.of(
                    String.format(Locale.ROOT, "Previous %s time avg/min/max: %.3f / %.3f / %.3f us", !previous.tableMode ? "graph" : "table",
                            previous.stats.nanos / (double) previous.stats.count / 1000, previous.stats.minTime / 1000.0, previous.stats.maxTime / 1000.0),
                    String.format(Locale.ROOT, "Previous work avg/min/max: %.1f / %d / %d | table bytes: %d", previous.stats.work / (double) previous.stats.count,
                            previous.stats.minWork, previous.stats.maxWork, previous.tableBytes));
        }
        session.job = job; session.results.put(n.getId(), job);
        while (session.results.size() > 16) session.results.remove(session.results.keySet().iterator().next());
        source.sendSuccess(() -> Component.literal("Benchmark started. Hold the selector to see progress; /circuit benchmark cancel stops it."), false);
        return 1;
    }
    public static int cancel(CommandSourceStack source) {
        var session = sessions.get(source.getServer());
        if (session == null || session.job == null) {
            source.sendSuccess(() -> Component.literal("No benchmark is running. Completed measurements remain visible."), false); return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer p) || (!session.job.player.equals(p.getUUID()) && !source.hasPermission(2))) return 0;
        session.job.finish("Cancelled (partial results)"); session.job = null;
        source.sendSuccess(() -> Component.literal("Benchmark cancelled. Collected partial measurements remain visible."), false); return 1;
    }
    public static void send(ServerPlayer player, int id) {
        var server = player.getServer(); if (server == null) return;
        var session = sessions.computeIfAbsent(server, k -> new Session());
        int tick = server.getTickCount(); var last = session.requests.get(player.getUUID());
        if (last != null && tick - last < 10) return;
        session.requests.put(player.getUUID(), tick);
        var n = NetworkSavedData.get(server).getNetwork(id); var job = session.results.get(id);
        List<String> lines = n == null ? List.of("Network no longer exists.")
                : job == null || job.network != n ? List.of("Network #" + id + ": no measurements", "Select it with the selector, then /circuit benchmark") : job.lines();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BenchmarkS2CPacket(id, lines));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer(); var session = sessions.get(server);
        if (session == null || session.job == null) return;
        var job = session.job;
        if (NetworkSavedData.get(server).getNetwork(job.network.getId()) != job.network) job.finish("Network removed/replaced");
        if (server.getPlayerList().getPlayer(job.player) == null) job.finish("Player disconnected (partial results)");
        if (System.nanoTime() - job.started > 120_000_000_000L) job.finish("Time limit (partial results)");
        long start = System.nanoTime();
        for (int i = 0; i < 128 && !job.done && System.nanoTime() - start < 1_000_000; i++) {
            long unit = ServerWorkBudget.begin(server, ServerWorkBudget.Lane.SCAN); if (unit == 0) break;
            try { job.step(); }
            catch (RuntimeException failure) {
                String reason = Objects.toString(failure.getMessage(), failure.getClass().getSimpleName());
                job.finish("Cannot measure: " + reason.substring(0, Math.min(180, reason.length())));
            }
            finally { ServerWorkBudget.end(server, ServerWorkBudget.Lane.SCAN, unit); }
        }
        if (job.done) {
            session.job = null; var player = server.getPlayerList().getPlayer(job.player);
            if (player != null) player.sendSystemMessage(Component.literal("Benchmark #" + job.network.getId() + ": " + job.status + ". " + job.next + "/" + job.total + (job.sequence ? " sequence steps." : " combinations.")));
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { sessions.remove(event.getServer()); }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        var server = event.getEntity().getServer(); var session = sessions.get(server);
        if (session != null) session.requests.remove(event.getEntity().getUUID());
    }
}
