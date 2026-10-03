package com.example.compiledcircuits.gametest;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.networking.*;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;
import static com.example.compiledcircuits.networking.DamageProtocol.*;

@GameTestHolder("compiledcircuits") @PrefixGameTestTemplate(false)
public class DamageSyncGameTests {
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
 private static CompiledNetwork network(NetworkSavedData data,int id,int size,String dimension){
  var elements=new ArrayList<CompiledCircuitElement>();
  for(int i=1;i<=size;i++)elements.add(new CompiledCircuitElement(i,new BlockPos(i%128,150,i/128+id*256),CircuitElementType.WIRE,"compiledcircuits:basic_wire"));
  var n=new CompiledNetwork(id,"Network "+id,0,dimension,elements);data.addNetwork(n);return n;
 }
 private static int count(DamageReplica replica){return replica.networks().stream().mapToInt(n->n.elements().size()).sum();}
 private static DamageBatch batch(List<Change> changes,Kind kind,long base,long revision,long id){
  var iterator=changes.iterator();var cursor=new DamageLedger.Cursor(){public boolean done(){return !iterator.hasNext();}public Change step(){return iterator.next();}};
  var b=new DamageBatch.Builder(cursor,kind,base,revision,id);while(!b.done())b.step();return b.result();
 }
 private static DamagePartS2CPacket codec(DamagePartS2CPacket packet){
  var bytes=new FriendlyByteBuf(Unpooled.buffer());try{DamagePartS2CPacket.encode(packet,bytes);check(bytes.readableBytes()<=PART_BYTES,"actual encoded part limit");return DamagePartS2CPacket.decode(bytes);}finally{bytes.release();}
 }
 private static void complete(DamageReplica replica,long now){for(int i=0;i<10000&&replica.assembling();i++)replica.tick(now,1024);check(replica.status()==DamageReplica.Status.READY,"assembly completes");}
 private static final class Peer {final UUID token=UUID.randomUUID();final DamageReplica replica=new DamageReplica();long acknowledged;int packets;Peer(){replica.reset(token);}}
 private static final class Harness {
  final NetworkSavedData data=new NetworkSavedData();final List<Peer> peers=new ArrayList<>();final DamageSync.Engine engine;
  long now;int packets,maxPerTick;long bytes,wireBytes;java.util.function.Consumer<DamagePartS2CPacket> hook;
  Harness(){engine=new DamageSync.Engine(data.damage(),(key,packet)->{var p=(Peer)key;p.packets++;packets++;bytes+=packet.payload().length;
   var encoded=new FriendlyByteBuf(Unpooled.buffer());try{DamagePartS2CPacket.encode(packet,encoded);wireBytes+=encoded.readableBytes();check(encoded.readableBytes()<=PART_BYTES,"actual encoded part limit");p.replica.accept(DamagePartS2CPacket.decode(encoded),now);}finally{encoded.release();}if(hook!=null)hook.accept(packet);});}
  Peer add(){var p=new Peer();check(engine.open(p,p.token,now),"peer admitted");peers.add(p);return p;}
  void tick(){now++;int before=packets;for(var p:peers)p.packets=0;engine.begin(now);for(int i=0;i<BUILD_STEPS;i++)engine.step();
   maxPerTick=Math.max(maxPerTick,packets-before);check(packets-before<=PARTS_PER_TICK,"global delivery budget");
   for(var p:peers){check(p.packets<=PARTS_PER_PLAYER,"per-player delivery budget");p.replica.tick(now,4096);
    if(p.replica.status()==DamageReplica.Status.READY&&p.acknowledged!=p.replica.completedBatch()){
     p.acknowledged=p.replica.completedBatch();engine.ack(p,p.token,p.acknowledged,p.replica.revision());}}
   check(engine.retainedJournalBytes()<=JOURNAL_BYTES,"bounded journal");check(engine.retainedEncodedBytes()<=ENCODED_BYTES,"bounded shared encoded retention");}
  void until(java.util.function.BooleanSupplier condition){for(int i=0;i<10000;i++){tick();if(condition.getAsBoolean())return;}throw new IllegalStateException("No convergence after events stop");}
  boolean all(int size){return peers.stream().allMatch(p->p.replica.status()==DamageReplica.Status.READY&&count(p.replica)==size&&p.replica.revision()==data.damage().revision());}
 }
 @GameTest(template="empty",batch="damage_protocol",timeoutTicks=100)
 public static void multipartRevisionsAndRecovery(GameTestHelper h){
  var context=UUID.randomUUID();var epoch=UUID.randomUUID();var replica=new DamageReplica();replica.reset(context);
  var changes=new ArrayList<Change>();changes.add(Change.meta(new Meta(1,"Network",0,"minecraft:overworld","")));
  for(int i=1;i<=1500;i++)changes.add(Change.upsert(new Element(1,i,new BlockPos(i,100,0),CircuitElementType.WIRE,"compiledcircuits:basic_wire","minecraft:air",i)));
  var full=batch(changes,Kind.SNAPSHOT,-1,1,1);check(full.partCount()>1,"multipart fixture");
  for(int i=full.partCount()-1;i>=0;i--){var packet=codec(full.packet(context,epoch,i));replica.accept(packet,0);replica.accept(packet,0);}
  replica.tick(0,1);check(count(replica)==0,"snapshot not partially visible");complete(replica,0);check(count(replica)==1500,"reordered parts and exact repeats converge");
  replica.accept(full.packet(context,epoch,0),1);check(replica.status()==DamageReplica.Status.READY,"completed duplicate ignored");
  var wrong=batch(List.of(Change.removeNetwork(1)),Kind.DELTA,99,100,2);replica.accept(wrong.packet(context,epoch,0),2);
  check(replica.status()==DamageReplica.Status.STALE&&count(replica)==1500,"wrong base preserves old data as stale");
  var empty=batch(List.of(),Kind.SNAPSHOT,-1,2,3);replica.accept(empty.packet(context,epoch,0),3);complete(replica,3);check(count(replica)==0,"empty snapshot explicitly clears");
  var timed=batch(changes,Kind.SNAPSHOT,-1,3,4);replica.accept(timed.packet(context,epoch,0),4);replica.tick(IDLE_TIMEOUT+10,1);
  check(replica.status()==DamageReplica.Status.STALE,"incomplete assembly times out");
  var recovery=batch(changes,Kind.SNAPSHOT,-1,3,5);replica.accept(recovery.packet(context,epoch,0),300);
  var original=recovery.packet(context,epoch,0);var corrupt=original.payload();corrupt[corrupt.length-1]^=1;
  replica.accept(new DamagePartS2CPacket(context,epoch,Kind.SNAPSHOT,5,-1,3,0,recovery.partCount(),recovery.records,recovery.bytes,corrupt),300);
  check(replica.status()==DamageReplica.Status.STALE,"contradictory repeated part rejected");
  var good=batch(changes,Kind.SNAPSHOT,-1,4,6);for(int i=0;i<good.partCount();i++)replica.accept(good.packet(context,epoch,i),301);complete(replica,301);
  var newer=UUID.randomUUID();replica.reset(newer);replica.accept(good.packet(context,epoch,0),302);check(replica.status()==DamageReplica.Status.UNKNOWN,"old context cannot restore markers after same-dimension return");
  boolean rejected=false;try{new DamagePartS2CPacket(context,epoch,Kind.SNAPSHOT,7,-1,5,0,MAX_PARTS+1,0,0,new byte[0]);}catch(IllegalArgumentException expected){rejected=true;}
  check(rejected,"header limits before assembly allocation");h.succeed();
 }
 @GameTest(template="empty",batch="damage_coalescing",timeoutTicks=100)
 public static void coalescingAndVisibleMetadata(GameTestHelper h){
  var f=new Harness();var n=network(f.data,1,3,"minecraft:overworld");var peer=f.add();f.until(()->f.all(0));int initial=f.packets;
  n.markBroken(new BrokenCircuitElement(1,"minecraft:air",1));n.markRepaired(1);for(int i=0;i<5;i++)f.tick();
  check(f.packets==initial&&f.all(0),"same-tick new damage and repair produce no delta");
  n.markBroken(new BrokenCircuitElement(1,"minecraft:air",2));f.until(()->f.all(1));int before=f.packets;
  for(int i=0;i<10;i++){n.markBroken(new BrokenCircuitElement(1,"minecraft:air",3));f.tick();}check(before==f.packets,"unchanged damage has no traffic");
  n.markRepaired(1);n.markBroken(new BrokenCircuitElement(1,"minecraft:stone",4));n.setName("Renamed");
  int folder=f.data.createFolder("Folder",0);n.setFolderId(folder);f.until(()->f.all(1));
  var client=peer.replica.networks().iterator().next();check(client.meta().name().equals("Renamed")&&client.meta().path().equals("Folder")&&client.elements().get(1).actual().equals("minecraft:stone"),"final state and rename/move delivered");
  f.data.renameFolder(folder,"New folder");f.until(()->peer.replica.networks().iterator().next().meta().path().equals("New folder"));
  n.updateBrokenActual(1,"minecraft:dirt");f.until(()->peer.replica.networks().iterator().next().elements().get(1).actual().equals("minecraft:dirt"));
  f.data.removeNetwork(1);f.until(()->f.all(0));check(peer.replica.networks().isEmpty(),"last removal clears all damage metadata");h.succeed();
 }
 @GameTest(template="empty",batch="damage_delivery",timeoutTicks=100)
 public static void changesDuringSharedSnapshot(GameTestHelper h){
  var f=new Harness();var a=network(f.data,1,10000,"minecraft:overworld");var b=network(f.data,2,2,"minecraft:the_nether");
  for(int i=1;i<=10000;i++)a.markBroken(new BrokenCircuitElement(i,"minecraft:air",1));b.markBroken(new BrokenCircuitElement(1,"minecraft:air",1));
  var p=f.add();var q=f.add();var r=f.add();final boolean[] changed={false};
  f.hook=packet->{if(!changed[0]&&packet.kind()==Kind.SNAPSHOT){changed[0]=true;f.data.removeNetwork(a.getId());b.markRepaired(1);b.markBroken(new BrokenCircuitElement(2,"minecraft:stone",2));b.setName("Other dimension");}};
  f.until(()->changed[0]&&f.all(1));
  for(var peer:f.peers){var n=peer.replica.networks().iterator().next();check(n.meta().id()==2&&n.meta().dimension().equals("minecraft:the_nether")&&n.elements().get(2)!=null,"global GUI retains foreign dimension; removed network never resurrects");}
  int oldPackets=f.packets;f.engine.close(q);for(int i=0;i<10;i++)f.tick();check(f.engine.clients()==2&&f.packets==oldPackets,"disconnect releases delivery without idle traffic");h.succeed();
 }
 @GameTest(template="empty",batch="damage_mass",timeoutTicks=100)
 public static void massDamageRepairAndLegacyComparison(GameTestHelper h){
  command(h,"ccperf reset");command(h,"ccperf start");
  for(int size:new int[]{100,1000,10000}){
   var f=new Harness();var n=network(f.data,1,size,"minecraft:overworld");f.add();f.add();f.add();f.until(()->f.all(0));int before=f.packets;long initialBytes=f.bytes,initialWire=f.wireBytes,initialBuild=metric("damage.build.nanos");
   for(int i=1;i<=size;i++)n.markBroken(new BrokenCircuitElement(i,"minecraft:air",1));
   long began=System.nanoTime();var legacy=new FriendlyByteBuf(Unpooled.buffer());int legacyBytes;
   try{BrokenElementListS2CPacket.encode(new BrokenElementListS2CPacket(NetworkGuiSync.buildBrokenEntries(f.data)),legacy);
    BrokenElementsS2CPacket.encode(new BrokenElementsS2CPacket("minecraft:overworld",BrokenElementSync.collectBrokenPositions(f.data,"minecraft:overworld")),legacy);legacyBytes=legacy.readableBytes();}finally{legacy.release();}
   long legacyNanos=System.nanoTime()-began;long started=f.now;f.until(()->f.all(size));int damagedPackets=f.packets-before;long damageBytes=f.bytes-initialBytes;long newWire=f.wireBytes-initialWire,newBuild=metric("damage.build.nanos")-initialBuild;
   before=f.packets;for(int i=1;i<=size;i++)n.markRepaired(i);f.until(()->f.all(0));
   check(f.packets-before==3,"one removal batch delivered once per client");
   System.out.println("DAMAGE_MASS: elements="+size+", legacySnapshotBytes="+legacyBytes+", legacyBuildEncodeNanos="+legacyNanos+", newDamagePacketsThreeClients="+damagedPackets+", newPayloadBytesThreeClients="+damageBytes+", newEncodedBytesThreeClients="+newWire+", newSharedBuildNanos="+newBuild+", newRepairPackets="+(f.packets-before)+", serverTicks="+(f.now-started));
  }command(h,"ccperf stop");command(h,"ccperf export");command(h,"ccperf reset");h.succeed();
 }
 @GameTest(template="empty",batch="damage_backlog",timeoutTicks=100)
 public static void continuousChangesStayBounded(GameTestHelper h){
  var f=new Harness();var n=network(f.data,1,10000,"minecraft:overworld");for(int i=1;i<=10000;i++)n.markBroken(new BrokenCircuitElement(i,"minecraft:air",1));
  for(int i=0;i<12;i++)f.add();
  for(int tick=0;tick<80;tick++){for(int i=1;i<=200;i++)n.updateBrokenActual(i,tick%2==0?"minecraft:stone":"minecraft:dirt");f.tick();}
  f.until(()->f.all(10000));
  for(var p:f.peers)check(p.replica.networks().iterator().next().elements().get(1).actual().equals("minecraft:dirt"),"client catches current state after backlog overflow");
  check(f.maxPerTick<=8,"many clients share global cap");h.succeed();
 }

 @GameTest(template="empty",batch="damage_views",timeoutTicks=100)
 public static void globalRowsAndDimensionMarkers(GameTestHelper h){
  var f=new Harness();var a=network(f.data,1,3,"minecraft:overworld");var b=network(f.data,2,2,"minecraft:the_nether");
  a.markBroken(new BrokenCircuitElement(1,"minecraft:air",1));a.markBroken(new BrokenCircuitElement(2,"minecraft:air",2));b.markBroken(new BrokenCircuitElement(1,"minecraft:stone",3));
  var p=f.add();f.until(()->f.all(3));
  for(String dimension:List.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end")){
   var view=new DamageView(p.replica,dimension);boolean rejected=false;
   try{view.entries();}catch(IllegalStateException expected){rejected=true;}check(rejected,"partial presentation is not readable");
   for(int i=0;i<30&&!view.step();i++){}
   check(view.entries().size()==3,"GUI contains all dimensions without opening GUI");
   check(view.positions().size()==(dimension.equals("minecraft:overworld")?2:dimension.equals("minecraft:the_nether")?1:0),"markers filtered from same committed revision");
   check(view.contains("minecraft:the_nether",b.getElement(1).getPos()),"foreign focused row remains valid");
  }
  a.markRepaired(1);a.markRepaired(2);b.markRepaired(1);f.until(()->f.all(0));var empty=new DamageView(p.replica,"minecraft:overworld");while(!empty.step()){}
  check(empty.entries().isEmpty()&&empty.positions().isEmpty()&&!empty.contains("minecraft:the_nether",b.getElement(1).getPos()),"last repair clears GUI, markers and focus");h.succeed();
 }
 @GameTest(template="empty",batch="damage_capacity",timeoutTicks=100)
 public static void capacityRecoveryAndAdmission(GameTestHelper h){
  var f=new Harness();var n=network(f.data,1,1,"minecraft:overworld");n.markBroken(new BrokenCircuitElement(1,"minecraft:air",1));n.setName("x".repeat(NAME+1));
  var p=f.add();f.until(()->p.replica.status()==DamageReplica.Status.OVER_CAPACITY);
  check(count(p.replica)==0,"capacity failure is explicit, never a healthy empty snapshot");
  check(!f.engine.open(p,UUID.randomUUID(),f.now),"resync rate limited before rebuilding");
  var q=f.add();f.until(()->q.replica.status()==DamageReplica.Status.OVER_CAPACITY);
  n.setName("Valid again");f.until(()->f.all(1));
  check(p.replica.networks().iterator().next().meta().name().equals("Valid again"),"oversized legacy metadata recovers after valid mutation");
  for(int i=f.peers.size();i<MAX_CLIENTS;i++)f.add();
  var excess=new Peer();check(!f.engine.open(excess,excess.token,f.now),"bounded recipient admission");f.until(()->excess.replica.status()==DamageReplica.Status.OVER_CAPACITY);
  f.engine.close(q);check(f.engine.open(excess,excess.token,f.now),"disconnect releases admission slot");
  h.succeed();
 }

 private static Object field(Object target,String name){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
 private static long metric(String name){try{var f=PerformanceDiagnostics.class.getDeclaredField("active");f.setAccessible(true);var values=(Map<?,?>)field(f.get(null),"counters");var value=values.get(name);return value==null?0:((Number)value).longValue();}catch(Exception e){throw new IllegalStateException(e);}}
 private static void command(GameTestHelper h,String command){try{var server=h.getLevel().getServer();server.getCommands().getDispatcher().execute(command,server.createCommandSourceStack());}catch(Exception e){throw new IllegalStateException(e);}}
 @GameTest(template="empty",batch="damage_chat",timeoutTicks=150)
 public static void continuousChatHasFixedDeadline(GameTestHelper h) throws Exception {
  var server=h.getLevel().getServer();DamageNotifications.stop(server);
  var changed=DamageNotifications.class.getDeclaredMethod("changed",net.minecraft.server.level.ServerLevel.class,int.class,boolean.class);changed.setAccessible(true);
  var pending=DamageNotifications.class.getDeclaredField("pending");pending.setAccessible(true);
  final int[] firstDeadline={-1};final Object[] firstWindow={null};
  new Runnable(){int elapsed;public void run(){try{
   changed.invoke(null,h.getLevel(),7,elapsed%2==1);
   var current=((Map<?,?>)pending.get(null)).get(server);
   if(firstWindow[0]==null){firstWindow[0]=current;firstDeadline[0]=(Integer)field(current,"deadline");}
   if(elapsed<CHAT_TICKS){check(current==firstWindow[0]&&(Integer)field(current,"deadline")==firstDeadline[0],"continuous transitions never move deadline");
    check(((Number)field(current,"damaged")).longValue()+((Number)field(current,"repaired")).longValue()==elapsed+1,"counts are physical transitions");
    check(((Set<?>)field(current,"networks")).size()==1,"affected network counted once");}
   if(elapsed++==CHAT_TICKS+2){check(current!=firstWindow[0],"real END-tick handler emits summary while changes continue");
    DamageNotifications.stop(server);check(!((Map<?,?>)pending.get(null)).containsKey(server),"server stop discards pending chat window");h.succeed();return;}
   // GameTest stores scheduled tasks by Runnable identity; wrap to avoid removing the rescheduled task.
   h.runAfterDelay(1,()->this.run());
  }catch(Exception e){DamageNotifications.stop(server);throw new IllegalStateException(e);}}}.run();
 }
}
