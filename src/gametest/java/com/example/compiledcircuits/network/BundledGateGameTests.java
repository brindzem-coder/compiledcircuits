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
    public static void absentChannelIsOmitted(GameTestHelper h){
        var r=new Rig(body(GateBodyBlock.Kind.AND));var a=r.channel(Direction.WEST,0,true);r.channel(Direction.NORTH,1,true);var out=r.channel(Direction.EAST,0,false);
        var graph=r.compile();graph.evaluate(Map.of(),Map.of(a,true));h.assertTrue(graph.faceOutput(out),"missing second channel is omitted");
        graph.evaluate(Map.of(),Map.of(a,false));h.assertTrue(!graph.faceOutput(out),"present LOW is still an operand");
        var n=new Rig(ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        var input=n.channel(Direction.WEST,0,true);var zero=n.channel(Direction.EAST,0,false);var one=n.channel(Direction.EAST,1,false);n.channel(Direction.WEST,1,true);
        var inverse=n.compile();inverse.evaluate(Map.of(),Map.of(input,true));
        h.assertTrue(!inverse.faceOutput(zero)&&inverse.faceOutput(one),"NOT preserves IDs and inverts present LOW");h.succeed();
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
        r.compile();
        r.states.put(CENTER.east(),ModBlocks.BASIC_WIRE.get().defaultBlockState());r.ports.keySet().removeIf(f->f.pos().equals(CENTER.east()));
        try{r.compile();h.fail("ordinary output on bundled gate must be rejected");}catch(IllegalArgumentException expected){h.assertTrue(expected.getMessage().contains("outputs require bundled"),expected.getMessage());}h.succeed();
    }
    @GameTest(template="empty",batch="bundled_gates")
    public static void channelIdsThroughCrossingAndGateChainToScreen(GameTestHelper h){
        var not=ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
        var r=new Rig(not);var input=r.channel(Direction.WEST,0,true);
        for(int ch=1;ch<8;ch++){
            var pos=CENTER.west(ch/2+1);r.states.put(pos,ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
            r.ports.put(new BundleFace(pos,ch%2==0?Direction.UP:Direction.DOWN),new BundleWireEntity.Port(3,Integer.toString(100+ch)));
        }
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

    @GameTest(template="empty",batch="bundled_gates")
    public static void scalarEnableControlsEveryBundledLatchChannel(GameTestHelper h){
        var r=new Rig(ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        var inputs=new BundleFace[8];var outputs=new BundleFace[8];
        for(int ch=0;ch<8;ch++){
            var left=CENTER.west(ch/2+1);var right=CENTER.east(ch/2+1);
            r.states.put(left,ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
            r.states.put(right,ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
            var face=ch%2==0?Direction.UP:Direction.DOWN;
            inputs[ch]=new BundleFace(left,face);outputs[ch]=new BundleFace(right,face);
            r.ports.put(inputs[ch],new BundleWireEntity.Port(3,Integer.toString(100+ch)));
            r.ports.put(outputs[ch],new BundleWireEntity.Port(4,Integer.toString(100+ch)));
        }
        r.states.put(CENTER.north(),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        var enable=CENTER.north(2);r.states.put(enable,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        for(boolean optimized:new boolean[]{false,true}){
            var graph=r.compile();if(optimized)graph.optimize(r.states.keySet(),true);
            Map<BundleFace,Boolean> data=new HashMap<>();
            for(int ch=0;ch<8;ch++)data.put(inputs[ch],ch%2==0);
            graph.evaluate(Map.of(enable,false),data);
            for(var out:outputs)h.assertTrue(!graph.faceOutput(out),"LOW WDE holds initial state");
            graph.evaluate(Map.of(enable,true),data);
            for(int ch=0;ch<8;ch++)h.assertTrue(graph.faceOutput(outputs[ch])==(ch%2==0),"shared WDE writes channel "+ch);
            data.replaceAll((face,value)->!value);graph.evaluate(Map.of(enable,false),data);
            for(int ch=0;ch<8;ch++)h.assertTrue(graph.faceOutput(outputs[ch])==(ch%2==0),"LOW WDE retains channel "+ch);
            var loaded=r.compile();loaded.restoreMemory(graph.saveMemory());loaded.evaluate(Map.of(enable,false),data);
            for(int ch=0;ch<8;ch++)h.assertTrue(loaded.faceOutput(outputs[ch])==(ch%2==0),"shared-WDE memory survives reload "+ch);
            graph.evaluate(Map.of(enable,true),data);
            for(int ch=0;ch<8;ch++)h.assertTrue(graph.faceOutput(outputs[ch])==(ch%2!=0),"shared WDE updates all channels "+ch);
        }h.succeed();
    }

    static boolean fold(GateBodyBlock.Kind kind,boolean... values){
        boolean all=true,any=false,parity=false;for(boolean v:values){all&=v;any|=v;parity^=v;}
        return switch(kind){case AND->all;case OR->any;case XOR->parity;case NAND->!all;case NOR->!any;case XNOR->!parity;};
    }
    @GameTest(template="empty",batch="bundled_gates")
    public static void fiveMixedInputsAllGateKinds(GameTestHelper h){
        for(var kind:GateBodyBlock.Kind.values()){
            var gate=body(kind);for(var side:new Direction[]{Direction.SOUTH,Direction.UP,Direction.DOWN})gate=GateBodyBlock.withPort(gate,side,1);
            var r=new Rig(gate);var a0=r.channel(Direction.WEST,0,true);var a1=r.channel(Direction.WEST,1,true);
            var b1=r.channel(Direction.NORTH,1,true);var c0=r.channel(Direction.SOUTH,0,true);
            var o0=r.channel(Direction.EAST,0,false);var o1=r.channel(Direction.EAST,1,false);
            r.states.put(CENTER.above(),ModBlocks.BASIC_WIRE.get().defaultBlockState());r.states.put(CENTER.below(),ModBlocks.BASIC_WIRE.get().defaultBlockState());
            var e=CENTER.above(2);var f=CENTER.below(2);
            r.states.put(e,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());r.states.put(f,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
            var graph=r.compile();
            for(int optimize=0;optimize<2;optimize++){
                if(optimize==1)graph.optimize(r.states.keySet(),true);
                for(int bits=0;bits<64;bits++){
                    boolean av=(bits&1)!=0,bv=(bits&2)!=0,cv=(bits&4)!=0,dv=(bits&8)!=0,ev=(bits&16)!=0,fv=(bits&32)!=0;
                    graph.evaluate(Map.of(e,ev,f,fv),Map.of(a0,av,a1,bv,b1,cv,c0,dv));
                    h.assertTrue(graph.faceOutput(o0)==fold(kind,av,dv,ev,fv),kind+" channel 100 mixed inputs "+bits);
                    h.assertTrue(graph.faceOutput(o1)==fold(kind,bv,cv,ev,fv),kind+" channel 101 mixed inputs "+bits);
                }
            }
        }h.succeed();
    }
    @GameTest(template="empty",batch="bundled_gates")
    public static void sharedDataWithPerChannelEnable(GameTestHelper h){
        var r=new Rig(ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        var e0=r.channel(Direction.NORTH,0,true);var e1=r.channel(Direction.NORTH,1,true);
        var q0=r.channel(Direction.EAST,0,false);var q1=r.channel(Direction.EAST,1,false);
        r.states.put(CENTER.west(),ModBlocks.BASIC_WIRE.get().defaultBlockState());var d=CENTER.west(2);
        r.states.put(d,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        for(boolean optimized:new boolean[]{false,true}){
            var graph=r.compile();if(optimized)graph.optimize(r.states.keySet(),true);
            graph.evaluate(Map.of(d,true),Map.of(e0,true,e1,false));
            h.assertTrue(graph.faceOutput(q0)&&!graph.faceOutput(q1),"shared D writes only enabled ID");
            graph.evaluate(Map.of(d,true),Map.of(e0,false,e1,true));
            h.assertTrue(graph.faceOutput(q0)&&graph.faceOutput(q1),"other ID can record shared D");
            var loaded=r.compile();loaded.restoreMemory(graph.saveMemory());loaded.evaluate(Map.of(d,false),Map.of(e0,true,e1,false));
            h.assertTrue(!loaded.faceOutput(q0)&&loaded.faceOutput(q1),"selective clearing after memory reload");
        }h.succeed();
    }
    @GameTest(template="empty",batch="bundled_gates")
    public static void channelPresencePropagatesForwardOnly(GameTestHelper h){
        var r=new Rig(body(GateBodyBlock.Kind.AND));var a=r.channel(Direction.WEST,0,true);r.channel(Direction.NORTH,1,true);
        r.states.put(CENTER.east(),ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
        var not=ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
        r.states.put(CENTER.east(2),not);r.states.put(CENTER.east(3),ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
        var out=new BundleFace(CENTER.east(3),Direction.UP);r.ports.put(out,new BundleWireEntity.Port(4,"100"));
        var graph=r.compile();graph.evaluate(Map.of(),Map.of(a,true));h.assertTrue(!graph.faceOutput(out),"AND omits absent operand before downstream NOT");
        graph.evaluate(Map.of(),Map.of(a,false));h.assertTrue(graph.faceOutput(out),"present LOW remains in upstream AND");
        r.ports.put(new BundleFace(CENTER.east(3),Direction.DOWN),new BundleWireEntity.Port(4,"999"));
        try{r.compile();h.fail("consumer must not invent a source ID upstream");}catch(IllegalArgumentException expected){h.assertTrue(expected.getMessage().contains("999")&&expected.getMessage().contains("has no input"),expected.getMessage());}h.succeed();
    }

    @GameTest(template="empty",batch="bundled_gates")
    public static void ordinaryInputsBroadcastToEveryOutputId(GameTestHelper h){
        for(var kind:GateBodyBlock.Kind.values()){
            var r=new Rig(body(kind));var out0=r.channel(Direction.EAST,0,false);var out1=r.channel(Direction.EAST,1,false);
            r.states.put(CENTER.west(),ModBlocks.BASIC_WIRE.get().defaultBlockState());r.states.put(CENTER.north(),ModBlocks.BASIC_WIRE.get().defaultBlockState());
            var a=CENTER.west(2);var b=CENTER.north(2);
            r.states.put(a,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());r.states.put(b,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
            var graph=r.compile();
            for(int optimized=0;optimized<2;optimized++){
                if(optimized==1)graph.optimize(r.states.keySet(),true);
                for(int bits=0;bits<4;bits++){
                    boolean av=(bits&1)!=0,bv=(bits&2)!=0;graph.evaluate(Map.of(a,av,b,bv));
                    h.assertTrue(graph.faceOutput(out0)==expected(kind,av,bv)&&graph.faceOutput(out1)==expected(kind,av,bv),kind+" broadcasts equal results to both IDs");
                }
            }
        }h.succeed();
    }
    @GameTest(template="empty",batch="bundled_gates")
    public static void scalarNotBroadcastThroughGateToScreen(GameTestHelper h){
        var not=ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);var r=new Rig(not);
        var input=CENTER.west();r.states.put(input,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        r.states.put(CENTER.east(),ModBlocks.BUNDLED_WIRE.get().defaultBlockState());r.states.put(CENTER.east(2),not);
        r.states.put(CENTER.east(3),ModBlocks.BUNDLED_WIRE.get().defaultBlockState());var screen=CENTER.east(4);
        r.states.put(screen,ModBlocks.COLOR_SCREEN.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        r.ports.put(new BundleFace(CENTER.east(3),Direction.EAST),new BundleWireEntity.Port(6,"100"));
        var graph=r.compile();graph.optimize(r.states.keySet(),true);
        graph.evaluate(Map.of(input,true));h.assertTrue(graph.screenColors().get(screen)==255,"all eight requested screen IDs cross two NOTs");
        graph.evaluate(Map.of(input,false));h.assertTrue(graph.screenColors().get(screen)==0,"all eight screen bits clear together");h.succeed();
    }
    @GameTest(template="empty",batch="bundled_gates")
    public static void scalarLatchBroadcastKeepsMemory(GameTestHelper h){
        var r=new Rig(ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        var q0=r.channel(Direction.EAST,0,false);var q1=r.channel(Direction.EAST,1,false);
        var d=CENTER.west();var e=CENTER.north();r.states.put(d,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());r.states.put(e,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        var graph=r.compile();graph.evaluate(Map.of(d,true,e,true));
        h.assertTrue(graph.faceOutput(q0)&&graph.faceOutput(q1),"ordinary D and WDE write all IDs");
        var loaded=r.compile();loaded.restoreMemory(graph.saveMemory());loaded.optimize(r.states.keySet(),true);loaded.evaluate(Map.of(d,false,e,false));
        h.assertTrue(loaded.faceOutput(q0)&&loaded.faceOutput(q1),"all IDs hold after reload");
        loaded.evaluate(Map.of(d,false,e,true));h.assertTrue(!loaded.faceOutput(q0)&&!loaded.faceOutput(q1),"all IDs clear together");h.succeed();
    }

}
