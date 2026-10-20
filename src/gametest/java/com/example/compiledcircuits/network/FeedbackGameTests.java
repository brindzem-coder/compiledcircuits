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
public class FeedbackGameTests {
    private static BlockPos p(int x,int z){return new BlockPos(x,0,z);}
    private static void put(Map<BlockPos,BlockState> s,int x,int z,Block b){s.put(p(x,z),b.defaultBlockState());}
    private static Map<BlockPos,BlockState> latch() {
        Map<BlockPos,BlockState> s=new LinkedHashMap<>();
        put(s,0,0,ModBlocks.NOR_GATE.get());put(s,4,0,ModBlocks.NOR_GATE.get());
        put(s,-1,0,ModBlocks.GATE_INPUT.get());put(s,-2,0,ModBlocks.INPUT_ENDPOINT.get());
        put(s,5,0,ModBlocks.GATE_INPUT.get());put(s,6,0,ModBlocks.INPUT_ENDPOINT.get());
        put(s,1,0,ModBlocks.GATE_OUTPUT.get());put(s,2,0,ModBlocks.BASIC_WIRE.get());put(s,3,0,ModBlocks.GATE_INPUT.get());
        put(s,4,1,ModBlocks.GATE_OUTPUT.get());put(s,0,1,ModBlocks.GATE_INPUT.get());
        for(int x=0;x<=4;x++)put(s,x,2,ModBlocks.BASIC_WIRE.get());
        put(s,2,-1,ModBlocks.OUTPUT_ENDPOINT.get());put(s,4,3,ModBlocks.OUTPUT_ENDPOINT.get());
        return s;
    }
    private static Map<BlockPos,BlockState> loop(Block body) {
        Map<BlockPos,BlockState> s=new LinkedHashMap<>();
        put(s,0,0,body);put(s,-1,0,ModBlocks.GATE_INPUT.get());put(s,-2,0,ModBlocks.INPUT_ENDPOINT.get());
        put(s,1,0,ModBlocks.GATE_OUTPUT.get());put(s,1,1,ModBlocks.BASIC_WIRE.get());put(s,0,1,ModBlocks.GATE_INPUT.get());
        put(s,2,0,ModBlocks.OUTPUT_ENDPOINT.get());return s;
    }
    private static CompiledNetwork network(Map<BlockPos,BlockState> s) {
        List<CompiledCircuitElement> elements=new ArrayList<>();
        for(var item:s.entrySet()){
            Block b=item.getValue().getBlock();
            var type=b instanceof GateBodyBlock||b instanceof NotGateBlock?CircuitElementType.GATE:b instanceof GatePortBlock port?(port.isInput()?CircuitElementType.GATE_INPUT:CircuitElementType.GATE_OUTPUT):b instanceof InputEndpointBlock?CircuitElementType.INPUT:b instanceof OutputEndpointBlock?CircuitElementType.OUTPUT:CircuitElementType.WIRE;
            elements.add(new CompiledCircuitElement(elements.size()+1,item.getKey(),type,CompiledBlockStateCodec.capture(item.getValue())));
        }
        var n=new CompiledNetwork(1,"feedback",0,"minecraft:overworld",elements);n.restoreLogic();return n;
    }
    @GameTest(template="empty",batch="feedback_logic",timeoutTicks=100)
    public static void norLatchSetResetHoldAndReload(GameTestHelper h) {
        var s=latch();var program=LogicProgram.compile(s);
        var hold=Map.of(p(-2,0),false,p(6,0),false);
        var initial=program.evaluate(hold);
        h.assertTrue(initial.get(p(2,-1))!=initial.get(p(4,3)),"uninitialized latch settles to complementary outputs");
        for(int repeat=0;repeat<10;repeat++) {
            var set=program.evaluate(Map.of(p(-2,0),true,p(6,0),false));
            h.assertTrue(!set.get(p(2,-1))&&set.get(p(4,3)),"set selects state");
            h.assertTrue(program.evaluate(hold).equals(set),"release set retains state");
            var memory=program.saveMemory();
            List<BlockPos> keys=new ArrayList<>(s.keySet());Collections.reverse(keys);
            Map<BlockPos,BlockState> reversed=new LinkedHashMap<>();for(var key:keys)reversed.put(key,s.get(key));
            var restored=LogicProgram.compile(reversed);restored.restoreMemory(memory);
            h.assertTrue(restored.evaluate(hold).equals(set),"saved state survives different map iteration order");
            var reset=program.evaluate(Map.of(p(-2,0),false,p(6,0),true));
            h.assertTrue(reset.get(p(2,-1))&&!reset.get(p(4,3)),"reset selects opposite state");
            h.assertTrue(program.evaluate(hold).equals(reset),"release reset retains state");
        }
        var n=network(s);n.runtimeState(true,true);n.evaluateLogic(Map.of(p(-2,0),true,p(6,0),false));n.evaluateLogic(hold);
        var loaded=CompiledNetwork.load(n.save());loaded.restoreLogic();
        h.assertTrue(loaded.getEffectiveSignal(p(4,3))==0,"reload never emits before validation");
        loaded.evaluateLogic(hold);loaded.runtimeState(true,true);
        h.assertTrue(loaded.getEffectiveSignal(p(4,3))==15&&loaded.getEffectiveSignal(p(2,-1))==0,"NBT retains latch state");
        h.succeed();
    }
    @GameTest(template="empty",batch="feedback_logic",timeoutTicks=100)
    public static void controlledOscillationFailsClosedAndRecovers(GameTestHelper h) {
        var s=loop(ModBlocks.NAND_GATE.get());var program=LogicProgram.compile(s);
        h.assertTrue(program.evaluate(Map.of(p(-2,0),false)).get(p(2,0)),"disabled inversion loop is stable HIGH");
        var before=program.saveMemory();
        try {program.evaluate(Map.of(p(-2,0),true));throw new AssertionError("oscillation accepted");}
        catch(LogicProgram.UnstableFeedback expected){h.assertTrue(expected.getMessage().contains("Oscillating"),"bounded repeated-state detection");}
        h.assertTrue(before.equals(program.saveMemory()),"failed evaluation cannot corrupt saved memory");
        h.assertTrue(program.evaluate(Map.of(p(-2,0),false)).get(p(2,0)),"input change restores stable behavior");
        var n=network(s);n.runtimeState(true,true);n.evaluateLogic(Map.of(p(-2,0),false));
        h.assertTrue(n.getEffectiveSignal(p(2,0))==15,"network initially powered");
        n.evaluateLogic(Map.of(p(-2,0),true));
        h.assertTrue(n.getEffectiveSignal(p(2,0))==0&&n.getRuntimeStatus().equals("UNSTABLE_FEEDBACK")&&!n.getLogicError().isEmpty(),"runtime suppresses unstable outputs with diagnostic");
        n.evaluateLogic(Map.of(p(-2,0),false));
        h.assertTrue(n.getEffectiveSignal(p(2,0))==15&&n.getLogicError().isEmpty(),"network recovers without recompilation");
        h.succeed();
    }
    @GameTest(template="empty",batch="feedback_logic",timeoutTicks=100)
    public static void positiveFeedbackAndOddInverterRing(GameTestHelper h) {
        var program=LogicProgram.compile(loop(ModBlocks.OR_GATE.get()));
        h.assertTrue(!program.evaluate(Map.of(p(-2,0),false)).get(p(2,0)),"fresh positive feedback is LOW");
        h.assertTrue(program.evaluate(Map.of(p(-2,0),true)).get(p(2,0)),"pulse sets feedback");
        h.assertTrue(program.evaluate(Map.of(p(-2,0),false)).get(p(2,0)),"positive feedback holds pulse");
        Map<BlockPos,BlockState> ring=new LinkedHashMap<>();
        ring.put(p(0,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        for(var pos:List.of(p(-1,0),p(-1,1),p(0,1),p(1,1),p(1,0)))ring.put(pos,ModBlocks.BASIC_WIRE.get().defaultBlockState());
        try{LogicProgram.compile(ring);throw new AssertionError("odd inverter ring accepted");}
        catch(IllegalArgumentException expected){h.assertTrue(expected.getMessage().contains("no stable state"),"impossible ring rejected at compile time");}
        h.succeed();
    }
    @GameTest(template="empty",batch="feedback_runtime",timeoutTicks=200)
    public static void unavailableAndDamagedCircuitsFreezeMemory(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var storage=server.overworld().getDataStorage();
        var original=NetworkSavedData.get(server);var data=new NetworkSavedData();storage.set("compiledcircuits_networks",data);
        BlockPos base=h.absolutePos(new BlockPos(4,4,4));
        Map<BlockPos,BlockState> old=new LinkedHashMap<>(),layout=new LinkedHashMap<>();Set<Long> forced=new HashSet<>();
        latch().forEach((pos,state)->layout.put(base.offset(pos),state));
        Runnable cleanup=()->{
            for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());
            old.forEach((pos,state)->level.setBlock(pos,state,3));
            for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);
            storage.set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();
        };
        for(var pos:layout.keySet())for(long c:RuntimeSignalReader.dependencyChunks(pos)){
            if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}
            level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));
        }
        BlockPos set=base.offset(p(-2,0)),reset=base.offset(p(6,0));
        for(var input:List.of(set,reset)){old.put(input.below(),level.getBlockState(input.below()));level.setBlock(input.below(),net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(),3);}
        layout.forEach((pos,state)->{old.put(pos,level.getBlockState(pos));level.setBlock(pos,state,3);});
        layout.replaceAll((pos,state)->level.getBlockState(pos));
        var n=network(layout);data.addNetwork(n);
        new Runnable(){int phase,attempts;net.minecraft.nbt.CompoundTag memory;public void run(){try{
            if(++attempts>150)throw new AssertionError("feedback runtime failed to settle phase "+phase);
            NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
            switch(phase){
                case 0->{if(!n.getRuntimeStatus().equals("READY")){h.runAfterDelay(1,()->run());return;}
                    memory=n.save().getCompound("logicMemory").copy();
                    NetworkRuntime.chunkChanged(level,RuntimeSignalReader.chunk(set),false);phase++;}
                case 1->{h.assertTrue(memory.equals(n.save().getCompound("logicMemory")),"unavailable inputs must not overwrite latch state");
                    h.assertTrue(n.getEffectiveSignal(base.offset(p(2,-1)))==0,"unavailable outputs suppressed");
                    NetworkRuntime.chunkChanged(level,RuntimeSignalReader.chunk(set),true);phase++;}
                case 2->{if(!n.getRuntimeStatus().equals("READY")){h.runAfterDelay(1,()->run());return;}
                    h.assertTrue(memory.equals(n.save().getCompound("logicMemory")),"reload recovers original stable memory");
                    level.setBlock(base,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);phase++;}
                case 3->{if(!n.isDamaged()){h.runAfterDelay(1,()->run());return;}
                    level.setBlock(reset.below(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);phase++;}
                case 4->{h.assertTrue(memory.equals(n.save().getCompound("logicMemory")),"damage freezes memory even when inputs change");
                    level.setBlock(base,ModBlocks.NOR_GATE.get().defaultBlockState(),3);phase++;}
                default->{if(!n.getRuntimeStatus().equals("READY")){h.runAfterDelay(1,()->run());return;}
                    h.assertTrue(n.getEffectiveSignal(base.offset(p(4,3)))==15,"repair resumes evaluation using actual inputs");
                    h.assertTrue(data.isDirty(),"memory changes mark SavedData dirty");cleanup.run();h.succeed();return;}
            }
            h.runAfterDelay(1,()->run());
        }catch(Throwable error){cleanup.run();throw error;}}}.run();
    }
}
