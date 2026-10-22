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
public class ManualTableGameTests {
    static NetworkTruthTable build(Map<BlockPos,BlockState> states) {
        var builder=new NetworkTruthTable.Builder(LogicProgram.compile(states));
        while(!builder.step()){} return builder.finish();
    }
    static Map<BlockPos,Boolean> input(int mask) {return Map.of(TruthTableGameTests.A,(mask&1)!=0,TruthTableGameTests.B,(mask&2)!=0);}
    @GameTest(template="empty",batch="manual_tables",timeoutTicks=100)
    public static void wholeTablePersistenceRemovalAndOptimization(GameTestHelper h) {
        var states=TruthTableGameTests.chain(140);var raw=LogicProgram.compile(states);var table=build(states);
        h.assertTrue(table.bytes()==32,"only four rows of external outputs, independent of internal graph size");
        var n=TruthTableGameTests.network(1,states);n.enableOptimization();
        var graph=LogicProgram.compile(states);graph.optimize(states.keySet());h.assertTrue(graph.truthTableCount()==0,"optimization no longer creates automatic tables");
        n.evaluateLogic(input(3));var before=n.getEffectiveSignal(TruthTableGameTests.output(140));n.setNetworkTable(table);
        h.assertTrue(before==n.getEffectiveSignal(TruthTableGameTests.output(140)),"installation preserves published signals");
        var saved=n.save();var loaded=CompiledNetwork.load(saved);loaded.restoreLogic();loaded.runtimeState(true,true);
        h.assertTrue(loaded.networkTable()!=null&&loaded.networkTable().save().equals(table.save()),"persist exact rows rather than rebuild them");
        for(int mask:new int[]{0,1,2,3,3,1,0}) {
            var expected=raw.evaluate(input(mask));loaded.evaluateLogic(input(mask));
            h.assertTrue(loaded.getEffectiveSignal(TruthTableGameTests.output(140))==(expected.get(TruthTableGameTests.output(140))?15:0),"loaded whole table matches graph");
        }
        loaded.setNetworkTable(null);h.assertTrue(loaded.isOptimized()&&!loaded.save().contains("networkTable"),"removal preserves previous graph optimization and removes rows");
        for(int mask=0;mask<4;mask++){var expected=raw.evaluate(input(mask));loaded.evaluateLogic(input(mask));h.assertTrue(loaded.getEffectiveSignal(TruthTableGameTests.output(140))==(expected.get(TruthTableGameTests.output(140))?15:0),"removed table returns to equivalent graph");}
        h.succeed();
    }
    @GameTest(template="empty",batch="manual_tables",timeoutTicks=100)
    public static void tablesContractAcrossLinkedGraphs(GameTestHelper h) {
        var source=TruthTableGameTests.chain(12);var out=TruthTableGameTests.output(12);var in=out.east();var end=in.east();
        source.put(out,NetworkWireBlock.withFace(ModBlocks.NETWORK_WIRE.get().defaultBlockState(),Direction.EAST,3));
        var target=Map.of(in,NetworkWireBlock.withFace(ModBlocks.NETWORK_WIRE.get().defaultBlockState(),Direction.WEST,2),end,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var a=TruthTableGameTests.network(1,source);var b=TruthTableGameTests.network(2,target);a.setNetworkTable(build(source));b.setNetworkTable(build(target));a.enableOptimization();b.enableOptimization();
        var savedA=a.networkTable().save();var links=new DirectLinks(new NetworkSavedData(),n->{});links.add(a);links.add(b);
        var raw=LogicProgram.compile(source);
        for(int mask:new int[]{0,1,2,3,1,0}){links.update(a,input(mask));links.update(b,Map.of());DirectLinkGameTests.settle(links);h.assertTrue(b.getEffectiveSignal(end)==(raw.evaluate(input(mask)).get(out)?15:0),"two contracted networks propagate same-call signals");}
        h.assertTrue(a.tableStatus().contains("active in linked")&&b.tableStatus().contains("active in linked"),"both tables active");
        links.remove(b);DirectLinkGameTests.settle(links);links.add(b);links.update(b,Map.of());DirectLinkGameTests.settle(links);
        h.assertTrue(savedA.equals(a.networkTable().save()),"link rebuild reuses persisted rows");h.succeed();
    }
    @GameTest(template="empty",batch="manual_tables",timeoutTicks=100)
    public static void linkedCycleSuspendsAndRestoresTable(GameTestHelper h) {
        var pair=DirectLinkGameTests.loop(true);var original=DirectLinkGameTests.loop(true);for(var n:pair)n.setNetworkTable(build(n.tableStates()));
        var links=new DirectLinks(new NetworkSavedData(),n->{});for(var n:pair){links.add(n);links.update(n,Map.of());}DirectLinkGameTests.settle(links);
        var baseline=new DirectLinks(new NetworkSavedData(),n->{});for(var n:original){baseline.add(n);baseline.update(n,Map.of());}DirectLinkGameTests.settle(baseline);
        for(int mask:new int[]{0,1,3,2,0,0}) {
            var a=Map.of(new BlockPos(0,1,0),(mask&1)!=0);var b=Map.of(new BlockPos(-2,-1,1),(mask&2)!=0);
            links.update(pair.get(0),a);links.update(pair.get(1),b);baseline.update(original.get(0),a);baseline.update(original.get(1),b);
            DirectLinkGameTests.settle(links);DirectLinkGameTests.settle(baseline);
            for(int i=0;i<pair.size();i++) {
                h.assertTrue(pair.get(i).getLogicError().equals(original.get(i).getLogicError()),"cycle fallback preserves stability/errors");
                for(var port:pair.get(i).directPorts())if(!port.input())h.assertTrue(pair.get(i).getEffectiveSignal(port.pos())==original.get(i).getEffectiveSignal(port.pos()),"cycle fallback preserves original output sequence");
            }
        }
        for(var n:pair)h.assertTrue(n.tableStatus().contains("suspended")&&n.networkTable()!=null,"cycle uses original graph without deleting saved rows");
        links.remove(pair.get(1));DirectLinkGameTests.settle(links);
        h.assertTrue(pair.get(0).tableStatus().contains("active in linked"),"table resumes when cycle removed");h.succeed();
    }
    @GameTest(template="empty",batch="manual_tables",timeoutTicks=100)
    public static void limitsAndInvalidSavedData(GameTestHelper h) {
        boolean refused=false;
        try{build(Map.of(BlockPos.ZERO,ModBlocks.D_LATCH.get().defaultBlockState()));}catch(IllegalArgumentException expected){refused=true;}
        h.assertTrue(refused,"latches rejected before allocation");
        Map<BlockPos,BlockState> many=new HashMap<>();for(int i=0;i<17;i++)many.put(new BlockPos(i*3,0,0),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());many.put(new BlockPos(0,0,1),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        refused=false;try{build(many);}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"input cap enforced");
        var s=TruthTableGameTests.chain(12);var table=build(s);var bad=table.save();bad.putLongArray("rows",new long[0]);
        refused=false;try{NetworkTruthTable.load(bad);}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"truncated persisted rows rejected");
        var n=TruthTableGameTests.network(1,s);n.setNetworkTable(table);var save=n.save();save.getCompound("networkTable").putString("graph","wrong");
        var loaded=CompiledNetwork.load(save);loaded.restoreLogic();h.assertTrue(loaded.networkTable()==null&&loaded.tableStatus().contains("discarded"),"wrong topology falls back to graph");h.succeed();
    }
    @GameTest(template="empty",batch="manual_tables",timeoutTicks=100)
    public static void multipleOutputWordsAndSharedInputBus(GameTestHelper h) {
        Map<BlockPos,BlockState> s=new HashMap<>();var a=BlockPos.ZERO;var b=a.west();
        s.put(a,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());s.put(b,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        for(int i=1;i<=65;i++)s.put(new BlockPos(i,0,0),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var table=build(s);h.assertTrue(table.inputs.size()==1&&table.outputs.size()==65&&table.words==2,"shared physical inputs OR into one independent bit; outputs span words");
        var passive=BenchmarkGameTests.network(s);passive.setNetworkTable(table);h.assertTrue(passive.hasLogic(),"passive network can use saved table");passive.setNetworkTable(null);
        h.assertTrue(!passive.hasLogic(),"removal restores original passive OR shortcut");
        var raw=LogicProgram.compile(s);var opt=LogicProgram.compile(s);opt.installTable(table,s.keySet());
        for(var values:List.of(Map.of(a,true,b,false),Map.of(a,false,b,true),Map.of(a,false,b,false)))h.assertTrue(raw.evaluate(values).equals(opt.evaluate(values)),"shared bus and packed output words match");h.succeed();
    }
    @GameTest(template="empty",batch="manual_tables",timeoutTicks=100)
    public static void benchmarksMeasureCurrentSavedTable(GameTestHelper h) {
        var s=TruthTableGameTests.chain(20);var n=TruthTableGameTests.network(1,s);n.enableOptimization();n.setNetworkTable(build(s));var saved=n.save();
        var current=new CircuitBenchmark.Job(n,UUID.randomUUID(),true,true,true);var baseline=new CircuitBenchmark.Job(n,UUID.randomUUID(),false,true,false);
        for(int i=0;i<1000&&(!current.done||!baseline.done);i++){if(!current.done)current.step();if(!baseline.done)baseline.step();}
        h.assertTrue(current.done&&baseline.done&&current.tables==1&&baseline.tables==0&&current.evaluationNodes==1,"current benchmark reads whole table; baseline ignores it");
        h.assertTrue(current.stats.work<baseline.stats.work&&saved.equals(n.save()),"measure table work without modifying live data");
        current.previousReport=List.of("Previous graph time avg/min/max: 1 / 1 / 1 us","Previous work avg/min/max: 10 / 10 / 10");
        h.assertTrue(current.lines().size()<=20&&current.lines().stream().allMatch(l->l.length()<=256),"comparison stays inside packet limits");h.succeed();
    }
    @GameTest(template="empty",batch="manual_tables",timeoutTicks=100)
    public static void contractedTableFeedsStatefulNeighbor(GameTestHelper h) {
        var source=TruthTableGameTests.chain(12);var out=TruthTableGameTests.output(12);
        var latch=new BlockPos(100,0,0);var state=ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
        var d=latch.west();var q=latch.east();var enable=latch.relative(DLatchBlock.enableSide(state));
        var target=Map.of(latch,state,d,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),enable,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),q,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        Map<BlockPos,BlockState> combined=new HashMap<>(source);combined.putAll(target);Map<BlockPos,Integer> owners=new HashMap<>();source.keySet().forEach(p->owners.put(p,1));target.keySet().forEach(p->owners.put(p,2));
        var edges=Map.of(out,Set.of(d));var raw=LogicProgram.compile(combined,edges,owners);var opt=LogicProgram.compile(combined,edges,owners);
        opt.installTable(build(source),source.keySet());opt.optimize(combined.keySet());
        for(int mask:new int[]{7,0,1,5,3,0,4,6,2,2,7,0}) {
            Map<BlockPos,Boolean> values=new HashMap<>(input(mask));values.put(enable,(mask&4)!=0);
            h.assertTrue(raw.evaluate(values).equals(opt.evaluate(values)),"table in shared graph preserves neighboring latch transitions");
        }
        h.assertTrue(opt.truthTableCount()==1,"table remains active beside latch");h.succeed();
    }
    @GameTest(template="empty",batch="manual_tables",timeoutTicks=100)
    public static void constantsAndImmutability(GameTestHelper h) {
        var states=TruthTableGameTests.chain(12);states.remove(TruthTableGameTests.A);states.remove(TruthTableGameTests.B);
        var table=build(states);h.assertTrue(table.inputs.isEmpty()&&table.bytes()==8,"zero-input constant uses one row");
        var expected=table.save();var exposed=table.save();exposed.getLongArray("rows")[0]^=1;
        h.assertTrue(table.save().equals(expected),"saved tag cannot mutate immutable row storage");
        var raw=LogicProgram.compile(states);var opt=LogicProgram.compile(states);opt.installTable(table,states.keySet());
        h.assertTrue(raw.evaluate(Map.of()).equals(opt.evaluate(Map.of())),"constant table outputs correct");opt.evaluate(Map.of());h.assertTrue(opt.lastWork()==0,"constant table skips after initialization");h.succeed();
    }

}
