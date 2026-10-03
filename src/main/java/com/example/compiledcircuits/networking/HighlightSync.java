package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.function.*;
import static com.example.compiledcircuits.networking.HighlightProtocol.*;

/** Per-player replaceable jobs, advanced fairly inside the shared stage-03 SYNC allowance. */
public final class HighlightSync {
    private record Manager(NetworkSavedData data, Engine engine) {}
    private static final Map<MinecraftServer,Manager> managers=new IdentityHashMap<>();
    private static Manager manager(MinecraftServer server){
        var data=NetworkSavedData.get(server);var m=managers.get(server);
        if(m==null||m.data!=data){
            if(m!=null)m.engine.stop();
            m=new Manager(data,new Engine(data,(key,p)->ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(()->(ServerPlayer)key),p),
                    key->{var p=(ServerPlayer)key;return !p.isRemoved()&&server.getPlayerList().getPlayer(p.getUUID())==p;}));
            managers.put(server,m);
        }
        return m;
    }
    public static void request(ServerPlayer player,Request request,Collection<Integer> ids){
        if(player==null||player.isRemoved()||!player.getServer().isSameThread()||!request.current())return;
        var m=manager(player.getServer());
        if(!request.dimension().equals(player.serverLevel().dimension().location().toString())) {
            m.engine.submit(player,request,List.of(),"Player world changed; select again.",State.ERROR,player.serverLevel(),player.getServer().getTickCount());return;
        }
        if(ids.isEmpty()) { // Cancellation is constant work and must not be blocked by a spent action budget.
            m.engine.submit(player,request,List.of(),"",State.READY,player.serverLevel(),player.getServer().getTickCount());return;
        }
        var result=NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.HIGHLIGHT,ids,0,"");
        m.engine.submit(player,request,result.success()?result.networks():List.of(),result.message(),
                result.success()?State.READY:State.ERROR,player.serverLevel(),player.getServer().getTickCount());
    }
    public static void begin(MinecraftServer server){var m=managers.get(server);if(m!=null)m.engine.begin(server.getTickCount());}
    public static boolean step(MinecraftServer server){var m=managers.get(server);return m!=null&&m.engine.step();}
    public static void forget(ServerPlayer p){var m=managers.get(p.getServer());if(m!=null)m.engine.close(p);}
    public static void stop(MinecraftServer server){var m=managers.remove(server);if(m!=null)m.engine.stop();}

    public static final class Engine {
        private static final class Peer {
            final Object key;Request request;Object world;long batch,revision,started;State state;String message;
            Map<Integer,CompiledNetwork> selected=Map.of();Iterator<CompiledNetwork> networks=Collections.emptyIterator();
            Iterator<CompiledCircuitElement> elements=Collections.emptyIterator();
            final List<byte[]> parts=new ArrayList<>();ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            int total,next,sent;boolean built,delivered,sendTurn;
            Peer(Object key){this.key=key;}
        }
        private final NetworkSavedData data;private final BiConsumer<Object,NetworkHighlightS2CPacket> sender;private final Predicate<Object> connected;
        private final Map<Object,Peer> peers=new IdentityHashMap<>();private final ArrayDeque<Peer> order=new ArrayDeque<>();
        private final Map<Object,NetworkHighlightS2CPacket> overflow=new IdentityHashMap<>();
        private long serial,now;private int sent;private boolean overflowTurn;
        public Engine(NetworkSavedData data,BiConsumer<Object,NetworkHighlightS2CPacket> sender,Predicate<Object> connected){
            this.data=data;this.sender=sender;this.connected=connected;data.highlightInvalidator(this::invalidate);
        }
        public void submit(Object key,Request request,Collection<CompiledNetwork> selected,String message,State state,Object world,long tick){
            var old=peers.get(key);if(old!=null&&request.id()<=old.request.id())return;
            if(old==null&&peers.size()>=PEERS){
                if(overflow.size()<PEERS)overflow.put(key,new NetworkHighlightS2CPacket(request,++serial,data.highlightMembershipRevision(),State.ERROR,
                        "Highlight capacity reached; retry later.",0,1,0,0,new byte[0]));return;
            }
            if(old!=null)order.remove(old);
            overflow.remove(key);
            var p=new Peer(key);p.request=request;p.world=world;p.batch=++serial;p.revision=data.highlightMembershipRevision();p.started=tick;p.message=message;p.state=state;
            Map<Integer,CompiledNetwork> refs=new LinkedHashMap<>();long count=0;
            if(selected.size()>IDS){state=State.ERROR;p.message="Too many selected networks.";}
            else for(var n:selected){
                if(data.getNetwork(n.getId())!=n||!request.dimension().equals(n.getDimension())){state=State.ERROR;p.message="Selection changed; select again.";break;}
                if(refs.putIfAbsent(n.getId(),n)==null)count+=n.getElements().size();
                if(count>POSITIONS){state=State.ERROR;p.message="Highlight exceeds 50000 positions; select fewer networks.";break;}
            }
            p.state=state;
            if(state==State.READY){p.selected=Map.copyOf(refs);p.networks=refs.values().iterator();p.total=(int)count;}
            else p.built=true;
            peers.put(key,p);order.addLast(p);
            PerformanceDiagnostics.max("highlight.peersPeak",peers.size());
        }
        private void invalidate(CompiledNetwork removed){
            for(var p:order)if(p.selected.get(removed.getId())==removed)error(p,"Highlighted membership changed; select again.",State.INVALIDATED);
        }
        private void error(Peer p,String message,State state){
            p.batch=++serial;p.revision=data.highlightMembershipRevision();p.state=state;p.message=message;p.selected=Map.of();p.networks=Collections.emptyIterator();p.elements=Collections.emptyIterator();
            p.parts.clear();p.buffer=new ByteArrayOutputStream();p.next=p.total=0;p.built=true;p.delivered=false;
        }
        public void begin(long tick){
            now=tick;sent=0;
            var it=order.iterator();while(it.hasNext()){
                var p=it.next();p.sent=0;
                if(!connected.test(p.key)){it.remove();peers.remove(p.key);continue;}
                if(p.key instanceof ServerPlayer player&&player.serverLevel()!=p.world){it.remove();peers.remove(p.key);continue;}
                if(!p.delivered&&tick-p.started>LIFETIME_TICKS)error(p,"Highlight timed out; select fewer networks.",State.ERROR);
            }
            overflow.keySet().removeIf(key->!connected.test(key));
        }
        public boolean step(){
            overflowTurn=!overflowTurn;
            if(sent<PARTS_PER_TICK&&!overflow.isEmpty()&&(overflowTurn||order.isEmpty())){
                var it=overflow.entrySet().iterator();var e=it.next();it.remove();sender.accept(e.getKey(),e.getValue());sent++;return true;
            }
            int remaining=order.size();while(remaining-->0){
                var p=order.removeFirst();order.addLast(p);
                if(!connected.test(p.key)||p.key instanceof ServerPlayer player&&player.serverLevel()!=p.world){close(p.key);continue;}
                if(p.delivered)continue;
                if(!p.built){build(p);return true;}
                if(sent>=PARTS_PER_TICK||p.sent>=PARTS_PER_PLAYER)continue;
                int parts=p.state==State.READY?p.parts.size():1;
                byte[] payload=p.state==State.READY?p.parts.get(p.next):new byte[0];
                var packet=new NetworkHighlightS2CPacket(p.request,p.batch,p.revision,p.state,p.message,p.next,parts,p.total,p.total*9,payload);
                sender.accept(p.key,packet);p.next++;p.sent++;sent++;
                PerformanceDiagnostics.add("highlight.physicalPackets",1);PerformanceDiagnostics.add("highlight.payloadBytesSent",payload.length);
                PerformanceDiagnostics.max("highlight.packetsPerTickPeak",sent);PerformanceDiagnostics.max("highlight.playerPacketsPerTickPeak",p.sent);
                if(p.next==parts){p.delivered=true;p.parts.clear();p.buffer=new ByteArrayOutputStream();}
                return true;
            }
            return false;
        }
        private void build(Peer p){
            long began=PerformanceDiagnostics.begin();
            try{
                if(p.elements.hasNext()){
                    var e=p.elements.next();int role=switch(e.getType()){case INPUT->1;case OUTPUT->2;default->0;};
                    var b=new FriendlyByteBuf(Unpooled.buffer(9));try{b.writeByte(role);b.writeBlockPos(e.getPos());byte[] record=new byte[9];b.readBytes(record);p.buffer.writeBytes(record);}finally{b.release();}
                    if(p.buffer.size()==PART_POSITIONS*9){p.parts.add(p.buffer.toByteArray());p.buffer.reset();}
                }else if(p.networks.hasNext()){
                    var n=p.networks.next();if(data.getNetwork(n.getId())!=n){error(p,"Selection changed; select again.",State.INVALIDATED);return;}
                    p.elements=n.getElements().iterator();
                }else{
                    if(p.buffer.size()>0||p.parts.isEmpty())p.parts.add(p.buffer.toByteArray());p.buffer.reset();p.built=true;
                    PerformanceDiagnostics.add("highlight.batches",1);PerformanceDiagnostics.add("highlight.encodedPositionBytes",p.total*9L);
                }
                if(began!=0)PerformanceDiagnostics.max("highlight.retainedPositionBytesPeak",retainedBytes());
            }finally{PerformanceDiagnostics.elapsed("highlight.build",began);}
        }
        public long retainedBytes(){long bytes=0;for(var p:order){bytes+=p.buffer.size();for(var part:p.parts)bytes+=part.length;}return bytes;}
        public int peers(){return peers.size();}
        public void close(Object key){var p=peers.remove(key);if(p!=null)order.remove(p);overflow.remove(key);}
        public void stop(){peers.clear();order.clear();overflow.clear();data.highlightInvalidator(n->{});}
    }
    private HighlightSync() {}
}
