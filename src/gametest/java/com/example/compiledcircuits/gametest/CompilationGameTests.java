package com.example.compiledcircuits.gametest;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.registry.ModBlocks;
import com.example.compiledcircuits.block.EndpointBlock;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class CompilationGameTests {
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final ServerLevel level;
        final NetworkSavedData original, data = new NetworkSavedData();
        final Map<BlockPos, BlockState> old = new LinkedHashMap<>();
        final Set<Long> forced = new HashSet<>();
        final Set<ChunkPos> exact = new HashSet<>();
        final List<ServerPlayer> players = new ArrayList<>();
        Fixture(GameTestHelper h) {
            this.h=h; level=h.getLevel(); original=NetworkSavedData.get(level.getServer());
            level.getServer().overworld().getDataStorage().set("compiledcircuits_networks", data);
        }
        void load(BlockPos pos) {
            long c=ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4);
            if (!level.getForcedChunks().contains(c)) { level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true); forced.add(c); }
            level.getChunk(pos); // fixture only
        }
        void loadExact(BlockPos pos) {
            var chunk=new ChunkPos(pos);exact.add(chunk);
            level.getChunkSource().addRegionTicket(TicketType.FORCED,chunk,0,chunk);
            level.getChunk(pos); // level 33: FULL center only; no FULL neighbor created by fixture
        }
        void set(BlockPos pos, BlockState state) {
            old.putIfAbsent(pos,level.getBlockState(pos));
            level.getChunkAt(pos).setBlockState(pos,state,false); // exercises central write hook without neighbor noise
        }
        ServerPlayer player(BlockPos pos) {
            var p=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"scan-test"));
            p.getAbilities().mayBuild=true; NetworkSelectionData.set(p,pos); players.add(p); return p;
        }
        NetworkOperations.Result submit(ServerPlayer p) {
            return NetworkOperations.execute(p.createCommandSourceStack(),NetworkOperations.Action.COMPILE,List.of(),0,"scan-test");
        }
        void pair(BlockPos pos) {
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) load(pos.offset(x*16,0,z*16));
            set(pos,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
            set(pos.east(),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        }
        public void close() {
            CompilationJobs.stop(level.getServer());
            for(var n:new ArrayList<>(data.getNetworks())) data.removeNetwork(n.getId());
            old.forEach((pos,state)->level.getChunkAt(pos).setBlockState(pos,state,false));
            for(long c:forced) level.setChunkForced(ChunkPos.getX(c),ChunkPos.getZ(c),false);
            for(var c:exact)level.getChunkSource().removeRegionTicket(TicketType.FORCED,c,0,c);
            for(var p:players) NetworkOperations.forget(p);
            level.getServer().overworld().getDataStorage().set("compiledcircuits_networks",original);
            NetworkIntegrityManager.clearPending();
        }
        void later(Runnable run) { h.runAfterDelay(1,()->run.run()); }
    }
    private static Object field(Object object,String name) {
        try { var f=object.getClass().getDeclaredField(name); f.setAccessible(true);return f.get(object); }
        catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
    }
    private static Object job(ServerPlayer p) {
        try {
            var f=CompilationJobs.class.getDeclaredField("MANAGERS");f.setAccessible(true);
            var manager=((Map<?,?>)f.get(null)).get(p.getServer());
            return manager==null?null:((Map<?,?>)field(manager,"actors")).get(p);
        } catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
    }
    private static Map<?,?> metrics() {
        try {
            var f=PerformanceDiagnostics.class.getDeclaredField("active");f.setAccessible(true);
            return (Map<?,?>)field(f.get(null),"counters");
        } catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
    }
    private static long metric(String name) { var value=metrics().get(name); return value==null?0:((Number)value).longValue(); }
    private static void command(Fixture f,String command) {
        try {f.level.getServer().getCommands().getDispatcher().execute(command,f.level.getServer().createCommandSourceStack());}
        catch(Exception e) {throw new IllegalStateException(e);}
    }
    @GameTest(template="empty",batch="compilation_admission",timeoutTicks=100)
    public static void queueAndStartLimits(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var pos=new BlockPos(72,150,72);f.pair(pos);
            f.data.setDirty(false);
            for(int i=0;i<CompilationJobs.MAX_JOBS;i++) h.assertTrue(f.submit(f.player(pos)).code()==NetworkOperations.Code.QUEUED,"bounded queue slot accepted");
            h.assertTrue(f.submit(f.player(pos)).code()==NetworkOperations.Code.BUSY,"ninth job refused before allocation");
            CompilationJobs.stop(f.level.getServer());
            h.assertTrue(f.players.stream().noneMatch(CompilationJobs::isBusy) && f.data.getNetworks().isEmpty() && !f.data.isDirty() && f.data.getNextNetworkId()==1,"shutdown releases all jobs without data/ID changes");
            h.assertTrue(f.submit(f.player(new BlockPos(0,f.level.getMaxBuildHeight(),0))).code()==NetworkOperations.Code.INVALID_START,"outside height rejected before chunk access");
            h.assertTrue(f.submit(f.player(new BlockPos(30000001,100,0))).code()==NetworkOperations.Code.INVALID_START,"outside world border rejected explicitly");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="compilation_safety",timeoutTicks=700)
    public static void atomicJobsAndInvalidation(GameTestHelper h) {
        var f=new Fixture(h); var pos=new BlockPos(72,160,72); f.pair(pos);
        var p=f.player(pos); var q=f.player(pos);
        var baseline=f.data.save(new CompoundTag()); int next=f.data.getNextNetworkId(); f.data.setDirty(false);
        command(f,"ccperf reset");command(f,"ccperf start");
        h.assertTrue(f.submit(p).code()==NetworkOperations.Code.QUEUED,"first queued");
        h.assertTrue(f.submit(q).code()==NetworkOperations.Code.QUEUED,"overlapping job also scans");
        h.assertTrue(f.submit(p).code()==NetworkOperations.Code.BUSY,"one job per actor");
        h.assertTrue(f.data.getNetworks().isEmpty() && !f.data.isDirty(),"no early publication");
        new Runnable() {
            int phase=0; ServerPlayer actor=p; long failures; BlockState state;
            public void run() {
                try {
                    if(phase==0) {
                        if(CompilationJobs.isBusy(p)||CompilationJobs.isBusy(q)){f.later(this);return;}
                        h.assertTrue(f.data.getNetworks().size()==1 && f.data.getNextNetworkId()==next+1,"only one overlapping job commits/consumes ID");
                        var n=f.data.getNetworks().iterator().next();
                        h.assertTrue(n.getElements().size()==2 && n.getElementAt(pos.east()).resolveState().state().orElseThrow()==f.level.getBlockState(pos.east()),"roles and facing snapshot exact");
                        h.assertTrue(n.getElementAt(pos).getId()==1 && n.getElementAt(pos.east()).getId()==2,"deterministic IDs");
                        f.data.removeNetwork(n.getId());
                        // A larger cycle-filled graph keeps jobs alive across phases/ticks.
                        for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)f.load(pos.offset(x*16,0,z*16));
                        for(int x=0;x<24;x++) for(int y=0;y<10;y++) for(int z=0;z<24;z++) f.set(pos.offset(x,y,z),ModBlocks.BASIC_WIRE.get().defaultBlockState());
                        f.set(pos,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
                        f.set(pos.offset(23,0,23),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
                        phase=1; actor=f.player(pos); failures=metric("scan.result.CHANGED_DURING_SCAN");
                        h.assertTrue(f.submit(actor).success(),"change test queued");
                    } else if(phase==1) {
                        var j=job(actor);
                        if(j!=null && !((Set<?>)field(j,"watched")).isEmpty()) {
                            // Direct chunk write models every normal setBlock path, including commands/pistons.
                            state=f.level.getBlockState(pos);command(f,"setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" compiledcircuits:input_endpoint[facing=south]");phase=2;
                        }
                    } else if(phase==2) {
                        if(CompilationJobs.isBusy(actor)){f.later(this);return;}
                        h.assertTrue(f.data.getNetworks().isEmpty() && metric("scan.result.CHANGED_DURING_SCAN")==failures+1,"central state-write hook aborts");
                        f.set(pos,state); actor=f.player(pos);f.submit(actor);phase=3;
                    } else if(phase==3) {
                        var j=job(actor);
                        if(j!=null && field(j,"phase").toString().equals("PREPARING")) {
                            var admission=field(j,"admission");
                            if(((Number)field(admission,"staged")).intValue()==0){f.later(this);return;}
                            // Force the interruption point just after one hidden runtime bucket was indexed.
                            var prepared=field(admission,"preparedRuntime");
                            var index=prepared.getClass().getDeclaredMethod("indexNext");index.setAccessible(true);index.invoke(prepared);
                            var runtime=field(f.data,"runtime");var chunks=(Map<?,?>)field(runtime,"chunks");
                            Object key=chunks.keySet().iterator().next();
                            NetworkRuntime.chunkChanged(f.level,((Number)field(key,"pos")).longValue(),true);
                            h.assertTrue(f.data.findNetworkContaining(f.level,pos)==null,"staged membership invisible");
                            NetworkSelectionData.set(actor,pos);phase=4; // even reselecting same coordinates cancels
                        }
                    } else if(phase==4) {
                        if(CompilationJobs.isBusy(actor)){f.later(this);return;}
                        h.assertTrue(f.data.getNetworks().isEmpty() && f.data.getNextNetworkId()==next+1,"cancel rollback consumes no ID");
                        var runtime=field(f.data,"runtime");
                        h.assertTrue(((Map<?,?>)field(runtime,"chunks")).isEmpty() && ((Set<?>)field(runtime,"loading")).isEmpty(),"rollback removes hidden runtime index and load retries");
                        actor=f.player(pos);f.submit(actor);phase=5;
                    } else if(phase==5) {
                        var j=job(actor);
                        if(j!=null && !((Set<?>)field(j,"watched")).isEmpty()) {
                            NetworkRuntime.chunkChanged(f.level,RuntimeSignalReader.chunk(pos),false);
                            NetworkRuntime.chunkChanged(f.level,RuntimeSignalReader.chunk(pos),true);phase=6;
                        }
                    } else if(phase==6) {
                        if(CompilationJobs.isBusy(actor)){f.later(this);return;}
                        h.assertTrue(f.data.getNetworks().isEmpty(),"unload/reload cannot revive stale scan");
                        actor=f.player(pos);f.submit(actor);actor.getAbilities().mayBuild=false;phase=7;
                    } else if(phase==7) {
                        if(CompilationJobs.isBusy(actor)){f.later(this);return;}
                        h.assertTrue(metric("scan.result.FORBIDDEN")>0 && f.data.getNetworks().isEmpty(),"permission loss aborts");
                        actor=f.player(pos);f.submit(actor);
                        var j=job(actor);var age=j.getClass().getDeclaredField("startedNanos");age.setAccessible(true);
                        age.setLong(j,System.nanoTime()-CompilationJobs.MAX_LIFETIME_NANOS-1);phase=8;
                    } else if(phase==8) {
                        if(CompilationJobs.isBusy(actor)){f.later(this);return;}
                        h.assertTrue(metric("scan.result.TIMEOUT")>0,"expired job times out");
                        actor=f.player(pos);f.submit(actor);phase=9;
                    } else if(phase==9) {
                        var j=job(actor);
                        if(j!=null && field(j,"phase").toString().equals("PREPARING")) {
                            failures=metric("scan.result.CHANGED_DURING_SCAN");
                            f.set(pos.west(),ModBlocks.BASIC_WIRE.get().defaultBlockState());phase=10;
                        }
                    } else if(phase==10) {
                        if(CompilationJobs.isBusy(actor)){f.later(this);return;}
                        h.assertTrue(metric("scan.result.CHANGED_DURING_SCAN")==failures+1 && f.data.getNetworks().isEmpty(),"new branch on already visited boundary aborts and rolls back");
                        f.set(pos.west(),Blocks.AIR.defaultBlockState());actor=f.player(pos);f.submit(actor);phase=11;
                    } else if(phase==11) {
                        var j=job(actor);
                        if(j!=null && !((Set<?>)field(j,"watched")).isEmpty()) {
                            NetworkOperations.forget(actor);phase=12;
                        }
                    } else if(phase==12) {
                        if(CompilationJobs.isBusy(actor)){f.later(this);return;}
                        h.assertTrue(f.data.getNetworks().isEmpty(),"logout/session cleanup cancels job");
                        actor=f.player(pos);f.submit(actor);phase=13;
                    } else {
                        if(CompilationJobs.isBusy(actor)){f.later(this);return;}
                        h.assertTrue(f.data.getNetworks().size()==1 && f.data.getNetworks().iterator().next().getElements().size()==5760,"rollback releases claims and retry succeeds over cycles");
                        h.assertTrue(metric("scan.job.tickWork")<=CompilationJobs.WORK_PER_JOB_TICK && metric("scan.tickWork")<=CompilationJobs.WORK_PER_TICK,"shared and per-job caps");
                        command(f,"ccperf stop");command(f,"ccperf export");command(f,"ccperf reset");f.close();h.succeed();return;
                    }
                    f.later(this);
                }catch(Exception e){command(f,"ccperf reset");f.close();throw new IllegalStateException("Compilation safety phase "+phase,e);}
            }
        }.run();
    }
    @GameTest(template="empty",batch="compilation_unloaded",timeoutTicks=180)
    public static void unloadedDoesNotForceChunks(GameTestHelper h) {
        var f=new Fixture(h);
        var missing=new BlockPos(2000000,120,2000000);var p=f.player(missing);
        long chunk=RuntimeSignalReader.chunk(missing);var forbidden=Set.of(chunk,ChunkPos.asLong(12501,12500));
        long[] unexpectedLoads={0};
        java.util.function.Consumer<net.minecraftforge.event.level.ChunkEvent.Load> loadHook=e->{
            if(e.getLevel()==f.level && forbidden.contains(e.getChunk().getPos().toLong()))unexpectedLoads[0]++;
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,net.minecraftforge.event.level.ChunkEvent.Load.class,loadHook);
        var forced=new HashSet<>(f.level.getForcedChunks());
        h.assertTrue(f.level.getChunkSource().getChunkNow(missing.getX()>>4,missing.getZ()>>4)==null,"fixture starts absent");
        h.assertTrue(f.submit(p).code()==NetworkOperations.Code.INCOMPLETE_UNLOADED,"unloaded start rejected");
        h.assertTrue(f.level.getChunkSource().getChunkNow(missing.getX()>>4,missing.getZ()>>4)==null && forced.equals(f.level.getForcedChunks()),"no chunk/ticket created for start");
        var edge=new BlockPos(200015,120,200008);f.loadExact(edge);
        f.set(edge,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());f.set(edge.west(),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        p=f.player(edge);var actor=p;
        h.assertTrue(f.level.getChunkSource().getChunkNow((edge.getX()>>4)+1,edge.getZ()>>4)==null,"potential neighbor absent");
        var afterSetup=new HashSet<>(f.level.getForcedChunks());
        h.assertTrue(f.submit(p).success(),"edge request queued");
        new Runnable(){public void run(){
            if(CompilationJobs.isBusy(actor)){f.later(this);return;}
            try {
                h.assertTrue(f.data.getNetworks().isEmpty() && f.data.getNextNetworkId()==1,"incomplete graph not published");
                h.assertTrue(f.level.getChunkSource().getChunkNow((edge.getX()>>4)+1,edge.getZ()>>4)==null
                        && afterSetup.equals(f.level.getForcedChunks()),"neighbor remains unloaded; no forced ticket added");
                h.assertTrue(unexpectedLoads[0]==0,"load hook recorded zero loads for unavailable positions");
                System.out.println("SCAN_NO_FORCE: unavailable start/neighbor load events="+unexpectedLoads[0]+", forced tickets unchanged");
            }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(loadHook);f.close();}h.succeed();
        }}.run();
    }
    @GameTest(template="empty",batch="compilation_directions",timeoutTicks=180)
    public static void directionsAndBuildHeight(GameTestHelper h) {
        var f=new Fixture(h);var bottom=new BlockPos(300015,f.level.getMinBuildHeight(),300015);
        var top=new BlockPos(300015,f.level.getMaxBuildHeight()-2,300015);f.loadExact(bottom);
        f.set(bottom,ScanTestBlocks.input.defaultBlockState());f.set(bottom.above(),ScanTestBlocks.output.defaultBlockState());
        f.set(top,ScanTestBlocks.input.defaultBlockState());f.set(top.above(),ScanTestBlocks.output.defaultBlockState());
        var p=f.player(bottom);var q=f.player(top);f.submit(p);f.submit(q);
        new Runnable(){int stage;ServerPlayer actor;public void run(){
            try {
                if(stage==0) {
                    if(CompilationJobs.isBusy(p)||CompilationJobs.isBusy(q)){f.later(this);return;}
                    h.assertTrue(f.data.getNetworks().size()==2,"height boundaries and disallowed horizontal neighbors compile");
                    for(var n:new ArrayList<>(f.data.getNetworks())) {
                        h.assertTrue(n.getInputs().size()==1 && n.getOutputs().size()==1 && n.getWires().isEmpty(),"same roles as reference pair");
                        f.data.removeNetwork(n.getId());
                    }
                    h.assertTrue(f.level.getChunkSource().getChunkNow((bottom.getX()>>4)+1,bottom.getZ()>>4)==null,"disallowed neighbor was not requested");
                    f.set(bottom.above(),ScanTestBlocks.output.defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
                    actor=f.player(bottom);f.submit(actor);stage=1;f.later(this);return;
                }
                if(CompilationJobs.isBusy(actor)){f.later(this);return;}
                h.assertTrue(f.data.getNetworks().isEmpty(),"neighbor refusal prevents one-way connection");
                f.close();h.succeed();
            }catch(Exception e){f.close();throw new IllegalStateException(e);}
        }}.run();
    }
    @GameTest(template="empty",batch="compilation_maximum",timeoutTicks=2000)
    public static void maximumAndOversize(GameTestHelper h) {
        var f=new Fixture(h);var origin=new BlockPos(64,180,64);
        for(int x=48;x<=128;x+=16)for(int z=48;z<=128;z+=16)f.load(new BlockPos(x,180,z));
        for(int x=0;x<50;x++)for(int y=0;y<20;y++)for(int z=0;z<50;z++)f.set(origin.offset(x,y,z),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        f.set(origin,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        f.set(origin.offset(49,19,49),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        command(f,"ccperf reset");command(f,"ccperf start");
        var smallPos=new BlockPos(122,180,122);f.pair(smallPos);
        var p=f.player(origin);var q=f.player(origin);var small=f.player(smallPos);
        try { h.assertTrue(f.level.getServer().getCommands().getDispatcher().execute("circuit compile",p.createCommandSourceStack())==1,"command queues maximum job"); }
        catch(Exception e){throw new IllegalStateException(e);}
        h.assertTrue(NetworkCompiler.compileSelected(q,"named maximum"),"named GUI service queues maximum job");
        f.submit(small);
        new Runnable(){int stage;boolean smallFinishedFirst;ServerPlayer actor=p;public void run(){
            try {
                if(stage==0 && !CompilationJobs.isBusy(small) && (CompilationJobs.isBusy(p)||CompilationJobs.isBusy(q))) smallFinishedFirst=true;
                if(CompilationJobs.isBusy(actor)||(stage==0 && (CompilationJobs.isBusy(q)||CompilationJobs.isBusy(small)))){
                    var pending=f.data.findNetworkContaining(f.level,origin);
                    if(pending!=null) h.assertTrue(pending.getElements().size()==50000,"no partial maximum membership");
                    f.later(this);return;
                }
                if(stage==0){
                    h.assertTrue(f.data.getNetworks().size()==2 && smallFinishedFirst,"small job finishes before competing maximum jobs");
                    var n=f.data.findNetworkContaining(f.level,origin);h.assertTrue(n.getElements().size()==50000,"inclusive element limit");
                    h.assertTrue(!n.isDamaged(),"maximum job snapshots remain healthy during initialization");
                    if(!n.getRuntimeStatus().equals("READY")){f.later(this);return;}
                    h.assertTrue(metric("runtime.workPerTickPeak")<=NetworkRuntime.WORK_PER_TICK,"post-commit initialization stays budgeted");
                    h.assertTrue(metric("scan.queuePops")==100002 && metric("scan.visited")==100002,"cycle frontier deduplicated on enqueue");
                    h.assertTrue(metric("scan.job.tickWork")<=2048 && metric("scan.tickWork")<=8192
                            && metric("scan.retainedElements.peak")<=CompilationJobs.MAX_TOTAL_ELEMENTS,"maximum jobs bounded");
                    for(var network:new ArrayList<>(f.data.getNetworks()))f.data.removeNetwork(network.getId()); f.set(origin.west(),ModBlocks.BASIC_WIRE.get().defaultBlockState());
                    actor=f.player(origin); f.submit(actor);stage=1;f.later(this);return;
                }
                h.assertTrue(f.data.getNetworks().isEmpty() && f.data.getNextNetworkId()==3 && metric("scan.result.TOO_LARGE")==1,"50001 rejected without consuming ID");
                command(f,"ccperf stop");command(f,"ccperf export");command(f,"ccperf reset");f.close();h.succeed();
            }catch(Exception e){command(f,"ccperf reset");f.close();throw new IllegalStateException("Maximum compilation phase "+stage,e);}
        }}.run();
    }
}
