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
public class IsolatedWireGameTests {
    @GameTest(template="empty",batch="isolated_wire")
    public static void adjacentCopiesNeverConnect(GameTestHelper h){
        var wire=ModBlocks.ISOLATED_WIRE.get().defaultBlockState();var basic=ModBlocks.BASIC_WIRE.get().defaultBlockState();
        for(var side:Direction.values()){
            h.assertTrue(!CircuitConnections.connects(wire,wire,side),"no self connection on "+side);
            h.assertTrue(CircuitConnections.connects(wire,basic,side)&&CircuitConnections.connects(basic,wire,side),"basic wire still connects on "+side);
        }
        var pos=h.absolutePos(new BlockPos(1,3,1));var level=h.getLevel();
        level.setBlock(pos,wire,3);level.setBlock(pos.east(),wire,3);
        var left=Block.updateFromNeighbourShapes(wire,level,pos);var right=Block.updateFromNeighbourShapes(wire,level,pos.east());
        h.assertTrue(!left.getValue(BasicWireBlock.EAST)&&!right.getValue(BasicWireBlock.WEST),"no visual arms between copies");
        h.assertTrue(!CircuitConnections.connects(level,pos,left,right,Direction.EAST),"world discovery also isolates copies");
        level.setBlock(pos.east(),basic,3);left=Block.updateFromNeighbourShapes(wire,level,pos);
        h.assertTrue(left.getValue(BasicWireBlock.EAST),"normal wire restores arm");h.succeed();
    }
    @GameTest(template="empty",batch="isolated_wire")
    public static void signalsRemainSeparateAfterReload(GameTestHelper h){
        var a=new BlockPos(0,2,0);var b=a.east();var input=a.west();var output=b.east();
        Map<BlockPos,BlockState> states=new HashMap<>();
        states.put(a,ModBlocks.ISOLATED_WIRE.get().defaultBlockState());states.put(b,ModBlocks.ISOLATED_WIRE.get().defaultBlockState());
        states.put(input,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());states.put(output,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var graph=LogicProgram.compile(states);h.assertTrue(!graph.evaluate(Map.of(input,true)).get(output),"touching isolated wires cannot transmit");
        List<CompiledCircuitElement> elements=new ArrayList<>();
        for(var e:states.entrySet())elements.add(new CompiledCircuitElement(elements.size()+1,e.getKey(),e.getKey().equals(input)?CircuitElementType.INPUT:e.getKey().equals(output)?CircuitElementType.OUTPUT:CircuitElementType.WIRE,CompiledBlockStateCodec.capture(e.getValue())));
        var network=new CompiledNetwork(1,"isolated",0,"minecraft:overworld",elements);var loaded=CompiledNetwork.load(network.save());loaded.restoreLogic();
        h.assertTrue(loaded.hasLogic(),"reload retains topology-aware graph");
        var restored=new HashMap<BlockPos,BlockState>();for(var e:loaded.getElements())restored.put(e.getPos(),e.resolveState().state().orElseThrow());
        h.assertTrue(!LogicProgram.compile(restored).evaluate(Map.of(input,true)).get(output),"reload preserves separation");
        states.put(b,ModBlocks.BASIC_WIRE.get().defaultBlockState());
        h.assertTrue(LogicProgram.compile(states).evaluate(Map.of(input,true)).get(output),"ordinary wire bridges the signal");h.succeed();
    }
}
