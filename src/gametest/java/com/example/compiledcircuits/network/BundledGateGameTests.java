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
public class BundledGateGameTests {
    static final BlockPos CENTER=new BlockPos(5,3,5);
    static class Rig {
        final Map<BlockPos,BlockState> states=new HashMap<>();
        final Map<BundleFace,BundleWireEntity.Port> ports=new HashMap<>();
        Rig(BlockState gate){states.put(CENTER,gate);}
        BundleFace channel(Direction side,int channel,boolean input){
            var pos=CENTER.relative(side);states.put(pos,ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
            var face=new BundleFace(pos,channel==0?Direction.UP:Direction.DOWN);
            ports.put(face,new BundleWireEntity.Port(input?3:4,Integer.toString(100+channel)));return face;
        }
        LogicProgram compile(){return LogicProgram.compile(states,Map.of(),Map.of(),Map.of(),ports,List.of());}
    }
    static BlockState body(GateBodyBlock.Kind kind){
        var block=switch(kind){case AND->ModBlocks.AND_GATE;case OR->ModBlocks.OR_GATE;case NAND->ModBlocks.NAND_GATE;case NOR->ModBlocks.NOR_GATE;case XOR->ModBlocks.XOR_GATE;case XNOR->ModBlocks.XNOR_GATE;};
        var state=GateBodyBlock.withPort(block.get().defaultBlockState(),Direction.WEST,1);
        state=GateBodyBlock.withPort(state,Direction.NORTH,1);return GateBodyBlock.withPort(state,Direction.EAST,2);
    }
    static boolean expected(GateBodyBlock.Kind kind,boolean a,boolean b){return switch(kind){case AND->a&&b;case OR->a||b;case NAND->!(a&&b);case NOR->!(a||b);case XOR->a^b;case XNOR->a==b;};}
    @GameTest(template="empty",batch="bundled_gates")
    public static void allTruthTablesAndOptimization(GameTestHelper h){
        for(var kind:GateBodyBlock.Kind.values()){
            var r=new Rig(body(kind));var a=new BundleFace[2];var b=new BundleFace[2];var out=new BundleFace[2];
            for(int ch=0;ch<2;ch++){a[ch]=r.channel(Direction.WEST,ch,true);b[ch]=r.channel(Direction.NORTH,ch,true);out[ch]=r.channel(Direction.EAST,ch,false);}
            var graph=r.compile();
            for(int optimized=0;optimized<2;optimized++){
                if(optimized==1)graph.optimize(r.states.keySet(),true);
                for(int bits=0;bits<16;bits++){
                    graph.evaluate(Map.of(),Map.of(a[0],(bits&1)!=0,b[0],(bits&2)!=0,a[1],(bits&4)!=0,b[1],(bits&8)!=0));
                    
                    for(int ch=0;ch<2;ch++)h.assertTrue(graph.faceOutput(out[ch])==expected(kind,(bits&(1<<(ch*2)))!=0,(bits&(2<<(ch*2)))!=0),kind+" independent channel "+ch+" pattern "+bits);
                }
            }
        }h.succeed();
    }
    @GameTest(template="empty",batch="bundled_gates")
    public static void absentChannelIsLow(GameTestHelper h){
        var r=new Rig(body(GateBodyBlock.Kind.AND));var a=r.channel(Direction.WEST,0,true);r.channel(Direction.NORTH,1,true);var out=r.channel(Direction.EAST,0,false);
        var graph=r.compile();graph.evaluate(Map.of(),Map.of(a,true));h.assertTrue(!graph.faceOutput(out),"missing second channel is LOW, not an omitted AND operand");
        var n=new Rig(ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        var input=n.channel(Direction.WEST,0,true);var zero=n.channel(Direction.EAST,0,false);var one=n.channel(Direction.EAST,1,false);
        var inverse=n.compile();inverse.evaluate(Map.of(),Map.of(input,true));
        h.assertTrue(!inverse.faceOutput(zero)&&inverse.faceOutput(one),"NOT preserves IDs and inverts absent input LOW");h.succeed();
    }
    @GameTest(template="empty",batch="bundled_gates")
    public static void latchHasIndependentEnableAndMemory(GameTestHelper h){
        var r=new Rig(ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        var d=new BundleFace[2];var en=new BundleFace[2];var q=new BundleFace[2];
        for(int ch=0;ch<2;ch++){d[ch]=r.channel(Direction.WEST,ch,true);en[ch]=r.channel(Direction.NORTH,ch,true);q[ch]=r.channel(Direction.EAST,ch,false);}
        var graph=r.compile();graph.optimize(r.states.keySet(),true);
        graph.evaluate(Map.of(),Map.of(d[0],true,d[1],true,en[0],true,en[1],false));
        h.assertTrue(graph.faceOutput(q[0])&&!graph.faceOutput(q[1]),"WDE affects only matching ID");
        graph.evaluate(Map.of(),Map.of(d[0],false,d[1],true,en[0],false,en[1],true));
        h.assertTrue(graph.faceOutput(q[0])&&graph.faceOutput(q[1]),"channels keep separate memory");
        graph.evaluate(Map.of(),Map.of(d[0],false,d[1],false,en[0],true,en[1],false));
        h.assertTrue(!graph.faceOutput(q[0])&&graph.faceOutput(q[1]),"clearing one latch does not clear another");
        var loaded=r.compile();loaded.restoreMemory(graph.saveMemory());loaded.evaluate(Map.of(),Map.of());
        h.assertTrue(!loaded.faceOutput(q[0])&&loaded.faceOutput(q[1]),"channel memory survives graph reload");h.succeed();
    }
    @GameTest(template="empty",batch="bundled_gates")
    public static void physicalConnectionAndMixedWires(GameTestHelper h){
        var r=new Rig(body(GateBodyBlock.Kind.OR));r.channel(Direction.WEST,0,true);r.channel(Direction.EAST,0,false);
        var pos=h.absolutePos(new BlockPos(1,2,1));var gate=r.states.get(CENTER);var cable=ModBlocks.BUNDLED_WIRE.get().defaultBlockState();
        h.getLevel().setBlock(pos,gate,3);h.getLevel().setBlock(pos.west(),cable,3);
        h.assertTrue(CircuitConnections.connects(h.getLevel(),pos,gate,cable,Direction.WEST),"world discovery includes gate and bundle");
        r.states.put(CENTER.north(),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        try{r.compile();h.fail("mixed gate should require explicit conversion");}catch(IllegalArgumentException expected){h.assertTrue(expected.getMessage().contains("Do not mix"),expected.getMessage());}h.succeed();
    }
    @GameTest(template="empty",batch="bundled_gates")
    public static void channelIdsThroughCrossingAndGateChainToScreen(GameTestHelper h){
        var not=ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
        var r=new Rig(not);var input=r.channel(Direction.WEST,0,true);
        var crossing=ModBlocks.CROSSING_WIRE.get().defaultBlockState().setValue(CrossingWireBlock.LANES[Direction.Axis.X.ordinal()],3).setValue(CrossingWireBlock.LANES[Direction.Axis.Z.ordinal()],1);
        r.states.put(CENTER.east(),crossing);
        r.states.put(CENTER.east(2),ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
        r.states.put(CENTER.east(3),not);
        r.states.put(CENTER.east(4),ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
        var screen=CENTER.east(5);
        r.states.put(screen,ModBlocks.COLOR_SCREEN.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        r.ports.put(new BundleFace(CENTER.east(4),Direction.EAST),new BundleWireEntity.Port(6,"100"));
        var graph=r.compile();graph.optimize(r.states.keySet(),true);
        graph.evaluate(Map.of(),Map.of(input,true));
        h.assertTrue(graph.screenColors().get(screen)==1,"screen receives original ID 100 after two NOTs and crossing");
        graph.evaluate(Map.of(),Map.of(input,false));
        h.assertTrue(graph.screenColors().get(screen)==0,"absent channels remain LOW after two inversions");h.succeed();
    }

}
