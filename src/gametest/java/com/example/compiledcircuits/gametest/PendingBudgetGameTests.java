package com.example.compiledcircuits.gametest;
import com.example.compiledcircuits.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import java.util.*;
import com.example.compiledcircuits.config.ServerConfig;
import com.example.compiledcircuits.registry.ModBlocks;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
@GameTestHolder("compiledcircuits") @PrefixGameTestTemplate(false)
public class PendingBudgetGameTests {
 @GameTest(template="empty",batch="pending_measurement",timeoutTicks=300)
 public static void measurePending(GameTestHelper h) throws Exception {
  var f=new Fixture(h);var points=new HashSet<BlockPos>();
  for(int i=0;i<1000;i++)points.add(new BlockPos(32+i%20,180,32+i/20));
  for(var pos:points)f.load(f.level,pos);
  var n=new CompiledNetwork(1,"pending baseline",0,f.level.dimension().location().toString(),points,Set.of(),Set.of());f.data.addNetwork(n);
  f.command("ccperf reset");f.command("ccperf start");
  for(var pos:points)NetworkIntegrityManager.scheduleCheck(f.level,pos);
  long started=System.nanoTime();NetworkIntegrityManager.processPending(f.level.getServer());
  System.out.println("PENDING_MEASUREMENT: first drain broken="+n.getBrokenElements().size()+", nanos="+(System.nanoTime()-started));
  new Runnable(){public void run(){try{
   if(n.isIntegrityPending()||!((Collection<?>)field(field(f.data,"runtime"),"queue")).isEmpty()){f.later(this);return;}
   DamageNotifications.flush(f.level.getServer());
   h.assertTrue(n.getBrokenElements().size()==1000,"entire baseline burst eventually confirmed");
   h.assertTrue(metric("work.POINT.tickPeak")<=ServerConfig.POINT_WORK.get(),"point quota bounds baseline burst");
   f.command("ccperf stop");f.command("ccperf export");f.close();h.succeed();
  }catch(Exception e){f.close();throw new IllegalStateException(e);}}}.run();
 }

 private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
 private static Map<?,?> metrics(){try{var f=PerformanceDiagnostics.class.getDeclaredField("active");f.setAccessible(true);return (Map<?,?>)field(f.get(null),"counters");}catch(Exception e){throw new IllegalStateException(e);}}
 private static long metric(String key){var value=metrics().get(key);return value==null?0:((Number)value).longValue();}
 private static final class Fixture implements AutoCloseable {
  final GameTestHelper h;final ServerLevel level;
  final NetworkSavedData original,data=new NetworkSavedData();
  final Map<ServerLevel,Map<BlockPos,BlockState>> old=new IdentityHashMap<>();
  final Map<ServerLevel,Set<Long>> forced=new IdentityHashMap<>();
  final List<ServerPlayer> players=new ArrayList<>();
  final int total=ServerConfig.TOTAL_WORK.get(),pending=ServerConfig.MAX_PENDING.get(),point=ServerConfig.POINT_WORK.get(),full=ServerConfig.RECHECK_WORK.get(),repair=ServerConfig.REPAIR_WORK.get(),audit=ServerConfig.AUDIT_WORK.get();
  Fixture(GameTestHelper h){this.h=h;level=h.getLevel();original=NetworkSavedData.get(level.getServer());level.getServer().overworld().getDataStorage().set("compiledcircuits_networks",data);}
  void limits(){ServerConfig.MAX_PENDING.set(4);ServerConfig.POINT_WORK.set(4);ServerConfig.RECHECK_WORK.set(8);ServerConfig.REPAIR_WORK.set(2);ServerConfig.AUDIT_WORK.set(4);}
  void load(ServerLevel l,BlockPos p){for(long c:RuntimeSignalReader.dependencyChunks(p)){if(!l.getForcedChunks().contains(c)){l.setChunkForced(ChunkPos.getX(c),ChunkPos.getZ(c),true);forced.computeIfAbsent(l,k->new HashSet<>()).add(c);}l.getChunk(ChunkPos.getX(c),ChunkPos.getZ(c));}}
  void set(ServerLevel l,BlockPos p,BlockState s){old.computeIfAbsent(l,k->new LinkedHashMap<>()).putIfAbsent(p,l.getBlockState(p));l.setBlock(p,s,3);}
  CompiledNetwork network(ServerLevel l,BlockPos base,int wires){
   var positions=new LinkedHashSet<BlockPos>();
   load(l,base);set(l,base,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());set(l,base.below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
   var output=base.above(2);set(l,output,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
   for(int i=0;i<wires;i++){var p=base.offset(2+i%12,0,i/12);load(l,p);set(l,p,ModBlocks.BASIC_WIRE.get().defaultBlockState());positions.add(p);}
   var n=new CompiledNetwork(data.getNextNetworkId(),"budget",0,l.dimension().location().toString(),CompiledElementFactory.create(l,positions,Set.of(base),Set.of(output)));data.addNetwork(n);return n;
  }
  ServerPlayer player(){var p=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"repair-budget"));p.getAbilities().mayBuild=true;players.add(p);return p;}
  NetworkOperations.Result repair(ServerPlayer p,CompiledNetwork... networks){return NetworkOperations.execute(p.createCommandSourceStack(),NetworkOperations.Action.REPAIR,Arrays.stream(networks).map(CompiledNetwork::getId).toList(),0,"");}
  void command(String c){try{level.getServer().getCommands().getDispatcher().execute(c,level.getServer().createCommandSourceStack());}catch(Exception e){throw new IllegalStateException(e);}}
  void later(Runnable r){h.runAfterDelay(1,()->r.run());}
  public void close(){
   RepairJobs.stop(level.getServer());DamageNotifications.stop(level.getServer());CompilationJobs.stop(level.getServer());NetworkRuntime.stop(level.getServer());
   for(var entry:old.entrySet())entry.getValue().forEach((p,s)->entry.getKey().setBlock(p,s,3));
   forced.forEach((l,cs)->cs.forEach(c->l.setChunkForced(ChunkPos.getX(c),ChunkPos.getZ(c),false)));
   for(var p:players)NetworkOperations.forget(p);
   level.getServer().overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();
   ServerConfig.TOTAL_WORK.set(total);ServerConfig.MAX_PENDING.set(pending);ServerConfig.POINT_WORK.set(point);ServerConfig.RECHECK_WORK.set(full);ServerConfig.REPAIR_WORK.set(repair);ServerConfig.AUDIT_WORK.set(audit);
   command("ccperf reset");
  }
 }
 @GameTest(template="empty",batch="pending_overflow",timeoutTicks=900)
 public static void overflowAndCrossDimensionConvergence(GameTestHelper h){
  var f=new Fixture(h);f.limits();var other=f.level.getServer().getLevel(Level.NETHER);
  var a=f.network(f.level,new BlockPos(96,150,96),72);var b=f.network(other,new BlockPos(96,100,96),12);
  var first=a.getWires().iterator().next();
  new Runnable(){int phase;boolean repeated;public void run(){try{
   if(phase==0){if(a.getEffectiveSignal()!=15||b.getEffectiveSignal()!=15){f.later(this);return;}
    f.command("ccperf reset");f.command("ccperf start");
    for(var p:a.getWires())f.set(f.level,p,Blocks.AIR.defaultBlockState());
    for(var p:b.getWires())f.set(other,p,Blocks.AIR.defaultBlockState());
    h.assertTrue(a.isIntegrityPending()&&b.isIntegrityPending()&&a.getEffectiveSignal()==0&&b.getEffectiveSignal()==0,"overflow gates both dimensions immediately");
    h.assertTrue(metric("pending.queuePeak")<=4&&metric("pending.overflow")>0,"bounded exact queue promotes overflow");phase=1;
   }else if(phase==1){
    if(!repeated&&metric("integrity.recheckElements")>=8){f.set(f.level,first,ModBlocks.BASIC_WIRE.get().defaultBlockState());NetworkIntegrityManager.scheduleCheck(f.level,first);repeated=true;}
    if(!repeated||a.isIntegrityPending()||b.isIntegrityPending()){f.later(this);return;}
    h.assertTrue(a.getBrokenElements().size()==71&&b.getBrokenElements().size()==12,"new generation is not cleared by old pass; exact convergence");
    h.assertTrue(metric("work.POINT.tickPeak")<=4&&metric("work.RECHECK.tickPeak")<=8&&metric("work.AUDIT.units")>0,"quotas and audit progress");
    var p=f.player();h.assertTrue(f.repair(p,a,b).code()==NetworkOperations.Code.QUEUED,"bulk repair queued");phase=2;
   }else{
    if(a.isDamaged()||b.isDamaged()||a.isIntegrityPending()||b.isIntegrityPending()||f.players.stream().anyMatch(RepairJobs::isBusy)){f.later(this);return;}
    if(a.getEffectiveSignal()!=15||b.getEffectiveSignal()!=15){f.later(this);return;}
    h.assertTrue(metric("repair.result.PLACED")==83,"fixed target count placed across dimensions");
    h.assertTrue(metric("work.REPAIR.tickPeak")<=2,"repair quota shared across dimensions");
    f.command("ccperf stop");f.command("ccperf export");f.close();h.succeed();return;
   }f.later(this);
  }catch(Exception e){f.close();throw new IllegalStateException("overflow phase "+phase,e);}}}.run();
 }
 @GameTest(template="empty",batch="pending_repair",timeoutTicks=600)
 public static void repairFairnessAndCancellation(GameTestHelper h){
  var f=new Fixture(h);f.limits();var large=f.network(f.level,new BlockPos(144,150,144),60);var small=f.network(f.level,new BlockPos(176,150,144),1);
  var p=f.player();var q=f.player();
  new Runnable(){int phase,placed;boolean smallFirst;public void run(){try{
   if(phase==0){if(large.getEffectiveSignal()!=15||small.getEffectiveSignal()!=15){f.later(this);return;}
    for(var pos:large.getWires())f.set(f.level,pos,Blocks.AIR.defaultBlockState());for(var pos:small.getWires())f.set(f.level,pos,Blocks.AIR.defaultBlockState());phase=1;
   }else if(phase==1){if(large.isIntegrityPending()||small.isIntegrityPending()){f.later(this);return;}
    f.command("ccperf reset");f.command("ccperf start");
    h.assertTrue(f.repair(p,large).success()&&f.repair(q,small).success(),"two players accepted");
    h.assertTrue(f.repair(f.player(),large).code()==NetworkOperations.Code.BUSY,"overlap rejected atomically");phase=2;
   }else if(phase==2){
    if(!RepairJobs.isBusy(q)&&RepairJobs.isBusy(p))smallFirst=true;
    placed=(int)large.getWires().stream().filter(pos->f.level.getBlockState(pos).is(ModBlocks.BASIC_WIRE.get())).count();
    if(placed>=6&&smallFirst){p.getAbilities().mayBuild=false;phase=3;}
   }else if(phase==3){if(RepairJobs.isBusy(p)){f.later(this);return;}
    int after=(int)large.getWires().stream().filter(pos->f.level.getBlockState(pos).is(ModBlocks.BASIC_WIRE.get())).count();
    h.assertTrue(after==placed&&after>0&&after<60,"permission loss stops later writes without undoing completed placements");
    h.assertTrue(metric("repair.cancelledJobs")==1&&metric("repair.completedJobs")==1,"one cancelled and one complete job");
    p.getAbilities().mayBuild=true;NetworkOperations.forget(p);
    h.assertTrue(f.repair(p,large).success(),"retry after partial cancellation");phase=4;
   }else{if(RepairJobs.isBusy(p)||large.isIntegrityPending()||large.isDamaged()){f.later(this);return;}
    h.assertTrue(smallFirst&&small.getEffectiveSignal()==15,"small job progresses alongside large one");
    f.command("ccperf stop");f.command("ccperf export");f.close();h.succeed();return;
   }f.later(this);
  }catch(Exception e){f.close();throw new IllegalStateException("repair phase "+phase,e);}}}.run();
 }
 @GameTest(template="empty",batch="pending_cancelled_event",timeoutTicks=300)
 public static void harmlessPendingAndAudit(GameTestHelper h){
  var f=new Fixture(h);f.limits();var n=f.network(f.level,new BlockPos(224,150,224),1);var wire=n.getWires().iterator().next();
  new Runnable(){int phase,start;public void run(){try{
   if(phase==0){if(n.getEffectiveSignal()!=15){f.later(this);return;}
    f.command("ccperf reset");f.command("ccperf start");
    NetworkIntegrityManager.scheduleCheck(f.level,wire);NetworkIntegrityManager.scheduleCheck(f.level,wire);
    h.assertTrue(n.getEffectiveSignal()==0&&n.isIntegrityPending()&&!n.isDamaged(),"unconfirmed event is LOW but not DAMAGED");start=f.level.getServer().getTickCount();phase=1;
   }else{if(n.isIntegrityPending()||n.getEffectiveSignal()!=15){f.later(this);return;}
    h.assertTrue(!n.isDamaged()&&metric("pending.uniqueEnqueued")==1&&metric("pending.deduplicated")==1,"harmless duplicate events restore without false damage");
    h.assertTrue(metric("audit.elements")<=3L*(f.level.getServer().getTickCount()-start+1),"small network audited at most once per element per tick");
    f.command("ccperf stop");f.command("ccperf export");f.close();h.succeed();return;
   }f.later(this);
  }catch(Exception e){f.close();throw new IllegalStateException(e);}}}.run();
 }
 @GameTest(template="empty",batch="pending_snapshot",timeoutTicks=100)
 public static void immutableDamageTargets(GameTestHelper h){
  var n=new CompiledNetwork(1,"snapshot",0,"minecraft:overworld",Set.of(new BlockPos(0,100,0)),Set.of(),Set.of());
  var random=new Random(808);var expected=new TreeMap<Integer,BrokenCircuitElement>();
  for(int i=0;i<4000;i++){int id=random.nextInt(1000)+1;if(random.nextBoolean()){var b=new BrokenCircuitElement(id,"minecraft:air",i);if(n.markBroken(b))expected.put(id,b);}else{n.markRepaired(id);expected.remove(id);}}
  var snapshot=n.getBrokenElements();var before=new ArrayList<>(expected.values());
  h.assertTrue(new ArrayList<>(snapshot).equals(before),"persistent map matches reference AVL contents");
  for(int i=1;i<=1000;i++){n.markRepaired(i);n.markBroken(new BrokenCircuitElement(i,"minecraft:stone",5000));}
  h.assertTrue(new ArrayList<>(snapshot).equals(before)&&n.getBrokenElements().size()==1000,"captured target set survives arbitrary later damage mutations");
  h.succeed();
 }

 @GameTest(template="empty",batch="pending_park_restart",timeoutTicks=400)
 public static void pendingSurvivesUnloadAndReload(GameTestHelper h){
  var f=new Fixture(h);f.limits();var initial=f.network(f.level,new BlockPos(288,150,288),1);
  var wire=initial.getWires().iterator().next();
  new Runnable(){int phase,parkedAt;long reads;CompiledNetwork n=initial;public void run(){try{
   if(phase==0){if(n.getEffectiveSignal()!=15){f.later(this);return;}
    f.command("ccperf reset");f.command("ccperf start");
    NetworkIntegrityManager.scheduleCheck(f.level,wire);NetworkRuntime.chunkChanged(f.level,RuntimeSignalReader.chunk(wire),false);
    h.assertTrue(n.isIntegrityPending()&&n.getEffectiveSignal()==0,"invalidated unloaded wire cannot emit HIGH");phase=1;
   }else if(phase==1){if(metric("pending.unavailable")==0){f.later(this);return;}
    reads=metric("integrity.recheckElements");parkedAt=f.level.getServer().getTickCount();phase=2;
   }else if(phase==2){if(f.level.getServer().getTickCount()-parkedAt<6){f.later(this);return;}
    h.assertTrue(reads==metric("integrity.recheckElements")&&!n.isDamaged(),"unavailable work parks without retries or false AIR");
    var saved=n.save();h.assertTrue(saved.getBoolean("integrityUnverified"),"unverified state persists");
    var replacement=CompiledNetwork.load(saved);f.data.replaceNetwork(replacement);n=replacement;phase=3;
   }else if(phase==3){if(metric("integrity.parkedPasses")==0){f.later(this);return;}
    h.assertTrue(n.isIntegrityPending()&&n.getEffectiveSignal()==0&&!n.isDamaged(),"saved unverified wire stays LOW after data reload");
    f.set(f.level,wire,Blocks.AIR.defaultBlockState());NetworkRuntime.chunkChanged(f.level,RuntimeSignalReader.chunk(wire),true);phase=4;
   }else if(phase==4){if(n.isIntegrityPending()){f.later(this);return;}
    h.assertTrue(n.getBrokenElements().size()==1&&n.getEffectiveSignal()==0,"load wakes recheck and detects real damage");
    f.set(f.level,wire,ModBlocks.BASIC_WIRE.get().defaultBlockState());phase=5;
   }else if(phase==5){if(n.isIntegrityPending()||n.getEffectiveSignal()!=15){f.later(this);return;}
    NetworkIntegrityManager.scheduleCheck(f.level,wire);var old=n;f.data.removeNetwork(n.getId());
    n=new CompiledNetwork(old.getId(),"reused id",0,old.getDimension(),old.getElements());f.data.addNetwork(n);
    h.assertTrue(!n.isIntegrityPending(),"old point does not transfer to reused ID");phase=6;
   }else{if(n.getEffectiveSignal()!=15){f.later(this);return;}
    h.assertTrue(!n.isDamaged()&&f.data.findNetworkContaining(f.level,wire)==n,"stale cleanup preserves replacement membership");
    f.command("ccperf stop");f.command("ccperf export");f.close();h.succeed();return;
   }f.later(this);
  }catch(Exception e){f.close();throw new IllegalStateException("park/reload phase "+phase,e);}}}.run();
 }
 @GameTest(template="empty",batch="pending_repair_outcomes",timeoutTicks=400)
 public static void repairOutcomesAndAdmissionLimits(GameTestHelper h){
  var f=new Fixture(h);f.limits();var base=new BlockPos(352,150,352);f.load(f.level,base);
  var positions=new ArrayList<BlockPos>();var elements=new ArrayList<CompiledCircuitElement>();
  for(int i=0;i<8;i++){
   var pos=i==3?new BlockPos(15000000,150,15000000):base.offset(i,0,0);positions.add(pos);
   String expected=i==5?"minecraft:stone":i==6?"missing:invalid_block":"compiledcircuits:basic_wire";
   elements.add(new CompiledCircuitElement(i+1,pos,CircuitElementType.WIRE,expected));
   if(i!=3){f.load(f.level,pos);f.set(f.level,pos,i==1?Blocks.STONE.defaultBlockState():i==4||i==7?ModBlocks.BASIC_WIRE.get().defaultBlockState():Blocks.AIR.defaultBlockState());}
  }
  var n=new CompiledNetwork(1,"outcomes",0,f.level.dimension().location().toString(),elements);f.data.addNetwork(n);
  for(int i=1;i<=7;i++)n.markBroken(new BrokenCircuitElement(i,"minecraft:air",0));
  int maxJobs=ServerConfig.MAX_REPAIR_JOBS.get(),maxTargets=ServerConfig.MAX_REPAIR_TARGETS.get();
  java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> deny=event->{if(event.getPos().equals(positions.get(2)))event.setCanceled(true);};
  net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,deny);
  Runnable cleanup=()->{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);ServerConfig.MAX_REPAIR_JOBS.set(maxJobs);ServerConfig.MAX_REPAIR_TARGETS.set(maxTargets);f.close();};
  try{
   var p=f.player();f.command("ccperf reset");f.command("ccperf start");
   ServerConfig.MAX_REPAIR_JOBS.set(1);ServerConfig.MAX_REPAIR_TARGETS.set(6);
   h.assertTrue(f.repair(p,n).code()==NetworkOperations.Code.BUSY&&!RepairJobs.isBusy(p),"target memory cap rejects entire job");
   var invalid=NetworkOperations.execute(p.createCommandSourceStack(),NetworkOperations.Action.REPAIR,List.of(1,99999),0,"");
   h.assertTrue(invalid.code()==NetworkOperations.Code.NOT_FOUND&&!RepairJobs.isBusy(p),"invalid last ID rejects all targets before admission");
   ServerConfig.MAX_REPAIR_TARGETS.set(7);h.assertTrue(f.repair(p,n).code()==NetworkOperations.Code.QUEUED,"exact target and job limits accepted");
   for(int i=0;i<24;i++){var code=f.repair(p,n).code();h.assertTrue(code==NetworkOperations.Code.BUSY||code==NetworkOperations.Code.RATE_LIMITED,"repeated requests cannot multiply jobs");}
   h.assertTrue(f.repair(f.player(),n).code()==NetworkOperations.Code.BUSY,"global job limit rejects another actor");
   f.set(f.level,positions.get(7),Blocks.AIR.defaultBlockState());
   new Runnable(){public void run(){try{
    if(RepairJobs.isBusy(p)){f.later(this);return;}
    for(var result:List.of("PLACED","OCCUPIED","PROTECTED","UNLOADED","CORRECT","UNSUPPORTED","INVALID"))h.assertTrue(metric("repair.result."+result)==1,"exact repair outcome "+result);
    h.assertTrue(metric("repair.elements")==7&&metric("repair.remainingAtCompletion")==0&&metric("repair.jobsPeak")==1,"captured set and honest completion counters");
    h.assertTrue(f.level.getBlockState(positions.get(7)).isAir()&&!f.level.hasChunkAt(positions.get(3)),"new damage excluded; unavailable target never force-loaded");
    f.command("ccperf stop");f.command("ccperf export");cleanup.run();h.succeed();
   }catch(Exception e){cleanup.run();throw new IllegalStateException(e);}}}.run();
  }catch(Exception e){cleanup.run();throw new IllegalStateException(e);}
 }
 @GameTest(template="empty",batch="pending_timeout_stop",timeoutTicks=400)
 public static void repairTimeoutAndStop(GameTestHelper h){
  var f=new Fixture(h);f.limits();var n=f.network(f.level,new BlockPos(416,150,416),60);var p=f.player();
  int lifetime=ServerConfig.REPAIR_LIFETIME_TICKS.get();ServerConfig.REPAIR_LIFETIME_TICKS.set(20);ServerConfig.REPAIR_WORK.set(1);
  Runnable cleanup=()->{ServerConfig.REPAIR_LIFETIME_TICKS.set(lifetime);f.close();};
  new Runnable(){int phase,placedAtStop,tick;public void run(){try{
   if(phase==0){if(n.getEffectiveSignal()!=15){f.later(this);return;}for(var pos:n.getWires())f.set(f.level,pos,Blocks.AIR.defaultBlockState());phase=1;
   }else if(phase==1){if(n.isIntegrityPending()){f.later(this);return;}
    f.command("ccperf reset");f.command("ccperf start");h.assertTrue(f.repair(p,n).success(),"timeout fixture queued");phase=2;
   }else if(phase==2){if(RepairJobs.isBusy(p)){f.later(this);return;}
    h.assertTrue(metric("repair.cancelledJobs")==1&&metric("repair.remainingAtCompletion")>0&&metric("repair.elements")<=21,"tick lifetime expires before all fixed targets");
    NetworkOperations.forget(p);h.assertTrue(f.repair(p,n).success(),"new job after timeout");
    RepairJobs.stop(f.level.getServer());h.assertTrue(!RepairJobs.isBusy(p),"server stop clears actor and network locks immediately");
    placedAtStop=(int)n.getWires().stream().filter(pos->f.level.getBlockState(pos).is(ModBlocks.BASIC_WIRE.get())).count();tick=f.level.getServer().getTickCount();phase=3;
   }else{if(f.level.getServer().getTickCount()-tick<5){f.later(this);return;}
    h.assertTrue(placedAtStop==n.getWires().stream().filter(pos->f.level.getBlockState(pos).is(ModBlocks.BASIC_WIRE.get())).count(),"stopped jobs never resume writes");
    f.command("ccperf stop");f.command("ccperf export");cleanup.run();h.succeed();return;
   }f.later(this);
  }catch(Exception e){cleanup.run();throw new IllegalStateException(e);}}}.run();
 }
 @GameTest(template="empty",batch="pending_large_decompile",timeoutTicks=900)
 public static void maximumDecompileIsAtomic(GameTestHelper h){
  var f=new Fixture(h);var positions=new LinkedHashSet<BlockPos>();var base=new BlockPos(14000000,150,14000000);
  for(int i=0;i<50000;i++)positions.add(base.offset(i%256,0,i/256));
  var n=new CompiledNetwork(1,"maximum decompile",0,f.level.dimension().location().toString(),Set.of(),Set.of(),positions);f.data.addNetwork(n);
  var p=f.player();f.command("ccperf reset");f.command("ccperf start");
  long started=System.nanoTime();var result=NetworkOperations.execute(p.createCommandSourceStack(),NetworkOperations.Action.DECOMPILE,List.of(1),0,"");long elapsed=System.nanoTime()-started;
  h.assertTrue(result.success()&&f.data.getNetwork(1)==null,"maximum request removes network atomically");
  for(var pos:positions)h.assertTrue(f.data.findNetworkContaining(f.level,pos)==null,"all retired claims invisible immediately");
  var replacement=new CompiledNetwork(1,"reused maximum ID",0,n.getDimension(),Set.of(),Set.of(),Set.of(base));f.data.addNetwork(replacement);
  System.out.println("PENDING_DECOMPILE_50000: service operation nanos="+elapsed);
  new Runnable(){int phase;public void run(){try{
   if(((Number)field(field(f.data,"runtime"),"retiredWork")).intValue()!=0){f.later(this);return;}
   if(phase==0){
    h.assertTrue(f.data.findNetworkContaining(f.level,base)==replacement,"old retirement cannot erase new claim");
    f.data.removeNetwork(replacement.getId());var ids=new ArrayList<Integer>();
    for(int i=0;i<1024;i++){
     var wires=new HashSet<BlockPos>();for(int j=0;j<48;j++)wires.add(base.offset(j,2,i));
     int id=i+2;f.data.addNetwork(new CompiledNetwork(id,"bulk metadata",0,n.getDimension(),wires,Set.of(),Set.of()));ids.add(id);
    }
    long began=System.nanoTime();var bulk=NetworkOperations.execute(p.createCommandSourceStack(),NetworkOperations.Action.DECOMPILE,ids,0,"");
    System.out.println("PENDING_DECOMPILE_1024_NETWORKS: service operation nanos="+(System.nanoTime()-began));
    h.assertTrue(bulk.success()&&f.data.getNetworks().isEmpty(),"maximum ID batch removes all 49152 members atomically");phase=1;f.later(this);return;
   }
   h.assertTrue(!f.level.hasChunkAt(base)&&metric("work.RUNTIME.tickPeak")<=NetworkRuntime.WORK_PER_TICK,"retirement honors quota and never loads outputs");
   f.command("ccperf stop");f.command("ccperf export");f.close();h.succeed();
  }catch(Exception e){f.close();throw new IllegalStateException(e);}}}.run();
 }

 @GameTest(template="empty",batch="pending_many_outputs",timeoutTicks=900)
 public static void manyOutputsAndDimensionInvalidation(GameTestHelper h){
  var f=new Fixture(h);var base=new BlockPos(480,150,480);var outputs=new LinkedHashSet<BlockPos>();
  f.load(f.level,base);f.set(f.level,base,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());f.set(f.level,base.below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
  for(int i=0;i<2304;i++){
   var pos=base.offset(4+(i%24)*2,(i/576)*2,(i/24%24)*2);f.load(f.level,pos);
   f.set(f.level,pos,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());outputs.add(pos);
  }
  var lamp=outputs.iterator().next().below();f.set(f.level,lamp,Blocks.REDSTONE_LAMP.defaultBlockState());
  var n=new CompiledNetwork(1,"many outputs",0,f.level.dimension().location().toString(),Set.of(),Set.of(base),outputs);f.data.addNetwork(n);
  new Runnable(){int phase,started;public void run(){try{
   boolean idle=((Collection<?>)field(field(f.data,"runtime"),"queue")).isEmpty();
   if(phase==0){if(n.getEffectiveSignal()!=15||!idle){f.later(this);return;}
    h.assertTrue(f.level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT),"initial output notification lights lamp");
    f.command("ccperf reset");f.command("ccperf start");
    // The large-event branch must ask only for size, never walk or retain the event list.
    var huge=new AbstractList<BlockPos>(){public int size(){return 1000000;}public BlockPos get(int i){throw new AssertionError("large event list traversed");}};
    var explosion=new net.minecraft.world.level.Explosion(f.level,null,base.getX(),base.getY(),base.getZ(),1,false,net.minecraft.world.level.Explosion.BlockInteraction.KEEP);
    explosion.getToBlow().addAll(List.of(base));
    var event=new net.minecraftforge.event.level.ExplosionEvent.Detonate(f.level,explosion,new ArrayList<>()){
     @Override public List<BlockPos> getAffectedBlocks(){return huge;}
    };
    com.example.compiledcircuits.event.NetworkIntegrityEvents.onExplosion(event);
    h.assertTrue(n.isIntegrityPending()&&n.getEffectiveSignal()==0&&!n.isDamaged(),"large harmless event gates immediately without fake damage");
    h.assertTrue(metric("runtime.outputNotifications")==0&&metric("integrity.dimensionInvalidations")==1,"invalidation does no output callbacks in event");
    f.set(f.level,base,Blocks.AIR.defaultBlockState());started=f.level.getServer().getTickCount();phase=1;
   }else if(phase==1){if(n.isIntegrityPending()||!idle||f.level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT)){f.later(this);return;}
    h.assertTrue(n.isDamaged()&&n.getEffectiveSignal()==0,"confirmed damage remains LOW");
    h.assertTrue(metric("runtime.outputNotifications")>=outputs.size()&&metric("work.RUNTIME.tickPeak")<=2048,"every output eventually notified under global runtime quota");
    h.assertTrue(f.level.getServer().getTickCount()>started,"neighbor delivery spans server ticks");
    System.out.println("PENDING_OUTPUTS_2304: LOW delivery ticks="+(f.level.getServer().getTickCount()-started));
    f.set(f.level,base,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());phase=2;
   }else{if(n.getEffectiveSignal()!=15||!idle||!f.level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT)){f.later(this);return;}
    h.assertTrue(!n.isDamaged(),"repair and pending confirmation restore output surroundings");
    f.command("ccperf stop");f.command("ccperf export");f.close();h.succeed();return;
   }f.later(this);
  }catch(Exception e){f.close();throw new IllegalStateException("many outputs phase "+phase,e);}}}.run();
 }

 @GameTest(template="empty",batch="pending_config",timeoutTicks=100)
 public static void configurationBounds(GameTestHelper h){
  var settings=List.of(ServerConfig.TOTAL_WORK,ServerConfig.TOTAL_MICROS,ServerConfig.INTEGRITY_MICROS,ServerConfig.POINT_WORK,
   ServerConfig.RECHECK_WORK,ServerConfig.REPAIR_WORK,ServerConfig.AUDIT_WORK,ServerConfig.MAX_PENDING,ServerConfig.MAX_REPAIR_JOBS,
   ServerConfig.MAX_REPAIR_TARGETS,ServerConfig.REPAIR_LIFETIME_TICKS,ServerConfig.REPAIR_LIFETIME_SECONDS);
  int[] minima={128,100,100,1,1,1,1,1,1,1,20,1};
  int[] maxima={65536,100000,20000,8192,8192,1024,4096,65536,64,1000000,72000,3600};
  for(int i=0;i<settings.size();i++){
   net.minecraftforge.common.ForgeConfigSpec.ValueSpec spec=ServerConfig.SPEC.getSpec().get(settings.get(i).getPath());
   h.assertTrue(spec.test(minima[i])&&spec.test(maxima[i])&&spec.test(spec.getDefault()),"valid budget bounds accepted");
   h.assertTrue(!spec.test(minima[i]-1)&&!spec.test(maxima[i]+1)&&!spec.test("unbounded"),"invalid budget values rejected");
  }h.succeed();
 }
 @GameTest(template="empty",batch="pending_actual_restart",timeoutTicks=300)
 public static void actualPendingRestart(GameTestHelper h){
  String phase=System.getenv("CC_RUNTIME_RESTART_PHASE");if(phase==null){h.succeed();return;}
  var f=new Fixture(h);var server=f.level.getServer();var storage=server.overworld().getDataStorage();
  var input=new BlockPos(16388,180,16388);var wire=input.east(2);var output=input.above(2);
  for(var pos:List.of(input,wire,output))f.load(f.level,pos);
  String key="compiledcircuits_pending_restart_fixture";
  if(phase.equals("write")){
   try{
    f.level.setBlock(input,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),3);f.level.setBlock(input.below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
    f.level.setBlock(wire,ModBlocks.BASIC_WIRE.get().defaultBlockState(),3);f.level.setBlock(output,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState(),3);
    var n=new CompiledNetwork(1,"pending restart",0,f.level.dimension().location().toString(),CompiledElementFactory.create(f.level,Set.of(wire),Set.of(input),Set.of(output)));
    f.data.addNetwork(n);n.setPowered(true);f.level.setBlock(wire,Blocks.AIR.defaultBlockState(),3);
    n.markBroken(new BrokenCircuitElement(n.getElementAt(wire).getId(),"minecraft:air",0));
    var p=f.player();h.assertTrue(f.repair(p,n).code()==NetworkOperations.Code.QUEUED,"unfinished repair exists before stop");
    h.assertTrue(n.isIntegrityPending(),"pending integrity exists before save");
    var frozen=NetworkSavedData.load(f.data.save(new CompoundTag()));frozen.setDirty();storage.set(key,frozen);server.saveEverything(true,true,true);
    System.out.println("PENDING_RESTART_WRITE: saved real missing wire, pending integrity and queued repair");
   }finally{f.close();}h.succeed();return;
  }
  var saved=storage.get(NetworkSavedData::load,key);h.assertTrue(saved!=null,"pending fixture saved by previous process");
  var before=saved.save(new CompoundTag());storage.set("compiledcircuits_networks",saved);var n=saved.getNetwork(1);var p=f.player();
  h.assertTrue(n.isIntegrityPending()&&n.getEffectiveSignal()==0&&!RepairJobs.isBusy(p),"restart keeps LOW without restoring stale repair jobs");
  new Runnable(){int step;public void run(){try{
   if(step==0){if(n.isIntegrityPending()){f.later(this);return;}
    h.assertTrue(n.isDamaged()&&f.level.getBlockState(wire).isAir()&&n.getEffectiveSignal()==0,"restart reconciles actual missing wire without resuming repair");
    h.assertTrue(f.repair(p,n).code()==NetworkOperations.Code.QUEUED,"fresh post-restart repair accepted");step=1;
   }else{if(RepairJobs.isBusy(p)||n.isIntegrityPending()||n.getEffectiveSignal()!=15){f.later(this);return;}
    h.assertTrue(!n.isDamaged(),"fresh repair recovers saved pending network");
    System.out.println("PENDING_RESTART_READ: actual second process confirmed damage, no stale repair, fresh repair restores HIGH");
    storage.set(key,NetworkSavedData.load(before));f.close();h.succeed();return;
   }f.later(this);
  }catch(Exception e){f.close();throw new IllegalStateException(e);}}}.run();
 }

 @GameTest(template="empty",batch="pending_minimum_budget",timeoutTicks=600)
 public static void minimumTotalBudgetStillProgresses(GameTestHelper h){
  var f=new Fixture(h);f.limits();ServerConfig.TOTAL_WORK.set(128);ServerConfig.AUDIT_WORK.set(128);var n=f.network(f.level,new BlockPos(608,150,608),24);var p=f.player();
  f.command("ccperf reset");f.command("ccperf start");
  new Runnable(){int phase;public void run(){try{
   if(phase==0){if(n.getEffectiveSignal()!=15){f.later(this);return;}for(var pos:n.getWires())f.set(f.level,pos,Blocks.AIR.defaultBlockState());phase=1;
   }else if(phase==1){if(n.isIntegrityPending()){f.later(this);return;}h.assertTrue(f.repair(p,n).success(),"repair admitted with minimum total budget");phase=2;
   }else{if(RepairJobs.isBusy(p)||n.getEffectiveSignal()!=15){f.later(this);return;}
    h.assertTrue(metric("work.total.tickPeak")<=128&&metric("work.AUDIT.units")>0&&metric("repair.result.PLACED")==24,"all work stays bounded and audit/runtime/repair progress at minimum global setting");
    f.command("ccperf stop");f.command("ccperf export");f.close();h.succeed();return;
   }f.later(this);
  }catch(Exception e){f.close();throw new IllegalStateException(e);}}}.run();
 }
}
