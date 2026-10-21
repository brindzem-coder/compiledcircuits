package com.example.compiledcircuits.network;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class TruthTableGameTests {
    static final BlockPos A=new BlockPos(-2,0,0), B=new BlockPos(-2,2,0);
    static BlockPos output(int count){return new BlockPos((count-1)*3+2,0,0);}
    static Map<BlockPos,BlockState> chain(int count) {
        Map<BlockPos,BlockState> s=new HashMap<>();
        Block[] gates={ModBlocks.XOR_GATE.get(),ModBlocks.NAND_GATE.get(),ModBlocks.OR_GATE.get(),ModBlocks.XNOR_GATE.get(),ModBlocks.AND_GATE.get(),ModBlocks.NOR_GATE.get()};
        for(int i=0;i<count;i++){
            int x=i*3;var gate=gates[i%gates.length].defaultBlockState();
            gate=GateBodyBlock.withPort(GateBodyBlock.withPort(GateBodyBlock.withPort(gate,Direction.WEST,1),Direction.UP,1),Direction.EAST,2);
            s.put(new BlockPos(x,0,0),gate);
            for(var p:List.of(new BlockPos(x-1,0,0),new BlockPos(x+1,0,0),new BlockPos(x,1,0)))s.put(p,ModBlocks.BASIC_WIRE.get().defaultBlockState());
        }
        for(int x=-1;x<=(count-1)*3;x++)s.put(new BlockPos(x,2,0),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        s.put(A,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());s.put(B,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());s.put(output(count),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        return s;
    }
    static void equal(GameTestHelper h,LogicProgram raw,LogicProgram opt,Map<BlockPos,Boolean> inputs) {
        Map<BlockPos,Boolean> a=null,b=null;String ea="",eb="";
        try{a=raw.evaluate(inputs);}catch(LogicProgram.UnstableFeedback e){ea=e.getMessage();}
        try{b=opt.evaluate(inputs);}catch(LogicProgram.UnstableFeedback e){eb=e.getMessage();}
        h.assertTrue(Objects.equals(a,b)&&ea.equals(eb),"table outputs and instability match baseline");
        h.assertTrue(raw.nodeMemory().equals(opt.nodeMemory()),"table restores every original node including intermediate values");
    }
    @GameTest(template="empty",batch="truth_tables",timeoutTicks=100)
    public static void tablesMatchAllInputsAndReduceWork(GameTestHelper h) {
        for(int count:new int[]{12,64,140}) {
            var s=chain(count);var raw=LogicProgram.compile(s);var simple=LogicProgram.compile(s);var table=LogicProgram.compile(s);
            simple.optimize(s.keySet(),false);var initial=table.benchmarkState();table.optimize(s.keySet());
            h.assertTrue(Arrays.equals(initial,table.benchmarkState()),"table construction never evaluates live state");
            h.assertTrue(table.truthTableCount()>0&&table.truthTableNodes()>=count,"profitable logic uses tables");
            h.assertTrue(table.truthTableBytes()<=LogicProgram.MAX_TABLE_BYTES,"table memory bounded");
            for(int mask:new int[]{0,1,2,3,1,0,3,2}) {
                var inputs=Map.of(A,(mask&1)!=0,B,(mask&2)!=0);equal(h,raw,table,inputs);
                simple.benchmarkReset(simple.benchmarkState());table.benchmarkReset(table.benchmarkState());
                h.assertTrue(simple.evaluate(inputs).equals(table.evaluate(inputs)),"same result as graph simplification alone");
                h.assertTrue(table.lastWork()<simple.lastWork(),"table work counts key reads and node restoration but still saves work");
            }
            int tables=table.truthTableCount(),bytes=table.truthTableBytes();var memory=table.nodeMemory();table.optimize(s.keySet());
            h.assertTrue(tables==table.truthTableCount()&&bytes==table.truthTableBytes()&&memory.equals(table.nodeMemory()),"repeated optimization deterministic and preserves state");
        }h.succeed();
    }
    @GameTest(template="empty",batch="truth_tables",timeoutTicks=100)
    public static void tinyAndExcludedRegionsStayOrdinary(GameTestHelper h) {
        var s=chain(2);var program=LogicProgram.compile(s);program.optimize(s.keySet());h.assertTrue(program.truthTableCount()==0,"tiny gates do not pay table overhead");
        s=chain(20);program=LogicProgram.compile(s);program.optimize(Set.of());h.assertTrue(program.truthTableCount()==0&&program.nodeCount()==program.evaluationNodeCount(),"unoptimized owner never receives tables");
        h.succeed();
    }
    @GameTest(template="empty",batch="truth_tables",timeoutTicks=100)
    public static void tableFeedingLatchAndReload(GameTestHelper h) {
        int count=20;var s=chain(count);var end=output(count);s.put(end,ModBlocks.BASIC_WIRE.get().defaultBlockState());
        var latch=end.east();var state=ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
        var enable=latch.relative(DLatchBlock.enableSide(state));var q=latch.east();s.put(latch,state);
        s.put(enable,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());s.put(q,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var raw=LogicProgram.compile(s);var opt=LogicProgram.compile(s);opt.optimize(s.keySet());h.assertTrue(opt.truthTableCount()>0,"combinational region before latch still optimized");
        for(int mask:new int[]{7,0,1,2,4,5,3,0,6})equal(h,raw,opt,Map.of(A,(mask&1)!=0,B,(mask&2)!=0,enable,(mask&4)!=0));
        var restored=LogicProgram.compile(s);restored.optimize(s.keySet());restored.restoreMemory(raw.saveMemory());
        equal(h,raw,restored,Map.of(A,true,B,false,enable,false));h.succeed();
    }
    @GameTest(template="empty",batch="truth_tables",timeoutTicks=100)
    public static void tableOutsideCyclePreservesFailures(GameTestHelper h) {
        int count=12;var s=chain(count);var end=output(count);s.put(end,ModBlocks.BASIC_WIRE.get().defaultBlockState());
        int x=end.getX()+2;s.put(new BlockPos(x,0,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        for(var p:List.of(new BlockPos(x-1,0,0),new BlockPos(x-1,0,1),new BlockPos(x,0,1),new BlockPos(x+1,0,1),new BlockPos(x+1,0,0)))s.put(p,ModBlocks.BASIC_WIRE.get().defaultBlockState());
        s.put(new BlockPos(x+2,0,0),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var raw=LogicProgram.compile(s);var opt=LogicProgram.compile(s);opt.optimize(s.keySet());h.assertTrue(raw.hasFeedback()&&opt.truthTableCount()>0,"table and protected cycle coexist");
        for(int mask:new int[]{3,1,0,2,3,0,1})equal(h,raw,opt,Map.of(A,(mask&1)!=0,B,(mask&2)!=0));h.succeed();
    }
    static CompiledNetwork network(int id,Map<BlockPos,BlockState> states) {
        var elements=new ArrayList<CompiledCircuitElement>();
        states.forEach((p,s)->{var b=s.getBlock();var role=b instanceof InputEndpointBlock?CircuitElementType.INPUT:b instanceof OutputEndpointBlock?CircuitElementType.OUTPUT:b instanceof GateBodyBlock?CircuitElementType.GATE:CircuitElementType.WIRE;
            elements.add(new CompiledCircuitElement(elements.size()+1,p,role,CompiledBlockStateCodec.capture(s)));});
        var n=new CompiledNetwork(id,"table-direct",0,"minecraft:overworld",elements);n.restoreLogic();n.runtimeState(true,true);return n;
    }
    @GameTest(template="empty",batch="truth_tables",timeoutTicks=100)
    public static void tableAcrossDirectBoundary(GameTestHelper h) {
        var source=chain(20);var out=output(20);var in=out.east();var targetOut=in.east();
        source.put(out,NetworkWireBlock.withFace(ModBlocks.NETWORK_WIRE.get().defaultBlockState(),Direction.EAST,3));
        var target=Map.of(in,NetworkWireBlock.withFace(ModBlocks.NETWORK_WIRE.get().defaultBlockState(),Direction.WEST,2),targetOut,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var raw=List.of(network(1,source),network(2,target));var opt=List.of(network(1,source),network(2,target));
        opt.get(0).enableOptimization();
        var rt=new DirectLinks(new NetworkSavedData(),n->{});var ot=new DirectLinks(new NetworkSavedData(),n->{});
        for(var n:raw)rt.add(n);for(var n:opt)ot.add(n);
        for(int mask:new int[]{0,1,2,3,1,0}){
            var inputs=Map.of(A,(mask&1)!=0,B,(mask&2)!=0);
            rt.update(raw.get(0),inputs);rt.update(raw.get(1),Map.of());ot.update(opt.get(0),inputs);ot.update(opt.get(1),Map.of());
            DirectLinkGameTests.settle(rt);DirectLinkGameTests.settle(ot);
            h.assertTrue(raw.get(1).getEffectiveSignal(targetOut)==opt.get(1).getEffectiveSignal(targetOut),"optimized table drives unoptimized peer identically");
        }h.succeed();
    }

}
