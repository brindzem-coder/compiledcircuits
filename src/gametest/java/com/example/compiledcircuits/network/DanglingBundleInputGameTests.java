package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class DanglingBundleInputGameTests {
    static final BlockPos C=new BlockPos(0,3,0);
    static CompiledNetwork network(int id,Map<BlockPos,BlockState> states,BlockPos cable,Map<Direction,BundleWireEntity.Port> faces){
        var elements=new ArrayList<CompiledCircuitElement>();
        states.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            var block=e.getValue().getBlock();var type=block instanceof InputEndpointBlock?CircuitElementType.INPUT:block instanceof OutputEndpointBlock?CircuitElementType.OUTPUT:block instanceof NotGateBlock?CircuitElementType.GATE:CircuitElementType.WIRE;
            var tag=new CompiledCircuitElement(elements.size()+1,e.getKey(),type,CompiledBlockStateCodec.capture(e.getValue())).save();if(e.getKey().equals(cable))tag.put("bundleFaces",BundleWireEntity.encode(faces));elements.add(CompiledCircuitElement.load(tag));
        });
        var n=new CompiledNetwork(id,"dangling-input",0,"minecraft:overworld",elements);n.restoreLogic();n.runtimeState(true,true);return n;
    }
    static Map<Direction,BundleWireEntity.Port> faces(int kind,int outputKind){
        return Map.of(Direction.WEST,new BundleWireEntity.Port(kind,kind==9?"0=9":"9"),Direction.UP,new BundleWireEntity.Port(1,"9"),Direction.EAST,new BundleWireEntity.Port(outputKind,"9"));
    }
    @GameTest(template="empty",batch="dangling_bundle")
    public static void openInputLowOrAndReconnect(GameTestHelper h){
        for(int kind:List.of(3,9))for(boolean optimized:List.of(false,true)){
            var target=network(1,Map.of(C,ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),C.above(),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),C.east(),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST),C.east(2),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState()),C,faces(kind,2));
            target=CompiledNetwork.load(target.save());target.restoreLogic();target.runtimeState(true,true);if(optimized)target.enableOptimization();
            var links=new DirectLinks(new NetworkSavedData(),n->{});links.add(target);
            links.update(target,Map.of(C.above(),false));DirectLinkGameTests.settle(links);h.assertTrue(target.getEffectiveSignal(C.east(2))==15,"open input is LOW and can be inverted");
            links.update(target,Map.of(C.above(),true));DirectLinkGameTests.settle(links);h.assertTrue(target.getEffectiveSignal(C.east(2))==0&&target.getRuntimeStatus().equals("READY"),"local OR source drives the same ID while external input is open");
            var source=network(2,Map.of(C.west(),ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),C.west(2),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState()),C.west(),Map.of(Direction.WEST,new BundleWireEntity.Port(1,kind==9?"0":"9"),Direction.EAST,new BundleWireEntity.Port(kind==9?8:4,kind==9?"0=9":"9")));
            links.validateCompilation(source);links.add(source);links.update(target,Map.of(C.above(),false));links.update(source,Map.of(C.west(2),true));DirectLinkGameTests.settle(links);
            h.assertTrue(target.getEffectiveSignal(C.east(2))==0,"connected HIGH feeds the inverter");
            links.remove(source);DirectLinkGameTests.settle(links);h.assertTrue(target.getEffectiveSignal(C.east(2))==15,"disconnect returns to LOW without stale HIGH or group shutdown");
        }h.succeed();
    }
    @GameTest(template="empty",batch="dangling_bundle")
    public static void openInputDoesNotDisableTransitivePeers(GameTestHelper h){
        for(int kind:List.of(3,9)){
            var a=network(1,Map.of(C,ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),C.above(),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState()),C,faces(kind,4));
            var bridge=NetworkWireBlock.withFace(DirectLinkGameTests.port(Direction.WEST,2),Direction.EAST,3);
            var b=DirectLinkGameTests.network(2,Map.of(C.east(),bridge));
            var c=DirectLinkGameTests.network(3,Map.of(C.east(2),DirectLinkGameTests.port(Direction.WEST,2),C.east(3),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState()));
            var links=new DirectLinks(new NetworkSavedData(),n->{});for(var n:List.of(a,b,c)){links.add(n);links.update(n,Map.of());}
            for(boolean high:List.of(true,false,true)){links.update(a,Map.of(C.above(),high));DirectLinkGameTests.settle(links);h.assertTrue(c.getEffectiveSignal(C.east(3))==(high?15:0),"open bundle input does not disable networks connected through a bridge");}
        }h.succeed();
    }
}
