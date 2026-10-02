package com.example.compiledcircuits.gametest;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class RuntimeLifecycleGameTests {
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final NetworkSavedData original, data = new NetworkSavedData();
        final Map<BlockPos, BlockState> old = new LinkedHashMap<>();
        final Set<Long> forced = new HashSet<>();
        final BlockPos input, output, power, lamp, other, wire;
        Fixture(GameTestHelper helper) {
            level = helper.getLevel(); original = NetworkSavedData.get(level.getServer());
            level.getServer().overworld().getDataStorage().set("compiledcircuits_networks", data);
            input = helper.absolutePos(new BlockPos(3, 4, 3)); output = input.offset(4, 0, 0);
            power = input.below(); lamp = output.above(); other = input.offset(32, 0, 0); wire = input.offset(64, 0, 0);
            // Test setup explicitly loads its chunks; production runtime must never do so.
            for (BlockPos pos : List.of(input, other, output, wire)) for (long c : RuntimeSignalReader.dependencyChunks(pos))
                level.getChunk(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c));
            set(input, ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
            set(output, ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
            set(power, Blocks.AIR.defaultBlockState()); set(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
            set(other, ModBlocks.INPUT_ENDPOINT.get().defaultBlockState()); set(other.below(), Blocks.AIR.defaultBlockState());
            set(wire, ModBlocks.BASIC_WIRE.get().defaultBlockState());
        }
        void set(BlockPos pos, BlockState state) { old.putIfAbsent(pos, level.getBlockState(pos)); level.setBlock(pos, state, 3); }
        CompiledNetwork create(boolean twoInputs) {
            var n = new CompiledNetwork(data.getNextNetworkId(), "runtime", 0, level.dimension().location().toString(),
                    CompiledElementFactory.create(level, Set.of(wire), twoInputs ? Set.of(input, other) : Set.of(input), Set.of(output)));
            data.addNetwork(n); return n;
        }
        void tick() { NetworkRuntime.tick(level.getServer()); }
        int signal() { return level.getBlockState(output).getSignal(level, output, Direction.UP); }
        void chunk(BlockPos pos, boolean loaded) { NetworkRuntime.chunkChanged(level, RuntimeSignalReader.chunk(pos), loaded); }
        void keepLoaded(long chunk) {
            if (!level.getForcedChunks().contains(chunk)) {
                level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk),net.minecraft.world.level.ChunkPos.getZ(chunk),true);
                forced.add(chunk);
            }
        }
        public void close() {
            for (var n : new ArrayList<>(data.getNetworks())) data.removeNetwork(n.getId());
            old.forEach((pos,state) -> level.setBlock(pos,state,3));
            for(long c:forced) level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);
            level.getServer().overworld().getDataStorage().set("compiledcircuits_networks", original);
            NetworkIntegrityManager.clearPending();
        }
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void savedSignalIsUntrusted(GameTestHelper helper) throws Exception {
        try (var f = new Fixture(helper)) {
            var n = f.create(false); n.setPowered(true);
            // Real compressed disk round trip; a separate two-process scenario tests server restart.
            var file = java.nio.file.Files.createTempFile("compiledcircuits-runtime-", ".nbt").toFile();
            try {
                NbtIo.writeCompressed(n.save(), file);
                var loaded = CompiledNetwork.load(NbtIo.readCompressed(file));
                f.data.replaceNetwork(loaded);
                helper.assertTrue(f.signal() == 0, "saved HIGH cannot emit before reconciliation");
                f.tick(); helper.assertTrue(f.signal() == 0 && !loaded.isPowered(), "actual LOW replaces saved HIGH");
                f.set(f.power, Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.assertTrue(f.signal() == 15, "ordinary input change settles within the same tick");
                loaded.setPowered(false); var raw = loaded.save();
                f.data.replaceNetwork(CompiledNetwork.load(raw));
                helper.assertTrue(f.signal() == 0, "saved LOW still initializes");
                f.tick(); helper.assertTrue(f.signal() == 15, "actual HIGH recovers without lever toggle");
                helper.assertTrue(f.level.getBlockState(f.lamp).getValue(RedstoneLampBlock.LIT), "output notifies lamp");
            } finally { java.nio.file.Files.deleteIfExists(file.toPath()); }
        }
        helper.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void chunkTransitions(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            f.set(f.power, Blocks.REDSTONE_BLOCK.defaultBlockState());
            var n = f.create(true); f.tick(); helper.assertTrue(f.signal() == 15, "two available inputs OR");
            // Unload-hook window: the real chunk is still readable, but must already be unavailable.
            f.chunk(f.other, false);
            helper.assertTrue(f.signal() == 0 && !n.isDamaged(), "pending unload gates HIGH immediately without damage");
            f.tick(); helper.assertTrue(f.signal() == 0, "known HIGH cannot bypass unavailable second input");
            f.chunk(f.other, true); f.tick(); helper.assertTrue(f.signal() == 15, "load restores without neighborChanged");
            f.chunk(f.wire, false); f.tick(); helper.assertTrue(f.signal() == 15 && !n.isDamaged(), "verified unloaded wire preserves circuit");
            f.set(f.wire, Blocks.AIR.defaultBlockState());
            f.chunk(f.wire, true); helper.assertTrue(f.signal() == 0, "wire load cannot flash stale HIGH");
            f.tick(); helper.assertTrue(n.isDamaged() && f.signal() == 0, "returned missing wire is damaged");
            f.chunk(f.other, false);
            f.set(f.wire, ModBlocks.BASIC_WIRE.get().defaultBlockState());
            NetworkIntegrityManager.processPending(f.level.getServer()); f.tick();
            helper.assertTrue(!n.isDamaged() && f.signal() == 0, "repair does not cancel unavailable input");
            f.chunk(f.other, true); f.tick(); helper.assertTrue(f.signal() == 15, "last dependency restores repaired network");
            f.chunk(f.output, false); f.chunk(f.output, true);
            f.set(f.lamp, Blocks.REDSTONE_LAMP.defaultBlockState()); f.tick();
            helper.assertTrue(f.signal() == 15 && f.level.getBlockState(f.lamp).getValue(RedstoneLampBlock.LIT), "output reload resynchronizes unchanged HIGH");
            f.data.removeNetwork(n.getId()); NetworkRuntime.notifyOutput(f.level, f.output);
            helper.assertTrue(f.signal() == 0, "removal clears runtime before callback");
        }
        helper.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void unavailableAndIndirectDependencies(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            f.set(f.power, Blocks.REDSTONE_BLOCK.defaultBlockState());
            BlockPos far = new BlockPos(15000000, 90, 15000000);
            var n = new CompiledNetwork(1,"unavailable",0,f.level.dimension().location().toString(), List.of(
                new CompiledCircuitElement(1,f.input,CircuitElementType.INPUT,"compiledcircuits:input_endpoint"),
                new CompiledCircuitElement(2,far,CircuitElementType.INPUT,"compiledcircuits:input_endpoint"),
                new CompiledCircuitElement(3,f.output,CircuitElementType.OUTPUT,"compiledcircuits:output_endpoint")));
            f.data.addNetwork(n); f.tick();
            helper.assertTrue(f.signal() == 0 && !f.level.hasChunkAt(far) && !n.isDamaged(), "no forced chunk or fake broken input");
            f.data.removeNetwork(1);
            // Position x=14: the indirect dependency x=16 is beyond the direct neighbor x=15.
            BlockPos boundary = new BlockPos((f.input.getX() >> 4) * 16 + 14, f.input.getY(), f.input.getZ());
            for (long c : RuntimeSignalReader.dependencyChunks(boundary)) f.level.getChunk(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c));
            f.set(boundary, ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
            f.set(boundary.east(), Blocks.STONE.defaultBlockState());
            f.set(boundary.east(2), Blocks.LEVER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, true));
            var indirect = new CompiledNetwork(2,"indirect",0,n.getDimension(),CompiledElementFactory.create(f.level,Set.of(),Set.of(boundary),Set.of(f.output)));
            f.data.addNetwork(indirect); f.tick();
            helper.assertTrue(f.signal() == 15, "strong power through solid neighbor");
            f.chunk(boundary.east(2), false);
            helper.assertTrue(f.signal() == 0, "indirect dependency unload gates before world removal");
            f.chunk(boundary.east(2), true); f.tick(); helper.assertTrue(f.signal() == 15,"indirect dependency returns");
        }
        helper.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void replacementAndFeedback(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            f.set(f.power, Blocks.REDSTONE_BLOCK.defaultBlockState());
            var old = f.create(false);
            var replacement = new CompiledNetwork(old.getId(),"replacement",0,old.getDimension(),CompiledElementFactory.create(f.level,Set.of(),Set.of(f.other),Set.of(f.output)));
            f.data.replaceNetwork(replacement); f.tick();
            helper.assertTrue(f.signal() == 0 && !replacement.isPowered(), "queued old owner cannot activate replacement");
            f.data.removeNetwork(replacement.getId());
            // One output next to its own input: stable feedback must terminate without recursive drain.
            BlockPos feedbackOutput = f.input.east();
            f.set(feedbackOutput, ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
            var feedback = new CompiledNetwork(3,"feedback",0,old.getDimension(),CompiledElementFactory.create(f.level,Set.of(),Set.of(f.input),Set.of(feedbackOutput)));
            f.data.addNetwork(feedback); f.tick();
            helper.assertTrue(feedback.getEffectiveSignal() == 15,"self feedback reaches stable HIGH");
            f.set(f.power,Blocks.AIR.defaultBlockState());
            helper.assertTrue(feedback.getEffectiveSignal() == 15,"self feedback retains supported HIGH without artificial cycle breaking");
            for(int i=0;i<30;i++) NetworkRuntime.inputChanged(f.level,f.input);
            helper.assertTrue(feedback.getEffectiveSignal() == 15,"repeat unchanged checks remain stable");
        }
        helper.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void actualServerRestart(GameTestHelper helper) throws Exception {
        String phase = System.getenv("CC_RUNTIME_RESTART_PHASE");
        if (phase == null) { helper.succeed(); return; }
        var level = helper.getLevel(); var server = level.getServer();
        var storage = server.overworld().getDataStorage();
        var original = NetworkSavedData.get(server);
        String key = "compiledcircuits_runtime_restart_fixture";
        BlockPos a = new BlockPos(4099, 90, 4099), b = a.offset(32,0,0);
        for (var pos : List.of(a,b)) for (long c : RuntimeSignalReader.dependencyChunks(pos))
            level.getChunk(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c));
        if (phase.equals("write")) {
            var saved = new NetworkSavedData();
            for (var pos : List.of(a,b)) {
                level.setBlock(pos, ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),3);
                level.setBlock(pos.above(), ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState(),3);
                level.setBlock(pos.below(), (pos.equals(a)?Blocks.AIR:Blocks.REDSTONE_BLOCK).defaultBlockState(),3);
                var n = new CompiledNetwork(saved.getNextNetworkId(),"restart",0,level.dimension().location().toString(),
                        CompiledElementFactory.create(level,Set.of(),Set.of(pos),Set.of(pos.above())));
                n.setPowered(pos.equals(a)); saved.addNetwork(n);
            }
            storage.set(key,saved); server.saveEverything(true,true,true);
            System.out.println("RUNTIME_RESTART_WRITE: world and SavedData saved; old HIGH/actual LOW and old LOW/actual HIGH");
        } else if (phase.equals("read")) {
            var saved = storage.get(NetworkSavedData::load,key);
            helper.assertTrue(saved != null && saved.getNetworks().size()==2,"restart file exists from previous process");
            helper.assertTrue(saved.getNetwork(1).isPowered() && !saved.getNetwork(2).isPowered(),"saved opposite OR values survived process restart");
            var persisted=saved.save(new CompoundTag());
            storage.set("compiledcircuits_networks",saved);
            try {
                helper.assertTrue(level.getBlockState(a.below()).isAir() && level.getBlockState(b.below()).is(Blocks.REDSTONE_BLOCK),"world blocks persisted across processes");
                helper.assertTrue(saved.getNetwork(1).getEffectiveSignal()==0 && saved.getNetwork(2).getEffectiveSignal()==0,"both outputs LOW before initial checks");
                NetworkRuntime.tick(server);
                helper.assertTrue(saved.getNetwork(1).getEffectiveSignal()==0 && saved.getNetwork(2).getEffectiveSignal()==15,"restart reconciles real world without input changes");
                System.out.println("RUNTIME_RESTART_READ: real two-process save/load passed, outputs 0/15");
            } finally { storage.set("compiledcircuits_networks",original);
                var restored=NetworkSavedData.load(persisted);restored.setDirty();storage.set(key,restored); }
        } else throw new AssertionError("Unknown restart phase");
        helper.succeed();
    }

    @GameTest(template="empty", batch="runtime_budget", timeoutTicks=160)
    public static void boundedInitialization(GameTestHelper helper) throws Exception {
        var f = new Fixture(helper);
        var elements = new ArrayList<CompiledCircuitElement>(CompiledElementFactory.create(f.level,Set.of(f.wire),Set.of(f.input),Set.of(f.output)));
        for (int i=0;i<5000;i++) elements.add(new CompiledCircuitElement(i+10,new BlockPos(14000000+i,90,14000000),CircuitElementType.WIRE,"compiledcircuits:basic_wire"));
        f.set(f.power,Blocks.REDSTONE_BLOCK.defaultBlockState());
        var large = new CompiledNetwork(1,"large",0,f.level.dimension().location().toString(),elements);
        f.data.addNetwork(large);
        f.set(f.other.below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        BlockPos smallOutput=f.other.above(); f.set(smallOutput,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var small = new CompiledNetwork(2,"small",0,large.getDimension(),CompiledElementFactory.create(f.level,Set.of(),Set.of(f.other),Set.of(smallOutput)));
        f.data.addNetwork(small);
        var commands=f.level.getServer().getCommands().getDispatcher(); var source=f.level.getServer().createCommandSourceStack();
        commands.execute("ccperf reset",source); commands.execute("ccperf start",source);
        f.tick(); f.tick(); f.tick();
        helper.assertTrue(large.getEffectiveSignal()==0 && small.getEffectiveSignal()==15,"large init yields and small network progresses within one tick");
        // Change an already checked physical member while initialization spans ticks.
        f.set(f.wire,Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(8, () -> {
            try {
                helper.assertTrue(large.isDamaged() && large.getEffectiveSignal()==0,"changed initial member cannot activate stale HIGH");
                f.set(f.wire,ModBlocks.BASIC_WIRE.get().defaultBlockState()); f.tick();
                helper.assertTrue(large.getEffectiveSignal()==15,"repair after bounded initialization restores output");
                commands.execute("ccperf stop",source);
                var last=com.example.compiledcircuits.diagnostics.PerformanceDiagnostics.class.getDeclaredField("last");last.setAccessible(true);
                var session=last.get(null);var field=session.getClass().getDeclaredField("counters");field.setAccessible(true);
                @SuppressWarnings("unchecked") var metrics=(Map<String,Long>)field.get(session);
                helper.assertTrue(metrics.getOrDefault("runtime.workPerTickPeak",0L)==NetworkRuntime.WORK_PER_TICK,"shared budget exactly reached, never exceeded by repeated drain");
                helper.assertTrue(metrics.getOrDefault("runtime.initializationTicksPeak",0L)>0,"large initialization spans ticks");
                System.out.println("RUNTIME_BUDGET: "+metrics);
                commands.execute("ccperf export",source);
                helper.succeed();
            } catch (Exception error) { throw new RuntimeException(error); }
            finally { f.close(); try { commands.execute("ccperf reset",source); } catch(Exception ignored) {} }
        });
    }
    @GameTest(template="empty", batch="runtime_chain", timeoutTicks=160)
    public static void longChain(GameTestHelper helper) {
        var f=new Fixture(helper); var networks=new ArrayList<CompiledNetwork>();
        BlockPos origin=new BlockPos(8196,90,8196); int count=512;
        var inputs=new ArrayList<BlockPos>();
        for(int i=0;i<=count;i++) inputs.add(origin.offset((i%16)*4,0,(i/16)*4));
        for(int i=0;i<count;i++) {
            BlockPos input=inputs.get(i), output=inputs.get(i+1).west();
            for(var pos:List.of(input,output)) for(long c:RuntimeSignalReader.dependencyChunks(pos)) {
                f.keepLoaded(c); f.level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));
            }
            f.set(input,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
            f.set(input.below(),Blocks.AIR.defaultBlockState());
            f.set(output,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
            var n=new CompiledNetwork(i+1,"chain",0,f.level.dimension().location().toString(),CompiledElementFactory.create(f.level,Set.of(),Set.of(input),Set.of(output)));
            f.data.addNetwork(n);networks.add(n);
        }
        f.set(inputs.get(0).below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        int start=f.level.getServer().getTickCount();
        class Poll implements Runnable {
            int attempts;
            public void run() {
                try {
                    if(networks.get(count-1).getEffectiveSignal()==15) {
                        helper.assertTrue(networks.stream().allMatch(n->n.getEffectiveSignal()==15),"all 512 cascade stages settled");
                        System.out.println("RUNTIME_CHAIN: 512 networks settled in "+(f.level.getServer().getTickCount()-start)+" ticks, no recursive overflow");
                        f.close();helper.succeed();return;
                    }
                    if(++attempts>100) throw new IllegalStateException("chain failed to converge");
                    helper.runAfterDelay(1, () -> this.run());
                } catch(Throwable error) {f.close();throw new RuntimeException(error); }
            }
        }
        helper.runAfterDelay(1,new Poll());
    }
    @GameTest(template="empty", batch="runtime_unload", timeoutTicks=300)
    public static void actualChunkUnload(GameTestHelper helper) {
        var f=new Fixture(helper);BlockPos remote=new BlockPos(16004,90,16004);
        var dependencies=RuntimeSignalReader.dependencyChunks(remote);
        for(long c:dependencies) f.level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));
        f.set(remote,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        f.set(remote.below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        var n=new CompiledNetwork(1,"actual unload",0,f.level.dimension().location().toString(),CompiledElementFactory.create(f.level,Set.of(),Set.of(remote),Set.of(f.output)));
        f.data.addNetwork(n);f.tick();helper.assertTrue(f.signal()==15,"remote input initially available");
        class Poll implements Runnable {
            int attempts; boolean reloaded;
            public void run() {
                try {
                    boolean exists=f.level.getChunkSource().getChunkNow(remote.getX()>>4,remote.getZ()>>4)!=null;
                    if(!reloaded && !exists) {
                        helper.assertTrue(f.signal()==0 && !n.isDamaged(),"real unload: signal="+f.signal()+", damaged="+n.isDamaged()+", status="+n.getRuntimeStatus());
                        for(long c:dependencies) { f.keepLoaded(c); f.level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c)); }
                        reloaded=true;
                    } else if(reloaded && f.signal()==15) {
                        System.out.println("RUNTIME_ACTUAL_UNLOAD: real chunk removal and return passed in "+attempts+" ticks");
                        f.close();helper.succeed();return;
                    }
                    if(attempts==20 || attempts==100) {
                        var current=f.level.getChunkSource().getChunkNow(remote.getX()>>4,remote.getZ()>>4);
                        System.out.println("RUNTIME_UNLOAD_PROGRESS: reloaded="+reloaded+", current="+(current==null?"absent":current.getFullStatus())
                                +", available="+NetworkRuntime.isChunkAvailable(f.level,RuntimeSignalReader.chunk(remote))
                                +", forced="+f.level.getForcedChunks().contains(RuntimeSignalReader.chunk(remote))+", status="+n.getRuntimeStatus());
                    }
                    if(++attempts>250) throw new IllegalStateException("real chunk unload/reload did not complete; reloaded="+reloaded+", signal="+f.signal()+", status="+n.getRuntimeStatus());
                    helper.runAfterDelay(1, () -> this.run());
                } catch(Throwable error) {f.close();throw new RuntimeException(error); }
            }
        }
        helper.runAfterDelay(1,new Poll());
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void twoNetworkCycle(GameTestHelper helper) {
        try(var f=new Fixture(helper)) {
            BlockPos outA=f.other.east(),outB=f.input.east();
            f.set(outA,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());f.set(outB,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
            var a=new CompiledNetwork(1,"cycleA",0,f.level.dimension().location().toString(),CompiledElementFactory.create(f.level,Set.of(),Set.of(f.input),Set.of(outA)));
            var b=new CompiledNetwork(2,"cycleB",0,a.getDimension(),CompiledElementFactory.create(f.level,Set.of(),Set.of(f.other),Set.of(outB)));
            f.data.addNetworks(List.of(a,b));f.tick();
            helper.assertTrue(a.getEffectiveSignal()==0 && b.getEffectiveSignal()==0,"unpowered cycle stable LOW");
            f.set(f.power,Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.assertTrue(a.getEffectiveSignal()==15 && b.getEffectiveSignal()==15,"cycle propagates external HIGH");
            f.set(f.power,Blocks.AIR.defaultBlockState());
            for(int i=0;i<20;i++)f.tick();
            helper.assertTrue(a.getEffectiveSignal()==15 && b.getEffectiveSignal()==15,"stable cycle preserves confirmed HIGH without invented delays");
        }
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void lifecycleOrders(GameTestHelper helper) {
        int[][] orders={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
        for(int[] unload:orders) for(int[] load:orders) try(var f=new Fixture(helper)) {
            BlockPos out=f.input.offset(96,0,0);
            for(long c:RuntimeSignalReader.dependencyChunks(out)) f.level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));
            f.set(out,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());f.set(f.power,Blocks.REDSTONE_BLOCK.defaultBlockState());
            var n=new CompiledNetwork(1,"orders",0,f.level.dimension().location().toString(),CompiledElementFactory.create(f.level,Set.of(f.wire),Set.of(f.input,f.other),Set.of(out)));
            f.data.addNetwork(n);f.tick();helper.assertTrue(n.getEffectiveSignal()==15,"initial order HIGH");
            BlockPos[] parts={f.other,f.wire,out};
            for(int i:unload) f.chunk(parts[i],false);
            helper.assertTrue(n.getEffectiveSignal()==0&&!n.isDamaged(),"all unload orders gate inputs without damage");
            boolean inputReady=false;
            for(int i:load) {
                f.chunk(parts[i],true);if(i==0)inputReady=true;f.tick();
                if(!inputReady)helper.assertTrue(n.getEffectiveSignal()==0,"output or wire cannot cancel input unavailability");
            }
            helper.assertTrue(n.getEffectiveSignal()==15&&!n.isDamaged(),"all 36 load/unload combinations restore");
        }
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void savedBrokenAndStableChecks(GameTestHelper helper) throws Exception {
        try(var f=new Fixture(helper)) {
            f.set(f.power,Blocks.REDSTONE_BLOCK.defaultBlockState());
            var elements=new ArrayList<CompiledCircuitElement>(CompiledElementFactory.create(f.level,Set.of(),Set.of(f.input),Set.of(f.output)));
            var far=new BlockPos(15000000,90,15000000);elements.add(new CompiledCircuitElement(50,far,CircuitElementType.WIRE,"compiledcircuits:basic_wire"));
            var n=new CompiledNetwork(1,"saved broken",0,f.level.dimension().location().toString(),elements);
            n.markBroken(new BrokenCircuitElement(50,"minecraft:air",1));n.setPowered(true);
            var loaded=CompiledNetwork.load(n.save());f.data.addNetwork(loaded);f.tick();
            helper.assertTrue(loaded.isDamaged()&&f.signal()==0&&!f.level.hasChunkAt(far),"saved unloaded broken element cannot be repaired by load");
            f.data.removeNetwork(1);var stable=f.create(false);f.tick();helper.assertTrue(f.signal()==15,"healthy fixture ready");
            var commands=f.level.getServer().getCommands().getDispatcher();var source=f.level.getServer().createCommandSourceStack();
            commands.execute("ccperf reset",source);commands.execute("ccperf start",source);
            for(int i=0;i<20;i++)NetworkRuntime.inputChanged(f.level,f.input);
            commands.execute("ccperf stop",source);
            var last=com.example.compiledcircuits.diagnostics.PerformanceDiagnostics.class.getDeclaredField("last");last.setAccessible(true);
            var session=last.get(null);var field=session.getClass().getDeclaredField("counters");field.setAccessible(true);
            @SuppressWarnings("unchecked")var metrics=(Map<String,Long>)field.get(session);
            helper.assertTrue(metrics.getOrDefault("runtime.outputNotifications",0L)==0,"unchanged input rechecks emit no output notifications");
            commands.execute("ccperf reset",source);
            // Replacing while the chunk is still in the unload callback window must preserve its gate.
            f.chunk(f.input,false);
            f.data.replaceNetwork(new CompiledNetwork(stable.getId(),"pending replace",0,stable.getDimension(),stable.getElements()));
            f.tick();helper.assertTrue(f.signal()==0,"replace cannot erase pending unload state");
            f.chunk(f.input,true);f.tick();helper.assertTrue(f.signal()==15,"replacement resumes on load");
        }
        helper.succeed();
    }

}
