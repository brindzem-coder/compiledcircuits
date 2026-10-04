package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import com.example.compiledcircuits.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Shared immutable dimension snapshots, built in the server SYNC lane. */
public final class CompiledElementSync {
    private static final Map<MinecraftServer,Engine> engines=new IdentityHashMap<>();
    private static long snapshotBytes,snapshotBuildNanos;
    public static long getSnapshotBytes(){return snapshotBytes;}
    public static long getSnapshotBuildNanos(){return snapshotBuildNanos;}
    private static Engine engine(MinecraftServer server){var data=NetworkSavedData.get(server);var e=engines.get(server);if(e==null||e.data!=data){e=new Engine(data);engines.put(server,e);}return e;}
    public static void sendSnapshot(ServerPlayer player){forget(player);}
    public static void forget(ServerPlayer player){var engine=engines.get(player.getServer());if(engine!=null)engine.close(player);}
    public static void stop(MinecraftServer server){engines.remove(server);}
    public static void clear(){engines.clear();snapshotBytes=snapshotBuildNanos=0;}
    public static void request(ServerPlayer player,MembershipRequestC2SPacket packet){
        if(player.isRemoved()||!player.serverLevel().dimension().location().toString().equals(packet.dimension()))return;
        var engine=engine(player.getServer());
        if(packet.ack()>0){engine.ack(player,packet.context(),packet.ack());return;}
        if(!NetworkOperations.authorizeDamageResync(player))return;
        engine.open(player,packet.context(),packet.dimension(),player.getServer().getTickCount());
    }

    public static void begin(MinecraftServer server){engine(server).begin(server);}
    public static boolean step(MinecraftServer server){return engine(server).step();}
    public static List<CompiledElementPositionsS2CPacket> buildSnapshot(NetworkSavedData data,String dimension,long id){
        var builder=new Builder(data,dimension,id);while(!builder.done)builder.step();var result=builder.result();snapshotBuildNanos=builder.nanos;snapshotBytes=builder.bytes;return result;
    }
    public static final class Builder {
        final ResourceLocation dimension;final long id;final boolean unknown;
        final Iterator<CompiledNetwork> networks;final Iterator<BlockPos> blocked;
        Iterator<CompiledCircuitElement> elements=Collections.emptyIterator();int networkId;
        final List<CompiledElementPositionsS2CPacket> parts=new ArrayList<>();
        final List<CompiledElementPositionsS2CPacket.Entry> entries=new ArrayList<>();final List<BlockPos> reservations=new ArrayList<>();
        final int partCount;final long retainedBound;long nanos,bytes;boolean done;
        public Builder(NetworkSavedData data,String dimension,long id){
            this.dimension=Objects.requireNonNull(ResourceLocation.tryParse(dimension));this.id=id;
            var usage=data.capacity().usage(dimension);retainedBound=usage.encodedUpperBound();unknown=data.hasUnknownMembershipReservations();
            networks=data.capacity().snapshot(dimension).values().iterator();blocked=data.reservedPositionsView(dimension).iterator();
            // Raw records may conservatively overcount; wire parts describe the actual unique reservation set.
            long actual=data.capacity().activeCount(dimension)+data.reservedPositionsView(dimension).size();
            partCount=(int)MembershipCapacity.parts(actual);
            if(usage.state()!=MembershipCapacity.State.READY){
                parts.add(new CompiledElementPositionsS2CPacket(this.dimension,id,0,1,List.of(),List.of(),true,
                    CompiledElementPositionsS2CPacket.TEST_CONTEXT,usage.state()==MembershipCapacity.State.OVER_CAPACITY?CompiledElementPositionsS2CPacket.State.OVER_CAPACITY:CompiledElementPositionsS2CPacket.State.UNKNOWN,"records="+usage.records()+", limit="+usage.limit()+"; "+usage.reason()));bytes=parts.get(0).encodedBytes();done=true;
            }
        }
        public void step(){
            if(done)return;long start=System.nanoTime();
            try{
                if(elements.hasNext()){var e=elements.next();entries.add(new CompiledElementPositionsS2CPacket.Entry(networkId,e.getPos()));}
                else if(networks.hasNext()){var n=networks.next();networkId=n.getId();elements=n.getElements().iterator();return;}
                else if(blocked.hasNext())reservations.add(blocked.next());
                else {if(!entries.isEmpty()||!reservations.isEmpty()||parts.isEmpty())flush();done=true;return;}
                if(entries.size()+reservations.size()==CompiledElementPositionsS2CPacket.ENTRIES_PER_PART)flush();
            }finally{nanos+=System.nanoTime()-start;}
        }
        private void flush(){var part=new CompiledElementPositionsS2CPacket(dimension,id,parts.size(),partCount,entries,reservations,unknown);parts.add(part);bytes+=part.encodedBytes();entries.clear();reservations.clear();}
        public List<CompiledElementPositionsS2CPacket> result(){
            if(!done)throw new IllegalStateException("Incomplete membership snapshot");return List.copyOf(parts);
        }
        public boolean done(){return done;}
    }
    private static final class Peer {
        final UUID context;final String dimension;final long requested;long ack,lastSent;int next,sent;
        List<CompiledElementPositionsS2CPacket> batch;
        Peer(UUID context,String dimension,long requested){this.context=context;this.dimension=dimension;this.requested=requested;}
    }
    private static final class Dimension {
        long generation,published,failedGeneration=-1;long captured=-1;Builder builder;List<CompiledElementPositionsS2CPacket> cache;
    }
    public static final class Engine {
        final Map<Object,Peer> rejected=new LinkedHashMap<>();
        final NetworkSavedData data;final Map<Object,Peer> peers=new LinkedHashMap<>();final Map<String,Dimension> dimensions=new LinkedHashMap<>();
        long serial,now;int sent,buildCursor,deliveryCursor;boolean buildTurn;
        final java.util.function.BiConsumer<Object,CompiledElementPositionsS2CPacket> send;
        Engine(NetworkSavedData data){this(data,(key,packet)->ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(()->(ServerPlayer)key),packet));}
        public Engine(NetworkSavedData data,java.util.function.BiConsumer<Object,CompiledElementPositionsS2CPacket> send){this.data=data;this.send=send;}
        public boolean open(Object key,UUID context,String dim,long tick){
            var peer=peers.get(key);if(peer!=null&&tick-peer.requested<40&&peer.context.equals(context))return false;
            if(peer==null&&peers.size()>=64){if(rejected.size()<64)rejected.put(key,new Peer(context,dim,tick));return false;}
            if(peer!=null&&peer.context.equals(context)){var d=dimensions.get(dim);if(d!=null&&d.cache!=null){d.cache=null;d.captured=-1;}}
            rejected.remove(key);peers.put(key,new Peer(context,dim,tick));return true;
        }
        public void ack(Object key,UUID context,long batch){var p=peers.get(key);if(p!=null&&p.context.equals(context)&&p.batch!=null&&p.batch.get(0).snapshotId()==batch&&p.next==p.batch.size()){p.ack=batch;p.batch=null;p.next=0;}}
        public void close(Object key){peers.remove(key);rejected.remove(key);}
        public int clients(){return peers.size();}
        void begin(MinecraftServer server){
            peers.entrySet().removeIf(e->!(e.getKey() instanceof ServerPlayer p)||p.isRemoved()||server.getPlayerList().getPlayer(p.getUUID())!=p
                ||!e.getValue().dimension.equals(p.serverLevel().dimension().location().toString()));
            rejected.entrySet().removeIf(e->!(e.getKey() instanceof ServerPlayer p)||p.isRemoved()||server.getPlayerList().getPlayer(p.getUUID())!=p
                ||!e.getValue().dimension.equals(p.serverLevel().dimension().location().toString()));
            begin(server.getTickCount());
        }
        public void begin(long tick){now=tick;sent=0;
            var expired=peers.entrySet().iterator();while(expired.hasNext()){var e=expired.next();var p=e.getValue();if(p.batch!=null&&p.next==p.batch.size()&&now-p.lastSent>600){if(rejected.size()<64)rejected.put(e.getKey(),p);expired.remove();}}
            Set<String> active=new HashSet<>();for(var p:peers.values()){active.add(p.dimension);p.sent=0;}
            dimensions.keySet().retainAll(active);for(var dim:active){
                var d=dimensions.computeIfAbsent(dim,k->new Dimension());long revision=data.capacity().revision(dim);
                if(d.generation!=revision&&data.capacity().usage(dim).state()!=MembershipCapacity.State.READY){
                    d.builder=null;d.cache=null;for(var p:peers.values())if(p.dimension.equals(dim)){p.batch=null;p.next=0;}
                }
                d.generation=revision;
                if(d.cache!=null){boolean needed=false;for(var p:peers.values())if(p.dimension.equals(dim)&&(p.batch==d.cache||p.ack<d.cache.get(0).snapshotId())){needed=true;break;}if(!needed)d.cache=null;}
            }
        }
        public long retained(){
            Set<List<CompiledElementPositionsS2CPacket>> batches=Collections.newSetFromMap(new IdentityHashMap<>());
            for(var d:dimensions.values())if(d.cache!=null)batches.add(d.cache);
            for(var p:peers.values())if(p.batch!=null)batches.add(p.batch);
            long bytes=0;for(var batch:batches)for(var part:batch)bytes+=part.estimatedBytes();
            for(var d:dimensions.values())if(d.builder!=null)bytes+=d.builder.retainedBound;return bytes;
        }
        public boolean step(){buildTurn=!buildTurn;return buildTurn?build():deliver();}
        boolean build(){
            if(dimensions.isEmpty())return false;var list=new ArrayList<>(dimensions.entrySet());
            for(int i=0;i<list.size();i++){
                var e=list.get(Math.floorMod(buildCursor++,list.size()));var d=e.getValue();
                try {
                boolean requested=d.published==0;for(var p:peers.values())if(p.dimension.equals(e.getKey())&&p.batch==null&&p.ack<d.published){requested=true;break;}
                if(d.builder==null&&(d.captured!=d.generation||d.cache==null&&requested)){
                    var usage=data.capacity().usage(e.getKey());
                    if(usage.state()!=MembershipCapacity.State.READY){
                        d.builder=null;for(var p:peers.values())if(p.dimension.equals(e.getKey())){p.batch=null;p.next=0;}
                    }
                    d.cache=null;
                    if(usage.state()==MembershipCapacity.State.READY&&retained()+usage.encodedUpperBound()>CompiledElementPositionsS2CPacket.MAX_BYTES)continue;
                    d.builder=new Builder(data,e.getKey(),++serial);d.captured=d.generation;
                }
                if(d.builder==null)continue;
                // A newly exceeded limit cancels an older captured healthy snapshot immediately.
                if(d.captured!=d.generation&&data.capacity().usage(e.getKey()).state()!=MembershipCapacity.State.READY){d.builder=null;return true;}
                d.builder.step();if(d.builder.done){
                    d.cache=d.builder.result();d.published=d.cache.get(0).snapshotId();snapshotBuildNanos=d.builder.nanos;snapshotBytes=d.builder.bytes;d.builder=null;
                    PerformanceDiagnostics.add("snapshot.builds",1);PerformanceDiagnostics.add("snapshot.buildNanos",snapshotBuildNanos);PerformanceDiagnostics.add("snapshot.payloadBytesBuilt",snapshotBytes);PerformanceDiagnostics.add("snapshot.partsBuilt",d.cache.size());
                }return true;
                }catch(IllegalArgumentException|IllegalStateException failure){
                    d.builder=null;d.captured=d.generation;
                    d.cache=List.of(new CompiledElementPositionsS2CPacket(Objects.requireNonNull(ResourceLocation.tryParse(e.getKey())),++serial,0,1,List.of(),List.of(),true,CompiledElementPositionsS2CPacket.TEST_CONTEXT,CompiledElementPositionsS2CPacket.State.UNKNOWN,"Membership snapshot build failed; request a new snapshot."));
                    d.published=d.cache.get(0).snapshotId();
                    for(var p:peers.values())if(p.dimension.equals(e.getKey())){p.batch=null;p.next=0;}
                    if(d.failedGeneration!=d.generation){d.failedGeneration=d.generation;org.slf4j.LoggerFactory.getLogger(CompiledElementSync.class).warn("Membership build failed in {}: {}",e.getKey(),failure.getMessage());}return true;
                }
            }return false;
        }
        boolean deliver(){
            if(sent>=8)return false;
            if(!rejected.isEmpty()&&(sent%2==0||peers.isEmpty())){
                var iterator=rejected.entrySet().iterator();var e=iterator.next();iterator.remove();var p=e.getValue();
                send.accept(e.getKey(),new CompiledElementPositionsS2CPacket(Objects.requireNonNull(ResourceLocation.tryParse(p.dimension)),++serial,0,1,List.of(),List.of(),true,p.context,CompiledElementPositionsS2CPacket.State.UNKNOWN,"Membership client capacity busy; retry shortly."));sent++;return true;
            }
            if(peers.isEmpty())return false;var list=new ArrayList<>(peers.entrySet());
            for(int i=0;i<list.size();i++){
                var e=list.get(Math.floorMod(deliveryCursor++,list.size()));var p=e.getValue();if(p.sent>=2)continue;
                if(p.batch==null){var d=dimensions.get(p.dimension);if(d==null||d.cache==null||d.cache.get(0).snapshotId()<=p.ack)continue;p.batch=d.cache;p.next=0;}
                if(p.next>=p.batch.size())continue;
                var packet=p.batch.get(p.next++).withContext(p.context);p.sent++;sent++;p.lastSent=now;
                send.accept(e.getKey(),packet);
                PerformanceDiagnostics.max("membership.packetsPerTickPeak",sent);PerformanceDiagnostics.max("membership.playerPacketsPerTickPeak",p.sent);return true;
            }return false;
        }
    }
    private CompiledElementSync(){}
}
