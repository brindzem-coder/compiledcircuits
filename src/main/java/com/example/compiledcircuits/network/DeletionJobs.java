package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.networking.NetworkGuiSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import java.util.*;
import static com.example.compiledcircuits.network.NetworkOperations.Code;

/** Loaded-only deletion, with bounded discovery/preflight and no item drops.
 * Metadata stays published until removal completes, so interruption remains repairable/retryable.
 */
public final class DeletionJobs {
    private static final Map<MinecraftServer, Job> JOBS = new IdentityHashMap<>();
    private static BlockEvent.BreakEvent activeBreakEvent;
    public static boolean isDeletionEvent(BlockEvent.BreakEvent event) { return event == activeBreakEvent; }
    private static boolean deniedBreak(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        var previous = activeBreakEvent;
        try { activeBreakEvent = new BlockEvent.BreakEvent(level, pos, state, player); return MinecraftForge.EVENT_BUS.post(activeBreakEvent); }
        finally { activeBreakEvent = previous; }
    }
    private record Target(BlockPos pos, BlockState state, CompiledNetwork network) {}
    private DeletionJobs() {}
    private static NetworkOperations.Result result(Code code, String message) {
        return new NetworkOperations.Result(code, message, List.of());
    }
    public static NetworkOperations.Result submit(CommandSourceStack source, ServerPlayer player,
                                                   NetworkSavedData data, Collection<Integer> ids) {
        if (!NetworkOperations.canModify(player)) return result(Code.FORBIDDEN, "Building is not allowed.");
        if (JOBS.containsKey(source.getServer())) return result(Code.BUSY, "A circuit deletion is already running.");
        if (data.hasUnknownMembershipReservations()) return result(Code.CONFLICT, "Unresolved circuit membership.");
        var networks = new ArrayList<CompiledNetwork>();
        BlockPos start = null;
        if (ids.isEmpty()) {
            start = NetworkSelectionData.get(player);
            if (start == null) return result(Code.NOT_FOUND, "No circuit selected.");
            if(player.serverLevel().hasChunkAt(start)){
                var selected=player.serverLevel().getBlockState(start);
                if(selected.getBlock() instanceof CrossingWireBlock&&Integer.bitCount(CrossingWireBlock.mask(selected)&NetworkSelectionData.mask(player))!=1)
                    return result(Code.INVALID_START,"Select one wire of the crossing first.");
            }
            var network = NetworkSelectionData.network(player);
            if (network != null) networks.add(network);
        } else for (int id : ids) {
            var network = data.getNetwork(id);
            if (network == null) return result(Code.NOT_FOUND, "Network no longer exists.");
            networks.add(network);
        }
        long count = 0;
        for (var network : networks) {
            if (!network.getDimension().equals(player.serverLevel().dimension().location().toString()))
                return result(Code.INVALID_ARGUMENT, "Visit the networks' dimension before deleting them.");
            count += network.getElements().size();
        }
        if (count > OperationLimits.ELEMENTS) return result(Code.TOO_LARGE, "Select at most 50000 elements.");
        if (!data.canRetireNetworks(networks)) return result(Code.BUSY, "Decompile cleanup queue is full; retry shortly.");
        JOBS.put(source.getServer(), new Job(source, player, data, networks, start));
        return result(Code.QUEUED, "Circuit deletion queued. Keep the area loaded until completion.");
    }
    public static boolean isBusy(MinecraftServer server) { return JOBS.containsKey(server); }
    public static void cancel(ServerPlayer player) {
        var job = JOBS.get(player.getServer());
        if (job != null && job.player == player) { job.fail("Player session changed."); JOBS.remove(player.getServer()); }
    }
    public static void stop(MinecraftServer server) { JOBS.remove(server); }
    public static void chunkChanged(ServerLevel level, long chunk) {
        var job = JOBS.get(level.getServer());
        if (job != null && job.level == level && job.phase < 2 && job.watched.contains(chunk)) job.changed = true;
    }
    public static void tick(MinecraftServer server) {
        var job = JOBS.get(server); if (job == null) return;
        for (int i = 0; i < 256 && !job.done; i++) {
            long budget = ServerWorkBudget.begin(server, ServerWorkBudget.Lane.SCAN);
            if (budget == 0) break;
            try { job.step(); }
            catch (IllegalArgumentException | IllegalStateException error) { job.fail("Circuit changed: " + error.getMessage()); }
            finally { ServerWorkBudget.end(server, ServerWorkBudget.Lane.SCAN, budget); }
        }
        if (job.done) JOBS.remove(server);
    }
    private static final class Job {
        final CommandSourceStack source;
        final ServerPlayer player;
        final ServerLevel level;
        final NetworkSavedData data;
        final List<CompiledNetwork> networks;
        final String dimension;
        final int started;
        final BlockPos start;
        final int startMask;
        final List<Target> targets = new ArrayList<>();
        final Map<BlockPos,Integer> seen = new HashMap<>();
        final Set<Long> watched = new HashSet<>();
        final ArrayDeque<Target> frontier = new ArrayDeque<>();
        Iterator<CompiledCircuitElement> elements = Collections.emptyIterator();
        int networkIndex, phase, index, deleted, skipped;
        boolean changed, done, initialized;
        Job(CommandSourceStack source, ServerPlayer player, NetworkSavedData data, List<CompiledNetwork> networks, BlockPos start) {
            this.source = source; this.player = player; this.level = player.serverLevel(); this.data = data;
            this.networks = List.copyOf(networks); this.start = start;
            this.startMask = NetworkSelectionData.mask(player);
            dimension = level.dimension().location().toString(); started = level.getServer().getTickCount();
        }
        void fail(String reason) {
            if (done) return;
            done = true;
            source.sendFailure(Component.literal("Deletion stopped: " + reason + " Removed blocks: " + deleted
                    + ". Compilation records retained; retry deletion or repair if needed."));
        }
        boolean available(BlockPos pos) {
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
                fail("Position outside the build area."); return false;
            }
            for (long chunk : RuntimeSignalReader.dependencyChunks(pos)) {
                if (!NetworkRuntime.isChunkAvailable(level, chunk)) {
                    fail("Area is not fully loaded. Visit it and retry."); return false;
                }
                if (phase < 2) watched.add(chunk);
            }
            return true;
        }
        BlockState read(BlockPos pos) { return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos); }
        boolean owned(Target target) {
            int mask=target.state.getBlock() instanceof CrossingWireBlock?CrossingWireBlock.mask(target.state):7;
            if (target.network == null) return Crossings.free(data, dimension, target.pos,mask);
            var owners = data.getMembershipOwners(dimension, target.pos);
            return data.getNetwork(target.network.getId()) == target.network && owners.stream().anyMatch(o->o.network()==target.network&&(Crossings.mask(o.element())&mask)==mask) && data.getBlockingRecords(dimension, target.pos).isEmpty();
        }
        void step() {
            if (!NetworkOperations.canModify(player) || player.serverLevel() != level) { fail("Player context changed."); return; }
            if (level.getServer().getTickCount() - started > 2400) { fail("Time limit exceeded."); return; }
            if (changed) { fail("World changed during preparation. Retry with the circuit unchanged."); return; }
            if (phase == 0) { collect(); return; }
            if (index < targets.size()) {
                var target = targets.get(index++);
                if (!available(target.pos)) return;
                if (!owned(target)) { fail("Circuit membership changed."); return; }
                var state = read(target.pos);
                if (state.getBlock() != target.state.getBlock()) {
                    if (phase == 1) { fail("A circuit element changed."); return; }
                    // Neighbor updates may already have removed an element. Never remove its replacement.
                    skipped++; return;
                }
                if (!level.mayInteract(player, target.pos) || !player.mayUseItemAt(target.pos, Direction.UP, player.getMainHandItem())
                        || state.getDestroySpeed(level, target.pos) < 0) { fail("A block is protected."); return; }
                if (phase == 1) return;
                if (deniedBreak(level, target.pos, state, player)) {
                    fail("A block break was denied."); return;
                }
                // A protection callback may mutate the world or membership itself.
                if (!available(target.pos)) return;
                if (!owned(target) || read(target.pos).getBlock() != state.getBlock()) { fail("Block changed during break event."); return; }
                if (!(state.getBlock() instanceof CrossingWireBlock?Crossings.remove(level,target.pos,CrossingWireBlock.mask(target.state)):level.setBlock(target.pos, Blocks.AIR.defaultBlockState(), 3))) { fail("Block removal failed."); return; }
                deleted++; return;
            }
            if (phase == 1) { phase = 2; index = 0; return; }
            for (var network : networks) if (data.getNetwork(network.getId()) != network) { fail("Compilation changed."); return; }
            if (!networks.isEmpty()) {
                var removed = data.removeNetworks(networks.stream().map(CompiledNetwork::getId).toList());
                if (removed.isEmpty()) { fail("Cleanup queue is full; retry to remove compilation records."); return; }
                DamageNotifications.removed(level.getServer(), removed);
            }
            done = true;
            source.sendSuccess(() -> Component.literal("Deleted " + deleted + " circuit blocks and " + networks.size()
                    + " compilation(s). Skipped missing/replaced blocks: " + skipped + "."), false);
            if (player.connection != null) NetworkGuiSync.sendList(player);
        }
        void collect() {
            if (!networks.isEmpty()) {
                if (!elements.hasNext()) {
                    if (networkIndex == networks.size()) { phase = 1; return; }
                    elements = networks.get(networkIndex++).getElements().iterator(); return;
                }
                var element = elements.next(); var pos = element.getPos();
                if (!available(pos)) return;
                var state = read(pos);
                if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(element.getBlockId())) { skipped++; return; }
                targets.add(new Target(pos, CrossingWireBlock.only(state,Crossings.mask(element)), networks.get(networkIndex - 1))); return;
            }
            if (!initialized) {
                initialized = true;
                if (!available(start)) return;
                var state = read(start);
                if (!(state.getBlock() instanceof IWireConnectable) || !Crossings.free(data, dimension, start,startMask)) {
                    fail("Selection is not an available circuit element."); return;
                }
                enqueue(start, CrossingWireBlock.only(state,startMask)); return;
            }
            var current = frontier.pollFirst();
            if (current == null) { phase = 1; return; }
            for (var side : Direction.values()) {
                if (!(current.state.getBlock() instanceof GateBodyBlock)
                        && !((IWireConnectable) current.state.getBlock()).canWireConnect(current.state, side)) continue;
                var next = current.pos.relative(side);
                if (level.isOutsideBuildHeight(next)) continue;
                if (!available(next)) return;
                var state = Crossings.project(read(next),side.getAxis());
                int mask=state.getBlock() instanceof CrossingWireBlock?CrossingWireBlock.mask(state):7;
                if(mask==0||!Crossings.free(data,dimension,next,mask))continue;
                if (CircuitConnections.connects(level, current.pos, current.state, state, side)) enqueue(next, state);
                if (done) return;
            }
        }
        void enqueue(BlockPos pos, BlockState state) {
            int mask=state.getBlock() instanceof CrossingWireBlock?CrossingWireBlock.mask(state):7;
            int fresh=mask&~seen.getOrDefault(pos,0);if(fresh==0)return;state=CrossingWireBlock.only(state,fresh);
            if (targets.size() >= OperationLimits.ELEMENTS) { fail("Circuit exceeds 50000 elements."); return; }
            var target = new Target(pos.immutable(), state, null);
            seen.merge(target.pos,mask,(a,b)->a|b); targets.add(target); frontier.addLast(target);
        }
    }
}
