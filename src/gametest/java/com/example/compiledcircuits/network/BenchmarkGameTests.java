package com.example.compiledcircuits.network;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.ModBlocks;
import com.example.compiledcircuits.networking.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class BenchmarkGameTests {
    static CompiledNetwork network(Map<BlockPos,BlockState> states) {
        List<CompiledCircuitElement> elements=new ArrayList<>();
        states.forEach((p,s)->{
            var b=s.getBlock();
            var role=b instanceof InputEndpointBlock?CircuitElementType.INPUT:b instanceof OutputEndpointBlock?CircuitElementType.OUTPUT:
                b instanceof NotGateBlock || b instanceof DLatchBlock?CircuitElementType.GATE:CircuitElementType.WIRE;
            elements.add(new CompiledCircuitElement(elements.size()+1,p,role,CompiledBlockStateCodec.capture(s)));
        });
        var n=new CompiledNetwork(1,"benchmark",0,"minecraft:overworld",elements);n.restoreLogic();n.runtimeState(true,true);return n;
    }
    static CircuitBenchmark.Job run(CompiledNetwork n) {
        var job=new CircuitBenchmark.Job(n,UUID.randomUUID());
        for(int i=0;i<1000&&!job.done;i++)job.step();
        if(!job.done)throw new AssertionError("benchmark did not finish");
        return job;
    }
    @GameTest(template="empty",batch="benchmarks",timeoutTicks=100)
    public static void exhaustiveAndLiveIsolation(GameTestHelper h) {
        var a=BlockPos.ZERO;var b=new BlockPos(4,0,0);var output=new BlockPos(2,0,1);
        Map<BlockPos,BlockState> states=new HashMap<>();
        states.put(a,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());states.put(b,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        states.put(new BlockPos(1,0,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        states.put(new BlockPos(3,0,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.WEST));
        states.put(new BlockPos(2,0,0),ModBlocks.BASIC_WIRE.get().defaultBlockState());states.put(output,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var n=network(states);n.evaluateLogic(Map.of(a,false,b,false));var before=n.save();
        var job=run(n);
        h.assertTrue(job.total==4&&job.next==4&&job.stats.count==4&&job.failed==0,"all independent combinations measured exactly once after repeats");
        h.assertTrue(job.stats.minWork>0&&job.stats.minWork==job.stats.maxWork,"acyclic work independent of signal values");
        h.assertTrue(n.getEffectiveSignal(output)==15&&before.equals(n.save()),"benchmark cannot change live output or saved memory");
        h.assertTrue(job.program==null&&job.states.isEmpty(),"release measurement graph after completion");
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try { var packet=new BenchmarkS2CPacket(1,job.lines());BenchmarkS2CPacket.encode(packet,buf);
            h.assertTrue(BenchmarkS2CPacket.decode(buf).equals(packet),"report round trip"); } finally {buf.release();}
        h.succeed();
    }
    @GameTest(template="empty",batch="benchmarks",timeoutTicks=100)
    public static void statefulSnapshotAndReset(GameTestHelper h) {
        var latch=ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
        var d=BlockPos.ZERO.west();var q=BlockPos.ZERO.east();var w=BlockPos.ZERO.relative(DLatchBlock.enableSide(latch));
        var states=Map.of(BlockPos.ZERO,latch,d,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),w,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),q,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var n=network(states);n.evaluateLogic(Map.of(d,true,w,true));var before=n.save();
        var program=LogicProgram.compile(states);program.importNodeMemory(n.nodeMemory());var baseline=program.benchmarkState();
        h.assertTrue(!program.evaluate(Map.of(d,false,w,true)).get(q),"sample can write LOW");
        program.benchmarkReset(baseline);
        h.assertTrue(program.evaluate(Map.of(d,false,w,false)).get(q),"next sample starts with captured HIGH, not previous sample LOW");
        var job=run(n);h.assertTrue(job.stateful&&job.stats.count==4,"stateful combinations use explicit fixed-state experiment");
        h.assertTrue(before.equals(n.save())&&n.getEffectiveSignal(q)==15,"live latch unchanged");h.succeed();
    }
    @GameTest(template="empty",batch="benchmarks",timeoutTicks=100)
    public static void limitsAndSharedBus(GameTestHelper h) {
        Map<BlockPos,BlockState> states=new HashMap<>();
        for(int i=0;i<17;i++)states.put(new BlockPos(i*3,0,0),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        states.put(new BlockPos(0,0,1),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var rejected=run(network(states));h.assertTrue(rejected.total==0&&rejected.stats.count==0&&rejected.status.contains("Too many"),"reject exponential work over input cap");
        h.assertTrue(rejected.lines().size()<=20,"bounded report even for oversized input count");
        states.clear();states.put(BlockPos.ZERO,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());states.put(BlockPos.ZERO.east(),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        states.put(BlockPos.ZERO.west(),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var shared=run(network(states));h.assertTrue(shared.inputs.size()==1&&shared.total==2,"inputs on same OR bus are one independent logical signal");h.succeed();
    }
    @GameTest(template="empty",batch="benchmarks",timeoutTicks=100)
    public static void unstableCombinationsAreSeparate(GameTestHelper h) {
        Map<BlockPos,BlockState> states=new HashMap<>();
        states.put(BlockPos.ZERO,ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        for(var p:List.of(new BlockPos(-1,0,0),new BlockPos(-1,0,1),new BlockPos(0,0,1),new BlockPos(1,0,1),new BlockPos(1,0,0)))states.put(p,ModBlocks.BASIC_WIRE.get().defaultBlockState());
        var input=new BlockPos(-2,0,0);var output=new BlockPos(2,0,0);
        states.put(input,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());states.put(output,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var n=network(states);n.evaluateLogic(Map.of(input,true));var before=n.save();var job=run(n);
        h.assertTrue(job.total==2&&job.next==2&&job.failed==1&&job.stats.count==1,"unstable combination excluded from success averages");
        h.assertTrue(before.equals(n.save())&&n.getEffectiveSignal(output)==15,"failed benchmark sample cannot damage live stable state");h.succeed();
    }
    @GameTest(template="empty",batch="benchmarks",timeoutTicks=100)
    public static void statisticsAndPacketLimits(GameTestHelper h) {
        var stats=new CircuitBenchmark.Stats();stats.add(0,90,7);stats.add(1,30,12);stats.add(2,60,2);
        h.assertTrue(stats.nanos/stats.count==60&&stats.minTime==30&&stats.maxTime==90&&stats.fastest==1&&stats.slowest==0,"arithmetic mean and timing extremes");
        h.assertTrue(stats.work/stats.count==7&&stats.easiest==2&&stats.hardest==1,"work extremes are separate from timing extremes");
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {buf.writeInt(1);buf.writeVarInt(21);boolean rejected=false;
            try {BenchmarkS2CPacket.decode(buf);}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected,"reject oversized packet before allocating rows");}finally{buf.release();}
        h.succeed();
    }
}
