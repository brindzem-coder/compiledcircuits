package com.example.compiledcircuits.client;

import com.example.compiledcircuits.networking.CompiledElementPositionsS2CPacket;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Main-thread logical membership. UNKNOWN is never equivalent to uncompiled. */
public final class ClientCompiledElements {
    private static String dimension = "";
    private static Object levelIdentity;
    private static boolean ready, unknownReservations;
    private static Set<BlockPos> blocked = Set.of();
    private static long revision, latestSnapshot;
    private static Map<BlockPos, Integer> byPos = Map.of();
    private static Map<Integer, Set<BlockPos>> byNetwork = Map.of();
    private static Assembly staging;
    private static Complete complete;
    private static UUID context=CompiledElementPositionsS2CPacket.TEST_CONTEXT;
    private static String status="UNKNOWN",reason="";
    public static String reason(){return reason;}
    public static String status(){return status;}
    public static long completedSnapshot(){return staging==null&&(ready||status.equals("OVER_CAPACITY")||status.equals("UNKNOWN_STATUS"))?latestSnapshot:0;}
    public static void context(UUID token){context=token;}

    private record Complete(String dimension, Map<BlockPos, Integer> positions, Map<Integer, Set<BlockPos>> networks, Set<BlockPos> blocked, boolean unknown) {}
    private static final class Assembly {
        final String dimension,reason;
        final long id, started;
        final List<CompiledElementPositionsS2CPacket.Entry>[] parts;
        final List<BlockPos>[] blockedParts;
        final boolean unknown;
        int count, entries;
        long bytes,lastProgress;
        int partCursor,entryCursor,blockedCursor;
        final Map<BlockPos,Integer> positions=new HashMap<>();
        final Map<Integer,Set<BlockPos>> networks=new HashMap<>();
        final Map<Integer,Set<BlockPos>> views=new HashMap<>();
        final Set<BlockPos> reservations=new HashSet<>();
        @SuppressWarnings("unchecked")
        Assembly(CompiledElementPositionsS2CPacket part, long now) {
            dimension = part.dimension().toString();reason=part.reason(); id = part.snapshotId(); started = now;lastProgress=now;
            parts = new List[part.partCount()]; blockedParts = new List[part.partCount()]; unknown = part.unknownReservations();
        }
    }
    private ClientCompiledElements() {}
    public static boolean isReadyFor(String id) { return ready && dimension.equals(id); }
    public static long getRevision() { return revision; }
    public static boolean isBlocked(BlockPos pos) { return !ready || blocked.contains(pos) || (unknownReservations && !byPos.containsKey(pos)); }
    public static boolean hasBlockedMembership() { return unknownReservations || !blocked.isEmpty(); }
    public static Integer networkIdAt(BlockPos pos) { return ready?byPos.get(pos):null; }
    public static Set<BlockPos> positionsForNetwork(int id) { return ready?byNetwork.getOrDefault(id, Set.of()):Set.of(); }

    public static void onLevelChanged(Object level, String id) {
        if (levelIdentity == level && dimension.equals(id)) return;
        levelIdentity = level; dimension = id;
        ready = false; byPos = Map.of(); byNetwork = Map.of(); blocked = Set.of(); unknownReservations = false; revision++;
        staging=null;complete=null;latestSnapshot=0;status="UNKNOWN";
    }
    public static void clear() {
        levelIdentity = null; dimension = ""; ready = false; byPos = Map.of(); byNetwork = Map.of(); blocked = Set.of(); unknownReservations = false;
        staging = null; complete = null; latestSnapshot = 0; status="UNKNOWN";context=CompiledElementPositionsS2CPacket.TEST_CONTEXT;revision++;
    }
    public static void tick(long now) {
        if (staging != null && (now - staging.lastProgress >= CompiledElementPositionsS2CPacket.STAGING_TIMEOUT_NANOS || now-staging.started>=CompiledElementPositionsS2CPacket.STAGING_LIFETIME_NANOS)) reject("Timed out");
        assemble(4096);
        if(staging!=null&&staging.count==staging.parts.length)staging.lastProgress=now;
    }
    private static void reject(String reason) {
        staging = null;complete=null;ready=false;byPos=Map.of();byNetwork=Map.of();blocked=Set.of();status="STALE";ClientCompiledElements.reason=reason;revision++;
        org.slf4j.LoggerFactory.getLogger(ClientCompiledElements.class).warn("Compiled membership snapshot rejected: {}", reason);
    }
    public static void accept(CompiledElementPositionsS2CPacket part, long now) {
        if(levelIdentity==null||!dimension.equals(part.dimension().toString())||!context.equals(part.context()))return;
        if(staging!=null&&(now-staging.lastProgress>=CompiledElementPositionsS2CPacket.STAGING_TIMEOUT_NANOS||now-staging.started>=CompiledElementPositionsS2CPacket.STAGING_LIFETIME_NANOS))reject("Timed out");
        if (part.snapshotId() < latestSnapshot) return;
        if(part.state()!=CompiledElementPositionsS2CPacket.State.READY){
            if(part.snapshotId()==latestSnapshot){if(staging!=null)reject("Conflicting status within snapshot");return;}
            latestSnapshot=part.snapshotId();staging=null;complete=null;ready=false;byPos=Map.of();byNetwork=Map.of();blocked=Set.of();
            reason=part.reason();status=part.state()==CompiledElementPositionsS2CPacket.State.OVER_CAPACITY?"OVER_CAPACITY":"UNKNOWN_STATUS";revision++;return;
        }
        if (part.snapshotId() > latestSnapshot) {
            latestSnapshot = part.snapshotId(); staging = new Assembly(part, now);status="STAGING";
        }
        if (staging == null) return; // already completed/rejected/expired ID
        if (!staging.dimension.equals(part.dimension().toString()) || staging.parts.length != part.partCount() || staging.unknown != part.unknownReservations() || !staging.reason.equals(part.reason())) {
            reject("Inconsistent parts"); return;
        }
        if (staging.parts[part.partIndex()] != null) {
            if(!staging.parts[part.partIndex()].equals(part.entries())||!staging.blockedParts[part.partIndex()].equals(part.blocked()))reject("Conflicting duplicate part");return;
        }
        staging.entries += part.entries().size() + part.blocked().size(); staging.bytes += part.estimatedBytes();
        if (staging.entries > CompiledElementPositionsS2CPacket.MAX_ENTRIES || staging.bytes > CompiledElementPositionsS2CPacket.MAX_BYTES) {
            reject("Assembly budget exceeded"); return;
        }
        staging.parts[part.partIndex()] = part.entries(); staging.blockedParts[part.partIndex()] = part.blocked(); staging.count++;staging.lastProgress=now;
        // Tiny snapshots finish here; larger snapshots are applied in bounded client ticks.
        if(staging.entries<=1024)assemble(4096);
    }
    private static void assemble(int budget){
        if(staging==null||staging.count!=staging.parts.length)return;
        var a=staging;
        while(budget-->0&&a.partCursor<a.parts.length){
            var entries=a.parts[a.partCursor];var reservations=a.blockedParts[a.partCursor];
            if(a.entryCursor<entries.size()){
                var entry=entries.get(a.entryCursor++);
                if(a.reservations.contains(entry.pos())||a.positions.putIfAbsent(entry.pos(),entry.networkId())!=null){reject("Duplicate membership");return;}
                a.networks.computeIfAbsent(entry.networkId(),k->{var set=new HashSet<BlockPos>();a.views.put(k,Collections.unmodifiableSet(set));return set;}).add(entry.pos());
            }else if(a.blockedCursor<reservations.size()){
                var pos=reservations.get(a.blockedCursor++);
                if(a.positions.containsKey(pos)||!a.reservations.add(pos)){reject("Duplicate reservation");return;}
            }else {a.partCursor++;a.entryCursor=a.blockedCursor=0;}
        }
        if(a.partCursor!=a.parts.length)return;
        complete=new Complete(a.dimension,Collections.unmodifiableMap(a.positions),Collections.unmodifiableMap(a.views),Collections.unmodifiableSet(a.reservations),a.unknown);
        staging=null;commitIfActive();
    }
    private static void commitIfActive() {
        if (levelIdentity == null || complete == null || !complete.dimension.equals(dimension)) return;
        byPos = complete.positions; byNetwork = complete.networks; blocked = complete.blocked; unknownReservations = complete.unknown;
        ready = true;status="READY"; revision++;
    }
}
