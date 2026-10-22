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
public class ConstantOptimizationGameTests {
    static Block[] gates(){return new Block[]{ModBlocks.AND_GATE.get(),ModBlocks.OR_GATE.get(),ModBlocks.NAND_GATE.get(),ModBlocks.NOR_GATE.get(),ModBlocks.XOR_GATE.get(),ModBlocks.XNOR_GATE.get()};}
    static Map<BlockPos,BlockState> gate(Block block){
        Map<BlockPos,BlockState> s=new HashMap<>();var state=block.defaultBlockState();
        state=GateBodyBlock.withPort(GateBodyBlock.withPort(GateBodyBlock.withPort(state,Direction.WEST,1),Direction.NORTH,1),Direction.EAST,2);s.put(BlockPos.ZERO,state);
        s.put(BlockPos.ZERO.west(),ModBlocks.BASIC_WIRE.get().defaultBlockState());s.put(BlockPos.ZERO.west(2),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        s.put(BlockPos.ZERO.east(),ModBlocks.BASIC_WIRE.get().defaultBlockState());s.put(BlockPos.ZERO.east(2),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());return s;
    }
    static void verify(GameTestHelper h,Map<BlockPos,BlockState> s){
        var raw=LogicProgram.compile(s);var opt=LogicProgram.compile(s);opt.optimize(s.keySet());
        for(boolean signal:new boolean[]{false,true,false,true,true}){
            var input=Map.of(BlockPos.ZERO.west(2),signal);
            h.assertTrue(raw.evaluate(input).equals(opt.evaluate(input)),"all outputs preserve Boolean function");
            h.assertTrue(raw.nodeMemory().equals(opt.nodeMemory()),"all original node values remain compatible");
        }
        var memory=opt.nodeMemory();opt.optimize(s.keySet());h.assertTrue(memory.equals(opt.nodeMemory()),"reoptimization does not change current state");
        opt.benchmarkReset(opt.benchmarkState());raw.evaluate(Map.of());opt.evaluate(Map.of());
        h.assertTrue(opt.lastWork()<raw.lastWork(),"constant or repeated-input simplification reduces full evaluation work");
    }
    @GameTest(template="empty",batch="constant_optimization",timeoutTicks=100)
    public static void everyGateWithLowAndHighConstants(GameTestHelper h){
        for(var block:gates())for(boolean high:new boolean[]{false,true}){
            var s=gate(block);
            if(high){s.put(BlockPos.ZERO.north(),ModBlocks.BASIC_WIRE.get().defaultBlockState());s.put(BlockPos.ZERO.north(2),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.SOUTH));}
            verify(h,s);
        }h.succeed();
    }
    @GameTest(template="empty",batch="constant_optimization",timeoutTicks=100)
    public static void repeatedSignalsEvenAndOddMultiplicity(GameTestHelper h){
        for(var block:gates())for(boolean third:new boolean[]{false,true}){
            var s=gate(block);s.put(BlockPos.ZERO.north(),ModBlocks.BASIC_WIRE.get().defaultBlockState());s.put(new BlockPos(-1,0,-1),ModBlocks.BASIC_WIRE.get().defaultBlockState());
            if(third){s.put(BlockPos.ZERO,GateBodyBlock.withPort(s.get(BlockPos.ZERO),Direction.UP,1));s.put(BlockPos.ZERO.above(),ModBlocks.BASIC_WIRE.get().defaultBlockState());s.put(new BlockPos(-1,1,0),ModBlocks.BASIC_WIRE.get().defaultBlockState());}
            verify(h,s);
        }h.succeed();
    }
    @GameTest(template="empty",batch="constant_optimization",timeoutTicks=100)
    public static void inactiveExternalInputsAreNotConstants(GameTestHelper h){
        var s=gate(ModBlocks.AND_GATE.get());s.put(BlockPos.ZERO.north(),ModBlocks.BASIC_WIRE.get().defaultBlockState());s.put(BlockPos.ZERO.north(2),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        var raw=LogicProgram.compile(s);var opt=LogicProgram.compile(s);opt.evaluate(Map.of());opt.optimize(s.keySet());
        for(int mask=0;mask<4;mask++){var in=Map.of(BlockPos.ZERO.west(2),(mask&1)!=0,BlockPos.ZERO.north(2),(mask&2)!=0);h.assertTrue(raw.evaluate(in).equals(opt.evaluate(in)),"LOW live input never becomes a proven constant");}
        var restricted=LogicProgram.compile(s);restricted.optimize(Set.of());restricted.evaluate(Map.of());raw.evaluate(Map.of());h.assertTrue(restricted.lastWork()==raw.lastWork(),"unoptimized regions are untouched");h.succeed();
    }
}
