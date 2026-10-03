package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import java.util.function.BiConsumer;
import static com.example.compiledcircuits.networking.DamageProtocol.*;

/** One GLOBAL revision stream per server, bounded shared encoding and fair acknowledged delivery. */
public final class DamageSync {
    private record Manager(NetworkSavedData data,Engine engine) {}
    private static final Map<MinecraftServer,Manager> managers=new IdentityHashMap<>();
    private static Manager manager(MinecraftServer server){
        var data=NetworkSavedData.get(server);var m=managers.get(server);
        if(m==null||m.data!=data){m=new Manager(data,new Engine(data.damage(),(peer,packet)->
                ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(()->(ServerPlayer)peer),packet)));managers.put(server,m);}
        return m;
    }
    public static void ensure(ServerPlayer player){manager(player.getServer());}
    public static void request(ServerPlayer p,UUID context,String dimension){
        if(!p.serverLevel().dimension().location().toString().equals(dimension) || p.isRemoved()
                || !NetworkOperations.authorizeDamageResync(p))return;
        manager(p.getServer()).engine.open(p,context,p.getServer().getTickCount());
    }
    public static void ack(ServerPlayer p,DamageAckC2SPacket ack){var m=managers.get(p.getServer());if(m!=null)m.engine.ack(p,ack.context(),ack.batch(),ack.revision());}
    public static void forget(ServerPlayer p){var m=managers.get(p.getServer());if(m!=null)m.engine.close(p);}
    public static void stop(MinecraftServer server){managers.remove(server);}
    public static void flush(MinecraftServer server){
        var m=manager(server);
        long preparation=ServerWorkBudget.begin(server,ServerWorkBudget.Lane.SYNC);if(preparation==0)return;
        try{
            m.engine.removeIf(peer->((ServerPlayer)peer).isRemoved()
                    || server.getPlayerList().getPlayer(((ServerPlayer)peer).getUUID())!=peer);
            m.engine.begin(server.getTickCount());HighlightSync.begin(server);
        }finally{ServerWorkBudget.end(server,ServerWorkBudget.Lane.SYNC,preparation);}
        int idle=0;boolean highlightTurn=false;
        while(idle<6){
            long start=ServerWorkBudget.begin(server,ServerWorkBudget.Lane.SYNC);if(start==0)break;
            try{
                highlightTurn=!highlightTurn;
                boolean worked=highlightTurn?HighlightSync.step(server):m.engine.step();
                if(!worked)worked=highlightTurn?m.engine.step():HighlightSync.step(server);
                if(worked)idle=0;else idle++;
            }finally{ServerWorkBudget.end(server,ServerWorkBudget.Lane.SYNC,start);}
        }
        m.engine.metrics();
    }

    /** Deterministic engine also exercised with independent client replicas in server tests. */
    public static final class Engine {
        private static final class Peer {
            final Object key;UUID context;long revision=-1,lastSnapshot,lastRequest,lastSent;
            DamageBatch delivery;int next,sent;boolean snapshot=true;
            Peer(Object key,UUID context,long now){this.key=key;this.context=context;lastRequest=now;}
        }
        private final DamageLedger ledger;private final BiConsumer<Object,DamagePartS2CPacket> send;
        private final UUID epoch=UUID.randomUUID();private final Map<Object,Peer> peers=new IdentityHashMap<>();
        private final ArrayDeque<Peer> order=new ArrayDeque<>();
        private final LinkedHashMap<Object,UUID> rejected=new LinkedHashMap<>();
        private final ArrayDeque<DamageBatch.Builder> deltas=new ArrayDeque<>();
        private final NavigableMap<Long,DamageBatch> journal=new TreeMap<>();
        private DamageBatch.Builder snapshotBuilder;private DamageBatch snapshot;
        private long serial,now,lastBegin=Long.MIN_VALUE,capacityRevision=-1;
        private int journalBytes,sent,cursor;private boolean rejectTurn,folderTurn;
        public Engine(DamageLedger ledger,BiConsumer<Object,DamagePartS2CPacket> send){this.ledger=ledger;this.send=send;}
        public boolean open(Object key,UUID context,long now){
            var p=peers.get(key);
            if(p!=null && now-p.lastRequest<RESYNC_TICKS)return false;
            if(p==null){
                if(peers.size()>=MAX_CLIENTS){if(rejected.size()<MAX_CLIENTS)rejected.put(key,context);return false;}
                p=new Peer(key,context,now);peers.put(key,p);order.addLast(p);
            }else{p.context=context;p.lastRequest=now;p.delivery=null;p.next=0;p.snapshot=true;p.lastSnapshot=0;}
            p.context=context;p.revision=-1;
            if(capacityRevision==ledger.revision()&&rejected.size()<MAX_CLIENTS)rejected.put(key,context);
            return true;
        }
        public void close(Object key){var p=peers.remove(key);if(p!=null)order.remove(p);rejected.remove(key);}
        void removeIf(java.util.function.Predicate<Object> predicate){
            var iterator=order.iterator();while(iterator.hasNext()){var p=iterator.next();if(predicate.test(p.key)){iterator.remove();peers.remove(p.key);}}
            rejected.keySet().removeIf(predicate);
        }
        public void ack(Object key,UUID context,long batch,long revision){
            var p=peers.get(key);if(p==null||!p.context.equals(context)||p.delivery==null)return;
            if(p.delivery.id!=batch||p.delivery.revision!=revision||p.next!=p.delivery.partCount())return;
            p.revision=revision;if(p.delivery.kind==Kind.SNAPSHOT)p.lastSnapshot=batch;
            p.delivery=null;p.next=0;p.snapshot=false;
        }
        public void begin(long now){
            if(lastBegin==now)return;lastBegin=this.now=now;sent=0;
            var publication=ledger.publish();
            if(publication!=null){
                if(publication.overflow()||deltas.size()>=MAX_PENDING_BATCHES){
                    deltas.clear();journal.clear();journalBytes=0;PerformanceDiagnostics.add("damage.backlogOverflows",1);
                }else if(!peers.isEmpty())deltas.addLast(new DamageBatch.Builder(publication.cursor(),Kind.DELTA,publication.base(),publication.revision(),++serial));
            }
            for(var p:order){
                p.sent=0;
                if(p.delivery!=null&&p.next==p.delivery.partCount()&&now-p.lastSent>ASSEMBLY_TIMEOUT){p.delivery=null;p.snapshot=true;p.next=0;PerformanceDiagnostics.add("damage.ackTimeouts",1);}
            }
            if(snapshot!=null){
                boolean used=false;
                for(var p:order)if(p.delivery==snapshot || p.delivery==null&&p.snapshot&&p.lastSnapshot!=snapshot.id&&p.revision<=snapshot.revision){used=true;break;}
                if(!used)snapshot=null;
            }
            boolean need=false;
            for(var p:order)if(p.delivery==null){
                if(!p.snapshot&&p.revision<ledger.revision()&&!journal.containsKey(p.revision)&&deltas.isEmpty())p.snapshot=true;
                if(p.snapshot)need=true;
            }
            if(need&&snapshot==null&&snapshotBuilder==null&&capacityRevision!=ledger.revision()) {
                rejected.entrySet().removeIf(e->peers.containsKey(e.getKey()));
                // These queued revisions are included in this captured full state. Existing deliveries
                // remain valid; any receiver without a contiguous journal will join the full snapshot.
                deltas.clear();
                snapshotBuilder=new DamageBatch.Builder(ledger.snapshot(),Kind.SNAPSHOT,-1,ledger.revision(),++serial);
            }
        }
        public boolean step(){
            int lane=cursor;cursor=(cursor+1)%3;
            return switch(lane){case 0->buildSnapshot();case 1->buildDelta();default->deliver();};
        }
        private boolean buildSnapshot(){
            folderTurn=!folderTurn;
            if(ledger.hasFolderWork()&&(folderTurn||snapshotBuilder==null)){ledger.folderStep();return true;}
            if(snapshotBuilder==null || retainedEncodedBytes()+PART_BYTES>ENCODED_BYTES)return false;
            long start=PerformanceDiagnostics.begin();
            try{
                snapshotBuilder.step();if(snapshotBuilder.done()){snapshot=snapshotBuilder.result();snapshotBuilder=null;record(snapshot);}
            }catch(IllegalArgumentException capacity){
                capacityRevision=snapshotBuilder.revision();snapshotBuilder=null;PerformanceDiagnostics.add("damage.overCapacity",1);
                for(var p:order)if(p.snapshot&&rejected.size()<MAX_CLIENTS)rejected.put(p.key,p.context);
            }finally{PerformanceDiagnostics.elapsed("damage.build",start);}return true;
        }
        private boolean buildDelta(){
            // Finish the captured full snapshot first; never fill memory with two competing builders.
            if(snapshotBuilder!=null || deltas.isEmpty() || retainedEncodedBytes()+PART_BYTES>ENCODED_BYTES)return false;var builder=deltas.peekFirst();long start=PerformanceDiagnostics.begin();
            try{
                builder.step();if(builder.done()){
                    var batch=builder.result();deltas.removeFirst();journal.put(batch.base,batch);journalBytes+=batch.bytes;record(batch);
                    while(journal.size()>MAX_PENDING_BATCHES||journalBytes>JOURNAL_BYTES){var removed=journal.pollFirstEntry();journalBytes-=removed.getValue().bytes;}
                }
            }catch(IllegalArgumentException capacity){deltas.clear();journal.clear();journalBytes=0;PerformanceDiagnostics.add("damage.backlogOverflows",1);}
            finally{PerformanceDiagnostics.elapsed("damage.build",start);}return true;
        }
        private void record(DamageBatch b){PerformanceDiagnostics.add(b.kind==Kind.SNAPSHOT?"damage.snapshots":"damage.deltas",1);PerformanceDiagnostics.add("damage.encodedBytesBuilt",b.bytes);}
        private boolean deliver(){
            if(sent>=PARTS_PER_TICK)return false;
            rejectTurn=!rejectTurn;
            if(!rejected.isEmpty()&&(rejectTurn||order.isEmpty())){
                var it=rejected.entrySet().iterator();var e=it.next();it.remove();
                send.accept(e.getKey(),new DamagePartS2CPacket(e.getValue(),epoch,Kind.OVER_CAPACITY,++serial,-1,ledger.revision(),0,1,0,0,new byte[0]));sent++;return true;
            }
            int remaining=order.size();
            while(remaining-->0){
                var p=order.removeFirst();order.addLast(p);if(p.sent>=PARTS_PER_PLAYER)continue;
                if(p.delivery==null){
                    if(p.snapshot){if(snapshot==null||p.lastSnapshot==snapshot.id||p.revision>snapshot.revision)continue;p.delivery=snapshot;}
                    else {p.delivery=journal.get(p.revision);if(p.delivery==null)continue;}
                    p.next=0;
                }
                if(p.next>=p.delivery.partCount())continue;
                var packet=p.delivery.packet(p.context,epoch,p.next++);p.sent++;sent++;p.lastSent=now;
                send.accept(p.key,packet);PerformanceDiagnostics.add("damage.physicalPackets",1);PerformanceDiagnostics.add("damage.payloadBytesSent",packet.payload().length);
                PerformanceDiagnostics.max("damage.packetsPerTickPeak",sent);PerformanceDiagnostics.max("damage.playerPacketsPerTickPeak",p.sent);return true;
            }
            return false;
        }
        public void metrics(){
            PerformanceDiagnostics.gauge("damage.clients",peers.size());PerformanceDiagnostics.gauge("damage.pendingKeys",ledger.pendingCount());
            PerformanceDiagnostics.gauge("damage.backlogBatches",deltas.size()+journal.size());PerformanceDiagnostics.gauge("damage.journalBytes",journalBytes);
            PerformanceDiagnostics.max("damage.backlogBatchesPeak",deltas.size()+journal.size());
        }
        public int retainedJournalBytes(){return journalBytes;}
        /** Count shared immutable batches once, including batches held by slow receivers. */
        public long retainedEncodedBytes(){
            Set<DamageBatch> retained=Collections.newSetFromMap(new IdentityHashMap<>());
            retained.addAll(journal.values());if(snapshot!=null)retained.add(snapshot);
            for(var p:order)if(p.delivery!=null)retained.add(p.delivery);
            long bytes=0;for(var batch:retained)bytes+=batch.bytes;
            if(snapshotBuilder!=null)bytes+=snapshotBuilder.retainedBytes();
            for(var builder:deltas)bytes+=builder.retainedBytes();
            return bytes;
        }
        public int clients(){return peers.size();}
    }
    private DamageSync() {}
}
