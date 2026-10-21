package com.example.compiledcircuits.network;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class IncrementalGameTests {
    @GameTest(template="empty",batch="incremental",timeoutTicks=100)
    public static void tablesSkipAndReset(GameTestHelper h) {
        var s=TruthTableGameTests.chain(64);var raw=LogicProgram.compile(s);var opt=LogicProgram.compile(s);opt.optimize(s.keySet());
        var random=new Random(71);
        for(int i=0;i<256;i++) {
            var in=Map.of(TruthTableGameTests.A,random.nextBoolean(),TruthTableGameTests.B,random.nextBoolean());
            TruthTableGameTests.equal(h,raw,opt,in);
            opt.evaluate(in);h.assertTrue(opt.lastWork()==0&&opt.lastSkipped()>0,"unchanged inputs skip every execution unit including tables");
        }
        var in=Map.of(TruthTableGameTests.A,true,TruthTableGameTests.B,false);
        opt.benchmarkReset(opt.benchmarkState());opt.evaluate(in);h.assertTrue(opt.lastWork()>0,"benchmark reset invalidates incremental state");
        opt.importNodeMemory(raw.nodeMemory());TruthTableGameTests.equal(h,raw,opt,in);
        h.assertTrue(opt.lastWork()>0,"memory import forces full evaluation");
        opt.optimize(s.keySet());opt.evaluate(in);h.assertTrue(opt.lastWork()>0,"reoptimization invalidates boundaries");h.succeed();
    }
    @GameTest(template="empty",batch="incremental",timeoutTicks=100)
    public static void latchStopsPropagation(GameTestHelper h) {
        var state=ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
        var d=BlockPos.ZERO.west();var enable=BlockPos.ZERO.relative(DLatchBlock.enableSide(state));var q=BlockPos.ZERO.east();
        var s=Map.of(BlockPos.ZERO,state,d,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),enable,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),q,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var raw=LogicProgram.compile(s);var opt=LogicProgram.compile(s);opt.optimize(s.keySet());
        TruthTableGameTests.equal(h,raw,opt,Map.of(d,false,enable,false));int full=opt.lastWork();
        TruthTableGameTests.equal(h,raw,opt,Map.of(d,true,enable,false));
        h.assertTrue(opt.lastWork()<full&&opt.lastSkipped()>0,"closed latch blocks propagation to downstream output");
        for(int mask:new int[]{3,3,1,0,0,2,2,3,0})TruthTableGameTests.equal(h,raw,opt,Map.of(d,(mask&1)!=0,enable,(mask&2)!=0));
        opt.restoreMemory(raw.saveMemory());TruthTableGameTests.equal(h,raw,opt,Map.of(d,false,enable,false));
        h.assertTrue(opt.lastWork()>0,"restored latch forces evaluation");h.succeed();
    }
    @GameTest(template="empty",batch="incremental",timeoutTicks=100)
    public static void failedCyclesNeverCommitSkipState(GameTestHelper h) {
        Map<BlockPos,BlockState> s=new HashMap<>();
        s.put(BlockPos.ZERO,ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        for(var p:List.of(new BlockPos(-1,0,0),new BlockPos(-1,0,1),new BlockPos(0,0,1),new BlockPos(1,0,1),new BlockPos(1,0,0)))s.put(p,ModBlocks.BASIC_WIRE.get().defaultBlockState());
        var in=new BlockPos(-2,0,0);s.put(in,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());s.put(new BlockPos(2,0,0),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var raw=LogicProgram.compile(s);var opt=LogicProgram.compile(s);opt.optimize(s.keySet());
        for(boolean v:new boolean[]{true,true,false,false,true,true,false,true}) {
            TruthTableGameTests.equal(h,raw,opt,Map.of(in,v));
            if(!v)h.assertTrue(opt.lastWork()>0,"failed input is retried rather than cached as successful");
        }h.succeed();
    }
    @GameTest(template="empty",batch="incremental",timeoutTicks=100)
    public static void sequenceBenchmarkCarriesStateAndMeasuresSkips(GameTestHelper h) {
        var n=TruthTableGameTests.network(1,TruthTableGameTests.chain(20));n.enableOptimization();var saved=n.save();
        var opt=new CircuitBenchmark.Job(n,UUID.randomUUID(),true,true);var raw=new CircuitBenchmark.Job(n,UUID.randomUUID(),false,true);
        for(int i=0;i<1000&&(!opt.done||!raw.done);i++){if(!opt.done)opt.step();if(!raw.done)raw.step();}
        h.assertTrue(opt.done&&raw.done&&opt.stats.count==8&&raw.stats.count==8&&opt.repeat==3,"three complete trajectories averaged per matching step");
        h.assertTrue(opt.stats.minWork==0&&raw.stats.minWork>0&&opt.stats.work<raw.stats.work,"unchanged samples skip only in optimized sequence");
        h.assertTrue(saved.equals(n.save()),"sequence never changes live state");
        h.assertTrue(opt.lines().size()<=20&&opt.lines().stream().allMatch(l->l.length()<=256),"sequence report fits protocol");
        h.assertTrue(opt.sequenceTimes==null&&opt.sequenceWork==null,"trajectory buffers released");h.succeed();
    }
    @GameTest(template="empty",batch="incremental",timeoutTicks=100)
    public static void sequenceLatchMatchesThreeIndependentTrajectories(GameTestHelper h) {
        var state=ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
        var d=BlockPos.ZERO.west();var enable=BlockPos.ZERO.relative(DLatchBlock.enableSide(state));
        var s=Map.of(BlockPos.ZERO,state,d,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),enable,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),BlockPos.ZERO.east(),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var n=BenchmarkGameTests.network(s);n.evaluateLogic(Map.of(d,true,enable,true));var saved=n.save();
        var job=new CircuitBenchmark.Job(n,UUID.randomUUID(),true,true);
        while(job.program==null||job.warmup<128)job.step();
        var reference=LogicProgram.compile(s);reference.importNodeMemory(n.nodeMemory());var initial=reference.benchmarkState();
        while(!job.done) {
            if(job.sequenceStep==0)reference.benchmarkReset(initial);
            int mask=CircuitBenchmark.Job.sequenceMask(job.sequenceStep);
            Map<BlockPos,Boolean> values=new HashMap<>();
            for(int k=0;k<job.inputs.size();k++)values.put(job.inputs.get(k),(mask&(1<<k))!=0);
            reference.evaluate(values);job.step();
            if(!job.done&&job.sequenceStep!=0)h.assertTrue(Arrays.equals(reference.benchmarkState(),job.program.benchmarkState()),"trajectory preserves latch memory between samples");
        }
        h.assertTrue(job.stats.count==8&&job.failed==0&&saved.equals(n.save()),"three latch trajectories complete without changing live memory");h.succeed();
    }

}
