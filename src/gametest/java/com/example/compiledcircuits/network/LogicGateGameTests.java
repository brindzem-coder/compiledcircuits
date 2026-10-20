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
public class LogicGateGameTests {
    private static BlockPos p(int x, int z) { return new BlockPos(x, 0, z); }
    private static void put(Map<BlockPos, BlockState> s, int x, int z, Block b) { s.put(p(x,z), b.defaultBlockState()); }
    private static Map<BlockPos, BlockState> gate(Block body) {
        Map<BlockPos, BlockState> s = new LinkedHashMap<>();
        put(s,0,0,body); put(s,-1,0,ModBlocks.GATE_INPUT.get()); put(s,0,-1,ModBlocks.GATE_INPUT.get());
        put(s,-2,0,ModBlocks.INPUT_ENDPOINT.get()); put(s,0,-2,ModBlocks.INPUT_ENDPOINT.get());
        put(s,1,0,ModBlocks.GATE_OUTPUT.get()); put(s,2,0,ModBlocks.OUTPUT_ENDPOINT.get());
        return s;
    }
    private static void rejects(GameTestHelper h, Map<BlockPos, BlockState> states, String reason) {
        try { LogicProgram.compile(states); throw new AssertionError("Expected " + reason); }
        catch (IllegalArgumentException expected) { h.assertTrue(expected.getMessage().contains(reason), expected.getMessage()); }
    }
    @GameTest(template="empty",batch="logic_gates",timeoutTicks=100)
    public static void truthTablesAndPortIsolation(GameTestHelper h) {
        for (Block body : new Block[]{ModBlocks.AND_GATE.get(),ModBlocks.OR_GATE.get()}) {
            var states = gate(body);
            // This passive output touches only the input port, and must never receive backfeed.
            states.put(new BlockPos(-1,-1,0),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
            var program = LogicProgram.compile(states);
            for (boolean a : new boolean[]{false,true}) for (boolean b : new boolean[]{false,true}) {
                var result = program.evaluate(Map.of(p(-2,0),a,p(0,-2),b));
                h.assertTrue(result.get(p(2,0)) == (body == ModBlocks.AND_GATE.get() ? a && b : a || b),"gate truth table");
                h.assertTrue(!result.get(new BlockPos(-1,-1,0)),"input port cannot bridge independent buses");
            }
        }
        var input=ModBlocks.GATE_INPUT.get().defaultBlockState(); var output=ModBlocks.GATE_OUTPUT.get().defaultBlockState();
        for(Direction d:Direction.values()) {
            h.assertTrue(!CircuitConnections.connects(input,input,d),"input/input disconnected");
            h.assertTrue(!CircuitConnections.connects(output,output,d),"output/output disconnected");
            h.assertTrue(CircuitConnections.connects(input,output,d),"input/output connected on every face");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="logic_gates",timeoutTicks=100)
    public static void sharedOutputsAndNotChain(GameTestHelper h) {
        Map<BlockPos,BlockState> s=new LinkedHashMap<>();
        put(s,0,0,ModBlocks.INPUT_ENDPOINT.get()); put(s,4,0,ModBlocks.INPUT_ENDPOINT.get());
        s.put(p(1,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        s.put(p(3,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.WEST));
        put(s,2,0,ModBlocks.BASIC_WIRE.get()); put(s,2,1,ModBlocks.OUTPUT_ENDPOINT.get());
        var program=LogicProgram.compile(s);
        for(boolean a:new boolean[]{false,true})for(boolean b:new boolean[]{false,true})
            h.assertTrue(program.evaluate(Map.of(p(0,0),a,p(4,0),b)).get(p(2,1))==(!a||!b),"multiple drivers OR on shared bus");
        s.clear(); put(s,-1,0,ModBlocks.INPUT_ENDPOINT.get());
        for(int i=0;i<301;i++)s.put(p(i,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        put(s,301,0,ModBlocks.OUTPUT_ENDPOINT.get()); program=LogicProgram.compile(s);
        h.assertTrue(program.evaluate(Map.of(p(-1,0),false)).get(p(301,0)),"301 NOTs propagate within one evaluation");
        h.assertTrue(!program.evaluate(Map.of(p(-1,0),true)).get(p(301,0)),"no retained signal in chain");
        h.succeed();
    }
    @GameTest(template="empty",batch="logic_gates",timeoutTicks=100)
    public static void invalidTopologyAndMultiBlockBody(GameTestHelper h) {
        var s=gate(ModBlocks.AND_GATE.get());
        put(s,-1,1,ModBlocks.OR_GATE.get()); rejects(h,s,"more than one gate");
        s=gate(ModBlocks.AND_GATE.get()); s.remove(p(0,0)); rejects(h,s,"must touch");
        s=gate(ModBlocks.AND_GATE.get()); s.remove(p(1,0)); rejects(h,s,"at least one");
        s.clear(); s.put(p(0,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        for(BlockPos pos:List.of(p(-1,0),p(-1,1),p(0,1),p(1,1),p(1,0)))s.put(pos,ModBlocks.BASIC_WIRE.get().defaultBlockState());
        rejects(h,s,"Feedback cycle");
        s=gate(ModBlocks.AND_GATE.get());
        // One port touches two blocks of a single contiguous body: valid.
        put(s,0,1,ModBlocks.AND_GATE.get()); put(s,-1,1,ModBlocks.AND_GATE.get());
        h.assertTrue(LogicProgram.compile(s).evaluate(Map.of(p(-2,0),true,p(0,-2),true)).get(p(2,0)),"multi-block body is one gate");
        h.succeed();
    }
    @GameTest(template="empty",batch="logic_gates",timeoutTicks=100)
    public static void persistenceAndFunctionalState(GameTestHelper h) {
        var states=gate(ModBlocks.AND_GATE.get()); List<CompiledCircuitElement> elements=new ArrayList<>();
        for(var item:states.entrySet()) {
            Block b=item.getValue().getBlock();
            CircuitElementType type=b instanceof GateBodyBlock?CircuitElementType.GATE:b instanceof GatePortBlock port?(port.isInput()?CircuitElementType.GATE_INPUT:CircuitElementType.GATE_OUTPUT):b instanceof InputEndpointBlock?CircuitElementType.INPUT:CircuitElementType.OUTPUT;
            elements.add(new CompiledCircuitElement(elements.size()+1,item.getKey(),type,CompiledBlockStateCodec.capture(item.getValue())));
        }
        var original=new CompiledNetwork(1,"logic",0,"minecraft:overworld",elements);
        var loaded=CompiledNetwork.load(original.save()); loaded.restoreLogic();
        loaded.evaluateLogic(Map.of(p(-2,0),true,p(0,-2),true));
        h.assertTrue(loaded.getEffectiveSignal(p(2,0))==0,"saved network cannot emit before ready");
        loaded.runtimeState(true,true);
        h.assertTrue(loaded.getEffectiveSignal(p(2,0))==15,"reconstructed program emits result");
        loaded.runtimeState(true,false);
        h.assertTrue(loaded.getEffectiveSignal(p(2,0))==0,"unavailable inputs suppress output");
        var state=ModBlocks.NOT_GATE.get().defaultBlockState();
        var element=new CompiledCircuitElement(1,BlockPos.ZERO,CircuitElementType.GATE,CompiledBlockStateCodec.capture(state));
        h.assertTrue(CompiledBlockStateMatcher.match(element,state.setValue(EndpointBlock.FACING,Direction.EAST),false)==CompiledBlockStateMatcher.Match.STATE_MISMATCH,"NOT orientation always checked");
        h.succeed();
    }
    @GameTest(template="empty",batch="logic_runtime",timeoutTicks=120)
    public static void independentOutputsInServerRuntime(GameTestHelper h) {
        var level=h.getLevel(); var server=level.getServer(); var storage=server.overworld().getDataStorage();
        var original=NetworkSavedData.get(server); var data=new NetworkSavedData();
        storage.set("compiledcircuits_networks",data);
        BlockPos base=h.absolutePos(new BlockPos(3,4,3)), input=base, inverted=base.offset(4,0,0), direct=base.offset(1,0,2);
        Map<BlockPos,BlockState> old=new LinkedHashMap<>(), layout=new LinkedHashMap<>(); Set<Long> forced=new HashSet<>();
        layout.put(input,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        layout.put(base.offset(1,0,0),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        layout.put(base.offset(2,0,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        layout.put(base.offset(3,0,0),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        layout.put(inverted,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        layout.put(base.offset(1,0,1),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        layout.put(direct,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        Runnable cleanup=()->{
            for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());
            old.forEach((pos,state)->level.setBlock(pos,state,3));
            for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);
            storage.set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();
        };
        for(var pos:layout.keySet())for(long c:RuntimeSignalReader.dependencyChunks(pos)) {
            if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}
            level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));
        }
        old.put(input.below(),level.getBlockState(input.below()));level.setBlock(input.below(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        layout.forEach((pos,state)->{old.put(pos,level.getBlockState(pos));level.setBlock(pos,state,3);});
        List<CompiledCircuitElement> elements=new ArrayList<>();
        for(var pos:layout.keySet()) {
            var state=level.getBlockState(pos);var b=state.getBlock();
            var type=b instanceof NotGateBlock?CircuitElementType.GATE:b instanceof InputEndpointBlock?CircuitElementType.INPUT:b instanceof OutputEndpointBlock?CircuitElementType.OUTPUT:CircuitElementType.WIRE;
            elements.add(new CompiledCircuitElement(elements.size()+1,pos,type,CompiledBlockStateCodec.capture(state)));
        }
        var network=new CompiledNetwork(1,"runtime-logic",0,level.dimension().location().toString(),elements); data.addNetwork(network);
        new Runnable(){int attempts;public void run(){try{
            NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
            if(!network.getRuntimeStatus().equals("READY") && ++attempts<80){h.runAfterDelay(1,this);return;}
            h.assertTrue(level.getBlockState(inverted).getSignal(level,inverted,Direction.UP)==15,"NOT initially HIGH");
            h.assertTrue(level.getBlockState(direct).getSignal(level,direct,Direction.UP)==0,"direct output initially LOW");
            level.setBlock(input.below(),net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            h.assertTrue(level.getBlockState(inverted).getSignal(level,inverted,Direction.UP)==0,"NOT changes in same tick");
            h.assertTrue(level.getBlockState(direct).getSignal(level,direct,Direction.UP)==15,"direct output changes independently in same tick");
            level.setBlock(input.below(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(network.getEffectiveSignal(inverted)==15 && network.getEffectiveSignal(direct)==0,"both outputs switch back");
            cleanup.run();h.succeed();
        }catch(Throwable error){cleanup.run();throw error;}}}.run();
    }
}
