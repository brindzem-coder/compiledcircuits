package com.example.compiledcircuits.network;

import com.example.compiledcircuits.config.ServerConfig;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import static com.example.compiledcircuits.network.ServerWorkBudget.Lane;

/** One bounded point queue; overflow is a generation on the saved network, never another position list. */
final class IntegrityScheduler {
    private static final class Dimension { long generation; }
    private static final class State {
        final CompiledNetwork network;
        final Dimension dimension;
        Iterator<CompiledCircuitElement> audit, pass;
        int points, checking, auditCompletedTick = Integer.MIN_VALUE;
        long version, passVersion, passDimension, validatedDimension, pendingSince;
        boolean full, parked, unavailable;
        State(CompiledNetwork n, Dimension d) { network=n; dimension=d; validatedDimension=d.generation; }
        boolean pending() { return points>0 || checking>0 || full || validatedDimension!=dimension.generation; }
    }
    private record Key(String dimension, BlockPos pos) {}
    private record Point(State owner, CompiledCircuitElement element, int enqueuedTick) {}
    private final NetworkSavedData data;
    private final NavigableMap<Integer, State> states = new TreeMap<>();
    private final Map<String, Dimension> dimensions = new HashMap<>();
    private final LinkedHashMap<Key, Point> points = new LinkedHashMap<>();
    private final LinkedHashSet<State> rechecks = new LinkedHashSet<>();
    private int auditCursor, laneCursor, parked;
    IntegrityScheduler(NetworkSavedData data) { this.data=data; }
    void add(CompiledNetwork network) {
        State s=new State(network,dimensions.computeIfAbsent(network.getDimension(),k->new Dimension()));
        states.put(network.getId(),s); network.integrityGuard(s::pending);
        if(network.savedIntegrityPending())full(s);
    }
    void remove(CompiledNetwork network) {
        State s=states.get(network.getId());
        if(s!=null && s.network==network){states.remove(network.getId());rechecks.remove(s);if(s.parked)parked--;}
        network.integrityGuard(()->false);
    }
    private ServerLevel level(MinecraftServer server, State s) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(s.network.getDimension())));
    }
    private boolean current(State s) { return states.get(s.network.getId())==s && data.getNetwork(s.network.getId())==s.network; }
    private void changedGate(ServerLevel level, State s, boolean wasPending) {
        if(wasPending!=s.pending()) { data.setDirty(); NetworkRuntime.pendingChanged(level,s.network); }
    }
    private void full(State s) {
        if(!s.full)s.pendingSince=System.nanoTime();
        if(s.parked)parked--;
        s.version++;s.full=true;s.parked=false;rechecks.add(s);
        PerformanceDiagnostics.add("integrity.fullRequests",1);
    }
    void schedule(ServerLevel level, NetworkSavedData.ElementLocation location) {
        State s=states.get(location.network().getId());
        if(s==null || s.network!=location.network())return;
        boolean was=s.pending();
        if(s.full || s.validatedDimension!=s.dimension.generation){
            if(!s.full)NetworkRuntime.pendingChanged(level,s.network);
            full(s);changedGate(level,s,was);return;
        }
        Key key=new Key(s.network.getDimension(),location.element().getPos());
        var previous=points.get(key);
        if(previous!=null && previous.owner==s) {PerformanceDiagnostics.add("pending.deduplicated",1);return;}
        if(points.size()>=ServerConfig.MAX_PENDING.get()) {
            full(s); PerformanceDiagnostics.add("pending.overflow",1);changedGate(level,s,was);return;
        }
        if(previous!=null)previous.owner.points--;
        points.put(key,new Point(s,location.element(),level.getServer().getTickCount()));s.points++;
        PerformanceDiagnostics.add("pending.uniqueEnqueued",1);PerformanceDiagnostics.max("pending.queuePeak",points.size());
        changedGate(level,s,was);
    }
    void invalidateDimension(ServerLevel level) {
        dimensions.computeIfAbsent(level.dimension().location().toString(),k->new Dimension()).generation++;
        data.setDirty();
        PerformanceDiagnostics.add("integrity.dimensionInvalidations",1);
        // Guards consult this generation immediately. Audit discovers/queues each full pass incrementally.
    }
    void loaded(ServerLevel level, CompiledNetwork network) {
        State s=states.get(network.getId());
        if(s!=null && s.network==network && s.full){full(s);}
    }
    private void point(MinecraftServer server) {
        var iterator=points.entrySet().iterator();var item=iterator.next();iterator.remove();
        Point p=item.getValue();State s=p.owner;s.points--;
        PerformanceDiagnostics.max("pending.oldestAgeTicks",server.getTickCount()-p.enqueuedTick);
        if(!current(s))return;
        s.checking++;
        var level=level(server,s);
        try {
            var actual=data.findIndexedElementLocation(item.getKey().dimension,item.getKey().pos);
            if(actual==null || actual.network()!=s.network || actual.element()!=p.element)return;
            if(level==null || !NetworkRuntime.isChunkAvailable(level,RuntimeSignalReader.chunk(p.element.getPos()))) {
                full(s);s.parked=true;parked++;rechecks.remove(s);PerformanceDiagnostics.add("pending.unavailable",1);return;
            }
            // Remove BEFORE checking: callbacks enqueue a fresh revision; the in-flight gate remains LOW.
            if(!NetworkIntegrityManager.checkElement(level,data,s.network,p.element))full(s);
            PerformanceDiagnostics.add("processPending.positions",1);
        } finally { s.checking--; if(level!=null)changedGate(level,s,true); }
    }
    private void recheck(MinecraftServer server) {
        var iterator=rechecks.iterator();State s=iterator.next();iterator.remove();if(!current(s))return;
        PerformanceDiagnostics.max("integrity.fullAgeMicros",(System.nanoTime()-s.pendingSince)/1000);
        var level=level(server,s);
        if(level==null){s.parked=true;parked++;return;}
        if(s.pass==null){s.pass=s.network.getElements().iterator();s.passVersion=s.version;s.passDimension=s.dimension.generation;s.unavailable=false;}
        if(s.pass.hasNext()) {
            var e=s.pass.next();
            if(!NetworkRuntime.isChunkAvailable(level,RuntimeSignalReader.chunk(e.getPos())))s.unavailable=true;
            else if(!NetworkIntegrityManager.checkElement(level,data,s.network,e))s.version++;
            rechecks.add(s);PerformanceDiagnostics.add("integrity.recheckElements",1);return;
        }
        s.pass=null;
        if(s.passVersion!=s.version || s.passDimension!=s.dimension.generation){rechecks.add(s);return;}
        if(s.unavailable){s.parked=true;parked++;PerformanceDiagnostics.add("integrity.parkedPasses",1);return;}
        s.full=false;s.parked=false;s.validatedDimension=s.passDimension;s.network.integrityConfirmed();
        changedGate(level,s,true);PerformanceDiagnostics.add("integrity.completedPasses",1);
    }
    private void auditOne(MinecraftServer server) {
        if(states.isEmpty())return;
        var next=states.higherEntry(auditCursor);if(next==null)next=states.firstEntry();auditCursor=next.getKey();State s=next.getValue();
        var level=level(server,s);if(level==null)return;
        if(s.validatedDimension!=s.dimension.generation && !s.full){full(s);NetworkRuntime.pendingChanged(level,s.network);}
        if(s.parked || s.auditCompletedTick==server.getTickCount())return;
        if(s.audit==null)s.audit=s.network.getElements().iterator();
        if(s.audit.hasNext()) {
            var e=s.audit.next();PerformanceDiagnostics.add("audit.elements",1);
            if(NetworkRuntime.isChunkAvailable(level,RuntimeSignalReader.chunk(e.getPos())))NetworkIntegrityManager.checkElement(level,data,s.network,e);
        }
        if(!s.audit.hasNext()){s.audit=null;s.auditCompletedTick=server.getTickCount();}
    }
    void audit(MinecraftServer server) {
        // Tick START reservation: urgent work cannot consume the whole allowance first.
        int count=Math.min(16,ServerConfig.AUDIT_WORK.get());
        while(count-->0 && !states.isEmpty()) {
            long start=ServerWorkBudget.begin(server,Lane.AUDIT);if(start==0)break;
            try{auditOne(server);}finally{ServerWorkBudget.end(server,Lane.AUDIT,start);}
        }
    }
    void tick(MinecraftServer server) {
        int idle=0;
        while(idle<4) {
            Lane lane=switch(laneCursor++ & 3){case 0->Lane.POINT;case 1->Lane.RECHECK;case 2->Lane.REPAIR;default->Lane.AUDIT;};
            boolean ready=switch(lane){case POINT->!points.isEmpty();case RECHECK->!rechecks.isEmpty();case REPAIR->RepairJobs.hasWork(server);default->!states.isEmpty();};
            if(!ready || !ServerWorkBudget.available(server,lane)){idle++;continue;}
            long start=ServerWorkBudget.begin(server,lane);if(start==0){idle++;continue;}idle=0;
            try{switch(lane){case POINT->point(server);case RECHECK->recheck(server);case REPAIR->RepairJobs.step(server);default->auditOne(server);}}
            finally{ServerWorkBudget.end(server,lane,start);}
        }
        PerformanceDiagnostics.max("integrity.recheckQueuePeak",rechecks.size());
        PerformanceDiagnostics.gauge("pending.queueCurrent",points.size());
        PerformanceDiagnostics.gauge("integrity.recheckQueueCurrent",rechecks.size());
        PerformanceDiagnostics.gauge("integrity.parkedCurrent",parked);
        PerformanceDiagnostics.max("integrity.parkedPeak",parked);
        if(!points.isEmpty())PerformanceDiagnostics.max("pending.oldestAgeTicks",server.getTickCount()-points.values().iterator().next().enqueuedTick);
        RepairJobs.recordQueue(server);
    }
    void clear() {
        points.clear();rechecks.clear();auditCursor=0;parked=0;
        for(var s:states.values()) {
            s.points=s.checking=0;s.full=s.parked=false;s.pass=s.audit=null;s.validatedDimension=s.dimension.generation;
            if(s.network.savedIntegrityPending())full(s);
        }
    }
    void dispose() {
        for(var s:states.values())s.network.integrityGuard(()->false);
        states.clear();points.clear();rechecks.clear();dimensions.clear();parked=0;
    }
}
