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
        h.assertTrue(NetworkWireBlock.face(left,Direction.EAST)==0&&NetworkWireBlock.face(right,Direction.WEST)==0,"no visual arms between copies");
        h.assertTrue(!CircuitConnections.connects(level,pos,left,right,Direction.EAST),"world discovery also isolates copies");
        level.setBlock(pos.east(),basic,3);left=Block.updateFromNeighbourShapes(wire,level,pos);
        h.assertTrue(NetworkWireBlock.face(left,Direction.EAST)==1,"normal wire restores arm");h.succeed();
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
    @GameTest(template="empty",batch="isolated_wire")
    public static void portsPreserveIsolation(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,3,1));
        var wire=ModBlocks.ISOLATED_WIRE.get().defaultBlockState();
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        level.setBlock(pos,wire,3);level.setBlock(pos.east(),wire,3);
        for(var item:List.of(com.example.compiledcircuits.registry.ModItems.NETWORK_INPUT.get(),com.example.compiledcircuits.registry.ModItems.NETWORK_OUTPUT.get())){
            player.setItemInHand(hand,new net.minecraft.world.item.ItemStack(item));
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false);
            var context=new net.minecraft.world.item.context.UseOnContext(player,hand,hit);
            h.assertTrue(item.useOn(context).consumesAction(),"port installs");
            var state=level.getBlockState(pos);
            h.assertTrue(state.is(ModBlocks.ISOLATED_WIRE.get()),"port preserves isolated block");
            h.assertTrue(NetworkWireBlock.face(state,Direction.UP)>=2,"port is present");
            h.assertTrue(NetworkWireBlock.face(state,Direction.EAST)==0,"no arm to adjacent isolated wire");
            h.assertTrue(!CircuitConnections.connects(state,wire,Direction.EAST),"no logical connection to adjacent isolated wire");
            var element=new CompiledCircuitElement(1,pos,CircuitElementType.WIRE,CompiledBlockStateCodec.capture(state));
            h.assertTrue(element.resolveState().state().orElseThrow().equals(state),"snapshot retains port and insulation");
            var loaded=CompiledNetwork.load(new CompiledNetwork(1,"isolated ports",0,"minecraft:overworld",List.of(element)).save());
            h.assertTrue(loaded.directPorts().size()==1&&loaded.directPorts().get(0).side()==Direction.UP,"transport discovers isolated wire port after reload");
            h.assertTrue(CompiledBlockStateMatcher.match(element,ModBlocks.NETWORK_WIRE.get().withPropertiesOf(state),false)==CompiledBlockStateMatcher.Match.BLOCK_MISMATCH,"ordinary replacement is not equivalent");
            item.useOn(context);
            h.assertTrue(level.getBlockState(pos).is(ModBlocks.ISOLATED_WIRE.get())&&NetworkWireBlock.face(level.getBlockState(pos),Direction.UP)==0,"removing port preserves isolation");
        }
        var props=new TreeMap<String,String>();for(var side:Direction.values())props.put(side.getName(),"true");
        var migrated=CompiledBlockStateCodec.readAndResolve(CompiledBlockStateCodec.write(new CompiledBlockStateSnapshot("compiledcircuits:isolated_wire",props)),"compiledcircuits:isolated_wire");
        h.assertTrue(migrated.status()==CompiledBlockStateCodec.Status.EXACT,"old snapshots migrate");
        for(var side:Direction.values())h.assertTrue(NetworkWireBlock.face(migrated.state().orElseThrow(),side)==1,"old arms retained without ports");
        h.succeed();
    }
}
