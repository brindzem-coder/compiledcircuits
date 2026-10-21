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
public class GraphOptimizationGameTests {
    static void compare(GameTestHelper h, Map<BlockPos,BlockState> states, List<Map<BlockPos,Boolean>> sequence) {
        var raw=LogicProgram.compile(states);var optimized=LogicProgram.compile(states);optimized.optimize(states.keySet());
        for(var inputs:sequence) {
            h.assertTrue(raw.evaluate(inputs).equals(optimized.evaluate(inputs)),"identical outputs");
            h.assertTrue(raw.nodeMemory().equals(optimized.nodeMemory()),"all original memory IDs preserve their values");
        }
        h.assertTrue(raw.saveMemory().equals(optimized.saveMemory()),"memory signature and layout unchanged");
    }
    @GameTest(template="empty",batch="optimization",timeoutTicks=100)
    public static void longNotChainsAndCommonExpressions(GameTestHelper h) {
        for(int count:new int[]{2,3,40,301}) {
            Map<BlockPos,BlockState> s=new HashMap<>();var input=new BlockPos(-1,0,0);
            s.put(input,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
            for(int i=0;i<count;i++)s.put(new BlockPos(i,0,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
            s.put(new BlockPos(count,0,0),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
            compare(h,s,List.of(Map.of(input,false),Map.of(input,true),Map.of(input,false)));
            var raw=LogicProgram.compile(s);var opt=LogicProgram.compile(s);opt.optimize(s.keySet());raw.evaluate(Map.of());opt.evaluate(Map.of());
            h.assertTrue(opt.lastWork()<raw.lastWork()&&opt.evaluationNodeCount()==2,"NOT chain collapses to input and one shared inversion");
        }
        Map<BlockPos,BlockState> s=new HashMap<>();var input=new BlockPos(-2,0,1);
        for(int z=0;z<=2;z++)s.put(new BlockPos(-1,0,z),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        for(int z:new int[]{0,2}){s.put(new BlockPos(0,0,z),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));s.put(new BlockPos(1,0,z),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());}
        s.put(input,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());compare(h,s,List.of(Map.of(input,false),Map.of(input,true)));
        var opt=LogicProgram.compile(s);opt.optimize(s.keySet());h.assertTrue(opt.evaluationNodeCount()==2,"identical parallel NOTs share one calculation");h.succeed();
    }
    @GameTest(template="empty",batch="optimization",timeoutTicks=100)
    public static void gateTruthTablesAndDuplicateXorInputs(GameTestHelper h) {
        for(Block gate:new Block[]{ModBlocks.AND_GATE.get(),ModBlocks.OR_GATE.get(),ModBlocks.NAND_GATE.get(),ModBlocks.NOR_GATE.get(),ModBlocks.XOR_GATE.get(),ModBlocks.XNOR_GATE.get()}) {
            Map<BlockPos,BlockState> s=new HashMap<>();var state=gate.defaultBlockState();
            state=GateBodyBlock.withPort(GateBodyBlock.withPort(GateBodyBlock.withPort(state,Direction.WEST,1),Direction.NORTH,1),Direction.EAST,2);s.put(BlockPos.ZERO,state);
            for(var d:new Direction[]{Direction.WEST,Direction.NORTH,Direction.EAST}){
                s.put(BlockPos.ZERO.relative(d),ModBlocks.BASIC_WIRE.get().defaultBlockState());
                s.put(BlockPos.ZERO.relative(d,2),(d==Direction.EAST?ModBlocks.OUTPUT_ENDPOINT.get():ModBlocks.INPUT_ENDPOINT.get()).defaultBlockState());
            }
            var a=BlockPos.ZERO.west(2);var b=BlockPos.ZERO.north(2);
            var sequence=List.of(Map.of(a,false,b,false),Map.of(a,true,b,false),Map.of(a,false,b,true),Map.of(a,true,b,true));compare(h,s,sequence);
            s.put(new BlockPos(-1,0,-1),ModBlocks.BASIC_WIRE.get().defaultBlockState());compare(h,s,sequence);
            var opt=LogicProgram.compile(s);opt.optimize(s.keySet());var value=opt.evaluate(Map.of(a,true,b,true)).get(BlockPos.ZERO.east(2));
            if(gate==ModBlocks.XOR_GATE.get())h.assertTrue(!value,"XOR(A,A) remains false after aliases converge");
            if(gate==ModBlocks.XNOR_GATE.get())h.assertTrue(value,"XNOR(A,A) remains true");
        }h.succeed();
    }
    @GameTest(template="empty",batch="optimization",timeoutTicks=100)
    public static void latchStateAndPersistence(GameTestHelper h) {
        var latch=ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
        var d=BlockPos.ZERO.west();var q=BlockPos.ZERO.east();var w=BlockPos.ZERO.relative(DLatchBlock.enableSide(latch));
        var s=Map.of(BlockPos.ZERO,latch,d,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),w,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),q,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        compare(h,s,List.of(Map.of(d,true,w,true),Map.of(d,false,w,false),Map.of(d,false,w,true),Map.of(d,true,w,false)));
        var n=BenchmarkGameTests.network(s);n.evaluateLogic(Map.of(d,true,w,true));var memory=n.save().getCompound("logicMemory").copy();
        n.enableOptimization();h.assertTrue(n.save().getCompound("logicMemory").equals(memory),"enabling optimization does not reset memory");
        var loaded=CompiledNetwork.load(n.save());loaded.restoreLogic();loaded.runtimeState(true,true);loaded.evaluateLogic(Map.of(d,false,w,false));
        h.assertTrue(loaded.isOptimized()&&loaded.getEffectiveSignal(q)==15,"optimized mode and HIGH latch survive reload");
        var old=n.save();old.remove("optimized");var legacy=CompiledNetwork.load(old);legacy.restoreLogic();legacy.runtimeState(true,true);legacy.evaluateLogic(Map.of(d,false,w,false));
        h.assertTrue(!legacy.isOptimized()&&legacy.getEffectiveSignal(q)==15,"legacy memory loads without optimization flag");h.succeed();
    }
    @GameTest(template="empty",batch="optimization",timeoutTicks=100)
    public static void mixedDirectNetworksAndFeedback(GameTestHelper h) {
        for(boolean even:new boolean[]{true,false}) {
            var raw=DirectLinkGameTests.loop(even);var opt=DirectLinkGameTests.loop(even);
            var rt=new DirectLinks(new NetworkSavedData(),n->{});var ot=new DirectLinks(new NetworkSavedData(),n->{});
            opt.get(0).enableOptimization();for(var n:raw)rt.add(n);for(var n:opt)ot.add(n);
            var a=new BlockPos(0,1,0);var b=new BlockPos(-2,-1,1);
            int iteration=0;
            for(int bits:new int[]{1,0,2,0,3,1,0,1}) {
                if(iteration++==3){opt.get(1).enableOptimization();ot.optimizationChanged(opt.get(1));}
                rt.update(raw.get(0),Map.of(a,(bits&1)!=0));rt.update(raw.get(1),Map.of(b,(bits&2)!=0));
                ot.update(opt.get(0),Map.of(a,(bits&1)!=0));ot.update(opt.get(1),Map.of(b,(bits&2)!=0));
                DirectLinkGameTests.settle(rt);DirectLinkGameTests.settle(ot);
                for(int i=0;i<2;i++){
                    h.assertTrue(raw.get(i).nodeMemory().equals(opt.get(i).nodeMemory()),"mixed direct network optimization preserves feedback memory");
                    h.assertTrue(raw.get(i).getLogicError().equals(opt.get(i).getLogicError()),"same oscillation outcome and location");
                    for(var port:raw.get(i).directPorts())if(!port.input())h.assertTrue(raw.get(i).getEffectiveSignal(port.pos())==opt.get(i).getEffectiveSignal(port.pos()),"same direct outputs");
                }
            }
        }h.succeed();
    }
}
