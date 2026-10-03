package com.example.compiledcircuits.gametest;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.networking.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;
import static com.example.compiledcircuits.networking.HighlightProtocol.*;

@GameTestHolder("compiledcircuits") @PrefixGameTestTemplate(false)
public class HighlightGameTests {
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
 private static CompiledNetwork network(NetworkSavedData data,int id,String dimension,int count,int offset){
  var elements=new ArrayList<CompiledCircuitElement>();
  for(int i=0;i<count;i++)elements.add(new CompiledCircuitElement(i+1,new BlockPos(offset+i,100,900000),i==0?CircuitElementType.INPUT:i==1?CircuitElementType.OUTPUT:CircuitElementType.WIRE,i==0?"compiledcircuits:input_endpoint":i==1?"compiledcircuits:output_endpoint":"compiledcircuits:basic_wire"));
  var n=new CompiledNetwork(id,"Network "+id,0,dimension,elements);data.addNetwork(n);return n;
 }
 private static NetworkHighlightS2CPacket codec(NetworkHighlightS2CPacket packet){
  var b=new FriendlyByteBuf(Unpooled.buffer());try{NetworkHighlightS2CPacket.encode(packet,b);check(b.readableBytes()<=PART_BYTES,"actual encoded part fits Forge payload limit");return NetworkHighlightS2CPacket.decode(b);}finally{b.release();}
 }
 private static int count(HighlightReplica r){var s=r.selection();return s.wires().size()+s.inputs().size()+s.outputs().size();}
 private static final class Peer { final HighlightReplica replica=new HighlightReplica();Request request;long id;int sent;boolean connected=true;final List<NetworkHighlightS2CPacket> received=new ArrayList<>();}
 private static final class Harness {
  final NetworkSavedData data=new NetworkSavedData();final List<Peer> peers=new ArrayList<>();long tick,encodedBytes,peakStaging;int sent;
  final HighlightSync.Engine engine=new HighlightSync.Engine(data,(key,packet)->{var p=(Peer)key;p.sent++;sent++;p.received.add(packet);
   var b=new FriendlyByteBuf(Unpooled.buffer());try{NetworkHighlightS2CPacket.encode(packet,b);encodedBytes+=b.readableBytes();}finally{b.release();}
   p.replica.accept(codec(packet),tick);peakStaging=Math.max(peakStaging,p.replica.stagingBytes());},key->((Peer)key).connected);
  Peer add(){var p=new Peer();peers.add(p);return p;}
  void request(Peer p,List<CompiledNetwork> selected){p.request=new Request(UUID.randomUUID(),++p.id,"minecraft:overworld");p.replica.expect(p.request,tick);engine.submit(p,p.request,selected,"",State.READY,this,tick);}
  void step(){tick++;sent=0;for(var p:peers)p.sent=0;engine.begin(tick);for(int i=0;i<512;i++)engine.step();
   check(sent<=PARTS_PER_TICK,"global delivery cap");for(var p:peers){check(p.sent<=PARTS_PER_PLAYER,"per-player cap");p.replica.tick(tick,CLIENT_STEPS);}
   check(engine.retainedBytes()<=(long)PEERS*BATCH_BYTES,"bounded encoded retention");}
  void until(java.util.function.BooleanSupplier condition){for(int i=0;i<20000;i++){step();if(condition.getAsBoolean())return;}throw new IllegalStateException("Highlight did not converge");}
 }
 @GameTest(template="empty",batch="highlight_filter",timeoutTicks=100)
 public static void filterBeforeCollectingAndAtomicAdmission(GameTestHelper h){
  var server=h.getLevel().getServer();var storage=server.overworld().getDataStorage();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();
  var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"highlight-test"));
  try{
   storage.set("compiledcircuits_networks",data);var a=network(data,1,"minecraft:overworld",3,0);network(data,2,"minecraft:the_nether",POSITIONS+1,0);
   var single=NetworkActionC2SPacket.execute(new NetworkActionC2SPacket(NetworkActionC2SPacket.Action.HIGHLIGHT,2,""),p);
   check(single.success()&&single.networks().isEmpty()&&single.message().contains("1"),"foreign oversize network filtered before capacity traversal");
   var group=NetworkBulkActionC2SPacket.execute(new NetworkBulkActionC2SPacket(NetworkBulkActionC2SPacket.BulkAction.HIGHLIGHT_NETWORKS,List.of(1,1,2),0),p);
   check(group.success()&&group.networks().equals(List.of(a))&&group.message().contains("1"),"same coordinates across dimensions stay separate; IDs deduplicated");
   var missing=NetworkOperations.execute(p.createCommandSourceStack(),NetworkOperations.Action.HIGHLIGHT,List.of(1,3),0,"");
   check(!missing.success()&&missing.networks().isEmpty(),"invalid member rejects entire group");
   int chunks=server.overworld().getChunkSource().getLoadedChunksCount();
   var f=new Harness();var remote=network(f.data,1,"minecraft:overworld",100,2000000);var peer=f.add();f.request(peer,List.of(remote));f.until(()->peer.replica.status()==HighlightReplica.Status.READY);
   check(chunks==server.overworld().getChunkSource().getLoadedChunksCount(),"remote highlight does not load chunks");
  }finally{NetworkOperations.forget(p);storage.set("compiledcircuits_networks",original);}h.succeed();
 }
 @GameTest(template="empty",batch="highlight_order",timeoutTicks=100)
 public static void latestRequestAndWorldContextWin(GameTestHelper h){
  var f=new Harness();var a=network(f.data,1,"minecraft:overworld",6100,0);var b=network(f.data,2,"minecraft:overworld",3,10000);var p=f.add();
  f.request(p,List.of(a));f.until(()->!p.received.isEmpty());var late=p.received.get(0);check(count(p.replica)==0,"first part does not expose partial selection");
  f.request(p,List.of(b));f.until(()->p.replica.status()==HighlightReplica.Status.READY);check(count(p.replica)==3,"B replaced A");
  p.replica.accept(late,f.tick);p.replica.accept(new NetworkHighlightS2CPacket(late.request(),late.batch()+100,late.revision(),State.ERROR,"late refusal",0,1,0,0,new byte[0]),f.tick);
  check(count(p.replica)==3,"late A success and refusal cannot overwrite B");
  var oldRequest=p.request;f.request(p,List.of());f.until(()->p.replica.status()==HighlightReplica.Status.READY);check(count(p.replica)==0,"explicit empty result clears");
  int start=p.received.size();f.request(p,List.of(a));f.until(()->p.received.size()>start);var cancelledPart=p.received.get(start);
  f.request(p,List.of());f.until(()->p.replica.status()==HighlightReplica.Status.READY);
  p.replica.accept(cancelledPart,f.tick);check(count(p.replica)==0,"cancel during multipart transfer cannot resurrect selection");
  p.replica.reset();p.replica.expect(new Request(UUID.randomUUID(),p.id+1,"minecraft:overworld"),f.tick);
  for(var packet:p.received)p.replica.accept(packet,f.tick);check(count(p.replica)==0&&p.replica.status()==HighlightReplica.Status.WAITING,"same dimension return with new context rejects old replies");
  h.succeed();
 }
 @GameTest(template="empty",batch="highlight_membership",timeoutTicks=100)
 public static void membershipInvalidatesBeforeDuringAndAfterDelivery(GameTestHelper h){
  for(int phase=0;phase<3;phase++){
   var f=new Harness();var a=network(f.data,1,"minecraft:overworld",6100,0);var p=f.add();f.request(p,List.of(a));
   if(phase==1)f.until(()->!p.received.isEmpty());
   if(phase==2)f.until(()->p.replica.status()==HighlightReplica.Status.READY);
   f.data.removeNetwork(1);f.until(()->p.replica.status()==HighlightReplica.Status.ERROR);check(count(p.replica)==0,"removal clears all stages");
   for(var packet:p.received)p.replica.accept(packet,f.tick);check(count(p.replica)==0,"old geometry cannot resurrect after invalidation");
  }
  var f=new Harness();var a=network(f.data,1,"minecraft:overworld",3,0);var p=f.add();f.request(p,List.of(a));f.until(()->p.replica.status()==HighlightReplica.Status.READY);
  a.markBroken(new BrokenCircuitElement(1,"minecraft:air",0));a.setName("Renamed");f.step();check(count(p.replica)==3,"damage and rename preserve membership highlight");
  f.data.replaceNetwork(new CompiledNetwork(1,"replacement",0,"minecraft:overworld",Set.of(new BlockPos(40,100,0)),Set.of(),Set.of()));f.until(()->p.replica.status()==HighlightReplica.Status.ERROR);
  p.connected=false;f.step();check(f.engine.peers()==0,"disconnect releases references");f.engine.stop();h.succeed();
 }
 @GameTest(template="empty",batch="highlight_limits",timeoutTicks=100)
 public static void limitsAndLargeSelection(GameTestHelper h){
  for(int size:new int[]{POSITIONS-1,POSITIONS,POSITIONS+1}){
   var f=new Harness();var n=network(f.data,1,"minecraft:overworld",size,0);var p=f.add();f.request(p,List.of(n));
   f.until(()->p.replica.status()!=HighlightReplica.Status.WAITING);
   check(size>POSITIONS?p.replica.status()==HighlightReplica.Status.ERROR&&count(p.replica)==0:count(p.replica)==size,"limit-1/limit/limit+1 contract");
   if(size==POSITIONS)System.out.println("HIGHLIGHT_MAX: positions="+size+", parts="+p.received.size()+", encodedBytes="+f.encodedBytes+", peakStagingBytes="+f.peakStaging+", simulatedTicks="+f.tick);
  }h.succeed();
 }
 @GameTest(template="empty",batch="highlight_parts",timeoutTicks=100)
 public static void reorderedMalformedAndRecovery(GameTestHelper h){
  var f=new Harness();var n=network(f.data,1,"minecraft:overworld",6100,0);var p=f.add();f.request(p,List.of(n));f.until(()->p.replica.status()==HighlightReplica.Status.READY);
  var r=new HighlightReplica();r.expect(p.request,0);for(int i=p.received.size()-1;i>=0;i--){r.accept(p.received.get(i),0);r.accept(p.received.get(i),0);}check(count(r)==0,"received batch is not partially visible");
  for(int i=0;i<20;i++)r.tick(i,CLIENT_STEPS);check(count(r)==6100,"reordered repeated parts assemble atomically");
  r.expect(p.request,0);var first=p.received.get(0);r.accept(first,0);var corrupt=first.payload();corrupt[0]=2;
  r.accept(new NetworkHighlightS2CPacket(first.request(),first.batch(),first.revision(),first.state(),first.message(),0,first.parts(),first.positions(),first.bytes(),corrupt),0);
  check(r.status()==HighlightReplica.Status.ERROR&&count(r)==0,"contradictory repeat fails closed");
  r.expect(p.request,0);r.accept(first,0);r.tick(IDLE_TICKS+1,CLIENT_STEPS);check(r.status()==HighlightReplica.Status.ERROR,"incomplete batch times out");
  r.expect(p.request,0);for(var part:p.received)r.accept(part,0);for(int i=0;i<20;i++)r.tick(i,CLIENT_STEPS);check(count(r)==6100,"next valid request works after error");
  boolean rejected=false;try{new NetworkHighlightS2CPacket(first.request(),1,0,State.READY,"",0,PARTS+1,0,0,new byte[0]);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"part limit before staging allocation");
  var oversized=new FriendlyByteBuf(Unpooled.buffer(PART_BYTES+1));try{oversized.writeZero(PART_BYTES+1);rejected=false;try{NetworkHighlightS2CPacket.decode(oversized);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"encoded byte cap before decode loops");}finally{oversized.release();}
  r.expect(p.request,0);
  var conflict=new FriendlyByteBuf(Unpooled.buffer());
  try{conflict.writeByte(0);conflict.writeBlockPos(BlockPos.ZERO);conflict.writeByte(1);conflict.writeBlockPos(BlockPos.ZERO);
   byte[] raw=new byte[conflict.readableBytes()];conflict.readBytes(raw);
   r.accept(new NetworkHighlightS2CPacket(p.request,first.batch()+1,0,State.READY,"",0,1,2,raw.length,raw),0);r.tick(0,20);
   check(r.status()==HighlightReplica.Status.ERROR&&count(r)==0,"same position in contradictory categories rejects whole batch");
  }finally{conflict.release();}
  r.expect(p.request,0);r.accept(first,0);var second=p.received.get(1);
  r.accept(new NetworkHighlightS2CPacket(second.request(),second.batch(),second.revision()+1,State.READY,second.message(),second.index(),second.parts(),second.positions(),second.bytes(),second.payload()),0);
  check(r.status()==HighlightReplica.Status.ERROR,"conflicting revision header rejected");
  r.expect(p.request,0);r.tick(LIFETIME_TICKS+1,20);check(r.status()==HighlightReplica.Status.ERROR,"no first reply also has a bounded timeout");
  h.succeed();
 }
 @GameTest(template="empty",batch="highlight_fairness",timeoutTicks=100)
 public static void multipleReceiversRemainIndependent(GameTestHelper h){
  var f=new Harness();var n=network(f.data,1,"minecraft:overworld",10000,0);
  for(int i=0;i<12;i++){var p=f.add();f.request(p,List.of(n));}
  f.request(f.peers.get(0),List.of());f.until(()->f.peers.stream().allMatch(p->p.replica.status()==HighlightReplica.Status.READY));
  check(count(f.peers.get(0).replica)==0,"one player's cancel does not cancel others");for(int i=1;i<12;i++)check(count(f.peers.get(i).replica)==10000,"independent fair delivery");
  check(f.engine.retainedBytes()==0,"encoded delivery buffers released after send");f.engine.stop();check(f.engine.peers()==0,"stop releases selections");h.succeed();
 }
}
