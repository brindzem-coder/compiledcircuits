package com.example.compiledcircuits.network;

import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Server-owned derived state. Every world read and neighbor callback runs in drain, never a load hook. */
public final class NetworkRuntime {
    public static final int WORK_PER_TICK = 2048;
    public static final int LOAD_RETRIES_PER_TICK = 128;
    private final NetworkSavedData data;
    private MinecraftServer server;
    private final Map<CompiledNetwork, Entry> entries = new IdentityHashMap<>();
    private final Map<ChunkKey, Map<Entry, Part>> chunks = new HashMap<>();
    private final LinkedHashSet<ChunkKey> unloading = new LinkedHashSet<>();
    private final LinkedHashSet<ChunkKey> loading = new LinkedHashSet<>();
    private final LinkedHashSet<Entry> queue = new LinkedHashSet<>();
    private final class Retired {
        final Entry entry;
        final Iterator<BlockPos> outputs;
        final Iterator<CompiledCircuitElement> elements;
        final Iterator<ChunkKey> parts;
        Retired(Entry entry) {
            this.entry=entry;outputs=entry.network.getOutputs().iterator();
            elements=entry.network.getElements().iterator();parts=entry.parts.keySet().iterator();
        }
        boolean step() {
            if(outputs.hasNext()) {
                var pos=outputs.next();var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(entry.dimension)));
                if(level!=null)notifyOutput(level,pos);
            } else if(elements.hasNext()) {
                var element=elements.next();
                if(data.getNetwork(entry.network.getId())!=entry.network)data.retireClaim(entry.network,element);
            } else if(parts.hasNext()) {
                var key=parts.next();var bucket=chunks.get(key);
                if(bucket!=null){bucket.remove(entry);if(bucket.isEmpty()){chunks.remove(key);loading.remove(key);}}
            }
            retiredWork--;return outputs.hasNext()||elements.hasNext()||parts.hasNext();
        }
    }
    public static final int MAX_RETIRED_WORK = 200000;
    private int retiredWork;
    boolean canRetire(Collection<CompiledNetwork> networks) {
        long work=retiredWork;
        for(var network:networks){var entry=entries.get(network);if(entry!=null)work+=(long)network.getElements().size()+network.getOutputs().size()+entry.parts.size();}
        return server==null || work<=MAX_RETIRED_WORK;
    }
    private final ArrayDeque<Retired> retired = new ArrayDeque<>();
    private boolean draining, retireTurn;
    private int budgetTick = Integer.MIN_VALUE, remaining;
    private record ChunkKey(String dimension, long pos) {}
    private static final class Part {
        final List<CompiledCircuitElement> elements = new ArrayList<>();
        boolean input;
    }
    private static final class Entry {
        final CompiledNetwork network;
        final String dimension;
        ResourceKey<Level> levelKey;
        final Map<ChunkKey, Part> parts = new HashMap<>();
        Iterator<CompiledCircuitElement> initial;
        final LinkedHashSet<CompiledCircuitElement> checks = new LinkedHashSet<>();
        final LinkedHashSet<Part> partsToCheck = new LinkedHashSet<>();
        Iterator<CompiledCircuitElement> loadedChecks = Collections.emptyIterator();
        int startedAt = -1;
        boolean firstReady;
        Iterator<BlockPos> inputs;
        Iterator<BlockPos> outputs = Collections.emptyIterator();
        boolean dirtyInputs = true, inputOr, available;
        final LinkedHashSet<ChunkKey> waiting = new LinkedHashSet<>();
        int waitingChecks;
        Entry(CompiledNetwork network) {
            this.network = network;
            dimension = network.getDimension();
            initial = network.getElements().iterator();
        }
    }
    NetworkRuntime(NetworkSavedData data) { this.data = data; }
    void bind(MinecraftServer server) { this.server = server; }
    void add(CompiledNetwork network) {
        network.reactivate();
        Entry entry = new Entry(network);
        entries.put(network, entry);
        data.integrity().add(network);data.damage().add(network);
        network.runtimeState(false, false);
        for (var element : network.getElements()) prepareElement(entry, element);
        entry.parts.forEach((key, part) -> chunks.computeIfAbsent(key, k -> new IdentityHashMap<>()).put(entry, part));
        entry.outputs = network.getOutputs().iterator();
        queue.add(entry);
    }
    private void prepareElement(Entry entry, CompiledCircuitElement element) {
        var network = entry.network;
        var key = new ChunkKey(network.getDimension(), RuntimeSignalReader.chunk(element.getPos()));
        entry.parts.computeIfAbsent(key, k -> new Part()).elements.add(element);
        if (element.getType() == CircuitElementType.INPUT) {
            var dependencies = RuntimeSignalReader.dependencyChunks(element.getPos());
            for (long chunk : dependencies)
                entry.parts.computeIfAbsent(new ChunkKey(network.getDimension(), chunk), k -> new Part()).input = true;
        }
        if (element.getType() == CircuitElementType.OUTPUT) for (Direction direction : Direction.values())
            entry.parts.computeIfAbsent(new ChunkKey(network.getDimension(), RuntimeSignalReader.chunk(element.getPos().relative(direction))), k -> new Part());
    }
    final class Prepared {
        private final Entry entry;
        private Iterator<Map.Entry<ChunkKey, Part>> parts;
        Prepared(CompiledNetwork network) { entry = new Entry(network); }
        void add(CompiledCircuitElement element) { prepareElement(entry, element); }
        boolean indexNext() {
            if (parts == null) parts = entry.parts.entrySet().iterator();
            if (!parts.hasNext()) return false;
            var item = parts.next();
            chunks.computeIfAbsent(item.getKey(), k -> new IdentityHashMap<>()).put(entry, item.getValue());
            return true;
        }
        void publish() {
            entries.put(entry.network, entry); data.integrity().add(entry.network);data.damage().add(entry.network); entry.outputs = entry.network.getOutputs().iterator(); queue.add(entry);
        }
        void beginRollback() { parts = entry.parts.entrySet().iterator(); }
        boolean rollbackNext() {
            if (!parts.hasNext()) return false;
            var key = parts.next().getKey(); var bucket = chunks.get(key);
            if (bucket != null) { bucket.remove(entry); if (bucket.isEmpty()) { chunks.remove(key); loading.remove(key); } }
            return true;
        }
    }
    Prepared prepare(CompiledNetwork network) { return new Prepared(network); }
    void remove(CompiledNetwork network) {
        Entry entry=entries.remove(network);data.integrity().remove(network);data.damage().remove(network);
        if(server!=null)RepairJobs.removed(server,network);
        network.retire();network.runtimeState(false,false);
        if(entry==null)return;
        queue.remove(entry);
        int work=network.getElements().size()+network.getOutputs().size()+entry.parts.size();
        if(work>0){retired.addLast(new Retired(entry));retiredWork+=work;}
    }
    public static boolean isChunkAvailable(ServerLevel level, long pos) {
        var runtime = NetworkSavedData.get(level.getServer()).runtime();
        var chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(pos), ChunkPos.getZ(pos));
        return chunk != null && chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                && !runtime.unloading.contains(new ChunkKey(level.dimension().location().toString(), pos));
    }
    public static void chunkChanged(ServerLevel level, long pos, boolean loaded) {
        // Forge unload is on the server thread; load may precede FULL promotion.
        if (!level.getServer().isSameThread()) {
            level.getServer().execute(() -> chunkChanged(level, pos, loaded));
            return;
        }
        CompilationJobs.chunkChanged(level, pos);
        var runtime = NetworkSavedData.get(level.getServer()).runtime();
        var key = new ChunkKey(level.dimension().location().toString(), pos);
        if (loaded) runtime.unloading.remove(key);
        var affected = runtime.chunks.get(key);
        if (affected == null) return;
        if (loaded) { runtime.unloading.remove(key); runtime.loading.add(key); }
        else { runtime.loading.remove(key); runtime.unloading.add(key); }
        PerformanceDiagnostics.add("runtime.chunkAffectedNetworks", affected.size());
        for (var item : affected.entrySet()) {
            Entry entry = item.getKey(); Part part = item.getValue();
            if (!entry.network.isPublished() || runtime.entries.get(entry.network)!=entry) continue;
            if (loaded) runtime.data.integrity().loaded(level, entry.network);
            if (loaded && (part.input || !part.elements.isEmpty())) entry.waiting.add(key);
            else entry.waiting.remove(key);
            entry.waitingChecks=entry.waiting.size();
            if ((loaded && !part.elements.isEmpty()) || part.input) {
                int before = entry.network.getEffectiveSignal();
                entry.network.runtimeState(false, false);
                runtime.resetInputs(entry);
                if (loaded) entry.partsToCheck.add(part);
                if (before != 0 || loaded) entry.outputs = entry.network.getOutputs().iterator();
                runtime.queue.add(entry);
            }
        }
        // No world access here: especially not getBlockState or neighborChanged.
    }
    private void resetInputs(Entry entry) { entry.dirtyInputs = true; entry.inputs = null; }
    public static void inputChanged(ServerLevel level, BlockPos inputPos) {
        var data = NetworkSavedData.get(level.getServer());
        var network = data.findNetworkByInput(level, inputPos);
        if (network == null) return;
        var runtime = data.runtime(); var entry = runtime.entries.get(network);
        if (entry == null) return;
        runtime.resetInputs(entry); runtime.queue.add(entry); runtime.drain();
    }
    public static void elementChanged(ServerLevel level, CompiledNetwork network, CompiledCircuitElement element) {
        NetworkIntegrityManager.scheduleCheck(level,element.getPos());
    }
    static void pendingChanged(ServerLevel level, CompiledNetwork network) { integrityChanged(level,network); }
    public static void networkBecameDamaged(ServerLevel level, CompiledNetwork network) { integrityChanged(level, network); }
    public static void networkBecameHealthy(ServerLevel level, CompiledNetwork network) { integrityChanged(level, network); }
    private static void integrityChanged(ServerLevel level, CompiledNetwork network) {
        var runtime = NetworkSavedData.get(level.getServer()).runtime(); var entry = runtime.entries.get(network);
        if (entry == null) return;
        network.runtimeState(false, false);
        entry.outputs = network.getOutputs().iterator(); runtime.resetInputs(entry);
        runtime.queue.add(entry);
        // Integrity callbacks can occur during initial validation. Never recurse into drain.
    }
    public static void tick(MinecraftServer server) {
        var runtime = NetworkSavedData.get(server).runtime();
        // Retain unload tombstones across replace/remove until the chunk is truly gone.
        int cleanup = Math.min(LOAD_RETRIES_PER_TICK, runtime.unloading.size());
        while (cleanup-- > 0) {
            long unit=ServerWorkBudget.begin(server,ServerWorkBudget.Lane.RUNTIME);if(unit==0)break;
            try {
            var iterator = runtime.unloading.iterator(); var key = iterator.next(); iterator.remove();
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(key.dimension)));
            if (level != null && level.getChunkSource().getChunkNow(ChunkPos.getX(key.pos), ChunkPos.getZ(key.pos)) != null)
                runtime.unloading.add(key);
            }finally{ServerWorkBudget.end(server,ServerWorkBudget.Lane.RUNTIME,unit);}
        }
        runtime.drain();
    }
    public static void flush(MinecraftServer server) { NetworkSavedData.get(server).runtime().drain(); }
    private void drain() {
        if (server == null || draining || ServerWorkBudget.inWork(server)) return;
        int tick = server.getTickCount();
        if (budgetTick != tick) { budgetTick = tick; remaining = WORK_PER_TICK; }
        draining = true;
        long start = PerformanceDiagnostics.begin();
        try {
            while (remaining > 0 && (!queue.isEmpty() || !retired.isEmpty())) {
                long unit=ServerWorkBudget.begin(server,ServerWorkBudget.Lane.RUNTIME);if(unit==0)break;
                remaining--;PerformanceDiagnostics.add("runtime.workUnits",1);
                try {
                    retireTurn=!retireTurn;
                    if(!retired.isEmpty() && (retireTurn || queue.isEmpty())) {
                        var old=retired.removeFirst();
                        if(old.step())retired.addLast(old);
                    } else {
                        var iterator=queue.iterator();Entry entry=iterator.next();iterator.remove();
                        if(data.getNetwork(entry.network.getId())!=entry.network || entries.get(entry.network)!=entry)continue;
                        if(entry.levelKey==null)entry.levelKey=ResourceKey.create(Registries.DIMENSION,new ResourceLocation(entry.dimension));
                        ServerLevel level=server.getLevel(entry.levelKey);
                        if(level==null){entry.network.runtimeState(false,false);continue;}
                        step(level,entry);
                        if(entry.waitingChecks>0 || entry.initial.hasNext() || entry.loadedChecks.hasNext() || !entry.partsToCheck.isEmpty() || !entry.checks.isEmpty() || entry.dirtyInputs || entry.outputs.hasNext())queue.add(entry);
                    }
                }finally{ServerWorkBudget.end(server,ServerWorkBudget.Lane.RUNTIME,unit);}
            }
            PerformanceDiagnostics.max("runtime.queuePeak", queue.size() + retired.size());
            PerformanceDiagnostics.gauge("runtime.queueCurrent",queue.size());
            PerformanceDiagnostics.gauge("runtime.retiredWorkCurrent",retiredWork);
            PerformanceDiagnostics.max("runtime.retiredWorkPeak",retiredWork);
            PerformanceDiagnostics.max("runtime.workPerTickPeak", WORK_PER_TICK - remaining);
        } finally { draining = false; PerformanceDiagnostics.elapsed("runtime", start); }
    }
    private void step(ServerLevel level, Entry entry) {
        // Notifications are iterative; reentrant input callbacks only invalidate/enqueue work.
        if (level.captureBlockSnapshots || level.restoringBlockSnapshots) return;
        if (entry.startedAt < 0) entry.startedAt = server.getTickCount();
        if (entry.outputs.hasNext()) { notifyOutput(level, entry.outputs.next()); return; }
        if(entry.waitingChecks>0 && !entry.waiting.isEmpty()) {
            var iterator=entry.waiting.iterator();var key=iterator.next();iterator.remove();entry.waitingChecks--;
            if(isChunkAvailable(level,key.pos)) {
                loading.remove(key);var part=entry.parts.get(key);
                if(part!=null && (part.input || !part.elements.isEmpty()))entry.partsToCheck.add(part);
                resetInputs(entry);
            }else entry.waiting.add(key);
            return;
        }
        if (entry.initial.hasNext()) { check(level, entry, entry.initial.next()); return; }
        if (entry.loadedChecks.hasNext()) { check(level, entry, entry.loadedChecks.next()); return; }
        if (!entry.partsToCheck.isEmpty()) {
            var iterator = entry.partsToCheck.iterator(); var part = iterator.next(); iterator.remove();
            entry.loadedChecks = part.elements.iterator(); return;
        }
        if (!entry.checks.isEmpty()) {
            var iterator = entry.checks.iterator(); var element = iterator.next(); iterator.remove();
            check(level, entry, element); return;
        }
        if (!entry.dirtyInputs) return;
        if (entry.inputs == null) {
            entry.inputs = entry.network.getInputs().iterator(); entry.inputOr = false; entry.available = true;
        }
        if (entry.inputs.hasNext()) {
            BlockPos pos = entry.inputs.next();
            boolean loaded = isChunkAvailable(level, RuntimeSignalReader.chunk(pos));
            entry.available &= loaded;
            if (loaded) {
                RuntimeSignalReader reader = new RuntimeSignalReader(level);
                entry.inputOr |= reader.readInput(pos) > 0;
                entry.available &= reader.available();
                PerformanceDiagnostics.add("runtime.inputReads", 1);
            }
            return;
        }
        entry.dirtyInputs = false;
        int before = entry.network.getEffectiveSignal();
        if (entry.network.isPowered() != entry.inputOr) { entry.network.setPowered(entry.inputOr); data.setDirty(); }
        entry.network.runtimeState(entry.waiting.isEmpty(), entry.available);
        if (before != entry.network.getEffectiveSignal()) entry.outputs = entry.network.getOutputs().iterator();
        if (!entry.firstReady && entry.waiting.isEmpty() && entry.available) {
            entry.firstReady = true;
            PerformanceDiagnostics.max("runtime.initializationTicksPeak", server.getTickCount() - entry.startedAt);
        }
        PerformanceDiagnostics.add("runtime.reconciliations", 1);
    }
    private void check(ServerLevel level, Entry entry, CompiledCircuitElement element) {
        if (isChunkAvailable(level, RuntimeSignalReader.chunk(element.getPos()))) {
            PerformanceDiagnostics.add("runtime.integrityReads", 1);
            NetworkIntegrityManager.checkElement(level, data, entry.network, element);
        }
    }
    public static void notifyOutput(ServerLevel level, BlockPos pos) {
        if (!isChunkAvailable(level, RuntimeSignalReader.chunk(pos))) return;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (isChunkAvailable(level, RuntimeSignalReader.chunk(neighbor)))
                level.neighborChanged(neighbor, ModBlocks.OUTPUT_ENDPOINT.get(), pos);
        }
        PerformanceDiagnostics.add("runtime.outputNotifications", 1);
    }
    public static void stop(MinecraftServer server) {
        var runtime = NetworkSavedData.get(server).runtime();
        for (var network : runtime.entries.keySet()) network.runtimeState(false, false);
        runtime.queue.clear(); runtime.retired.clear(); runtime.retiredWork=0; runtime.loading.clear(); runtime.unloading.clear();
        runtime.entries.clear(); runtime.chunks.clear(); runtime.data.integrity().dispose(); runtime.server = null;
    }
}
