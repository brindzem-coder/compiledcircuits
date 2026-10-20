package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class DLatchGameTests {
    private static Map<BlockPos,BlockState> layout(BlockState state) {
        Map<BlockPos,BlockState> s=new LinkedHashMap<>();s.put(BlockPos.ZERO,state);
        s.put(BlockPos.ZERO.relative(state.getValue(EndpointBlock.FACING).getOpposite()),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        s.put(BlockPos.ZERO.relative(DLatchBlock.enableSide(state)),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        s.put(BlockPos.ZERO.relative(state.getValue(EndpointBlock.FACING)),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());return s;
    }
    private static CompiledNetwork network(Map<BlockPos,BlockState> s) {
        List<CompiledCircuitElement> elements=new ArrayList<>();
        for(var item:s.entrySet()){
            Block block=item.getValue().getBlock();
            var type=block instanceof DLatchBlock?CircuitElementType.GATE:block instanceof InputEndpointBlock?CircuitElementType.INPUT:CircuitElementType.OUTPUT;
            elements.add(new CompiledCircuitElement(elements.size()+1,item.getKey(),type,CompiledBlockStateCodec.capture(item.getValue())));
        }
        var n=new CompiledNetwork(1,"latch",0,"minecraft:overworld",elements);n.restoreLogic();return n;
    }
    @GameTest(template="empty",batch="d_latch",timeoutTicks=100)
    public static void allOrientationsWriteHoldAndSave(GameTestHelper h) {
        for(var state:ModBlocks.D_LATCH.get().getStateDefinition().getPossibleStates()) {
            Direction facing=state.getValue(EndpointBlock.FACING),enable=DLatchBlock.enableSide(state);
            BlockPos d=BlockPos.ZERO.relative(facing.getOpposite()),wde=BlockPos.ZERO.relative(enable),q=BlockPos.ZERO.relative(facing);
            var s=layout(state);var program=LogicProgram.compile(s);
            h.assertTrue(!program.evaluate(Map.of(d,true,wde,false)).get(q),"disabled fresh latch stays LOW");
            h.assertTrue(program.evaluate(Map.of(d,true,wde,true)).get(q),"enabled latch writes HIGH");
            h.assertTrue(!program.evaluate(Map.of(d,false,wde,true)).get(q),"Q follows D while enable remains HIGH");
            program.evaluate(Map.of(d,true,wde,true));
            h.assertTrue(program.evaluate(Map.of(d,true,wde,false)).get(q),"closing WDE holds last value");
            h.assertTrue(program.evaluate(Map.of(d,false,wde,false)).get(q),"disabled D changes cannot overwrite memory");
            var restored=LogicProgram.compile(s);restored.restoreMemory(program.saveMemory());
            h.assertTrue(restored.evaluate(Map.of(d,false,wde,false)).get(q),"acyclic latch memory survives reload");
            h.assertTrue(!restored.evaluate(Map.of(d,false,wde,true)).get(q),"enable can write LOW after reload");
            var n=network(s);n.runtimeState(true,true);n.evaluateLogic(Map.of(d,true,wde,true));n.evaluateLogic(Map.of(d,false,wde,false));
            var loaded=CompiledNetwork.load(n.save());loaded.restoreLogic();
            h.assertTrue(loaded.getEffectiveSignal(q)==0,"saved memory does not emit before validation");
            loaded.evaluateLogic(Map.of(d,false,wde,false));loaded.runtimeState(true,true);
            h.assertTrue(loaded.getEffectiveSignal(q)==15,"network NBT restores HIGH with disabled WDE");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="d_latch",timeoutTicks=100)
    public static void portsRotationsAndIntegrity(GameTestHelper h) {
        h.assertTrue(ModBlocks.D_LATCH.get().getStateDefinition().getPossibleStates().size()==24,"exactly 24 valid orientations");
        var input=ModBlocks.GATE_INPUT.get().defaultBlockState();var output=ModBlocks.GATE_OUTPUT.get().defaultBlockState();
        for(var state:ModBlocks.D_LATCH.get().getStateDefinition().getPossibleStates()) {
            Direction q=state.getValue(EndpointBlock.FACING),d=q.getOpposite(),wde=DLatchBlock.enableSide(state);
            h.assertTrue(wde.getAxis()!=q.getAxis(),"WDE always on a side face");
            for(Direction side:Direction.values()) {
                h.assertTrue(((DLatchBlock)state.getBlock()).canWireConnect(state,side)==(side==q||side==d||side==wde),"only three working faces");
                h.assertTrue(CircuitConnections.connects(state,input,side)==(side==q),"only Q drives another input");
                h.assertTrue(CircuitConnections.connects(state,output,side)==(side==d||side==wde),"only D/WDE receive from another output");
            }
            for(Rotation rotation:Rotation.values()) {
                var rotated=state.rotate(rotation);
                h.assertTrue(rotated.getValue(EndpointBlock.FACING)==rotation.rotate(q)&&DLatchBlock.enableSide(rotated)==rotation.rotate(wde),"rotation preserves port roles");
            }
            for(Mirror mirror:Mirror.values()) {
                var mirrored=state.mirror(mirror);
                h.assertTrue(mirrored.getValue(EndpointBlock.FACING)==mirror.mirror(q)&&DLatchBlock.enableSide(mirrored)==mirror.mirror(wde),"mirror preserves port roles");
            }
            var element=new CompiledCircuitElement(1,BlockPos.ZERO,CircuitElementType.GATE,CompiledBlockStateCodec.capture(state));
            h.assertTrue(CompiledBlockStateMatcher.match(element,state.cycle(DLatchBlock.WDE_SIDE),false)==CompiledBlockStateMatcher.Match.STATE_MISMATCH,"WDE orientation is functional even outside exact mode");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="d_latch",timeoutTicks=100)
    public static void disabledLatchBreaksInvertingFeedback(GameTestHelper h) {
        Map<BlockPos,BlockState> s=new LinkedHashMap<>();
        s.put(BlockPos.ZERO,ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        BlockPos wde=new BlockPos(0,0,-1),q=new BlockPos(1,1,0);
        s.put(wde,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());s.put(q,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        s.put(new BlockPos(2,0,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        for(var pos:List.of(new BlockPos(1,0,0),new BlockPos(3,0,0),new BlockPos(3,0,1),new BlockPos(3,0,2),new BlockPos(2,0,2),new BlockPos(1,0,2),new BlockPos(0,0,2),new BlockPos(-1,0,2),new BlockPos(-1,0,1),new BlockPos(-1,0,0)))s.put(pos,ModBlocks.BASIC_WIRE.get().defaultBlockState());
        var program=LogicProgram.compile(s);
        h.assertTrue(!program.evaluate(Map.of(wde,false)).get(q),"closed latch holds despite inverted feedback at D");
        var memory=program.saveMemory();
        try{program.evaluate(Map.of(wde,true));throw new AssertionError("transparent inverting loop accepted");}
        catch(LogicProgram.UnstableFeedback expected){h.assertTrue(memory.equals(program.saveMemory()),"oscillation cannot corrupt latch state");}
        h.assertTrue(!program.evaluate(Map.of(wde,false)).get(q),"disable restores held state after oscillation");h.succeed();
    }
    @GameTest(template="empty",batch="d_latch_runtime",timeoutTicks=200)
    public static void serverWriteHoldAndReload(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var storage=server.overworld().getDataStorage();
        var original=NetworkSavedData.get(server);var data=new NetworkSavedData();storage.set("compiledcircuits_networks",data);
        BlockPos base=h.absolutePos(new BlockPos(4,6,4)),d=base.west(),wde=base.north(),q=base.east();
        var state=ModBlocks.D_LATCH.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
        Map<BlockPos,BlockState> old=new LinkedHashMap<>(),s=new LinkedHashMap<>();Set<Long> forced=new HashSet<>();
        layout(state).forEach((pos,value)->s.put(base.offset(pos),value));
        Runnable cleanup=()->{
            for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());
            old.forEach((pos,value)->level.setBlock(pos,value,3));
            for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);
            storage.set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();
        };
        for(var pos:s.keySet())for(long c:RuntimeSignalReader.dependencyChunks(pos)){
            if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}
            level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));
        }
        for(var pos:List.of(d.below(),wde.below())){old.put(pos,level.getBlockState(pos));level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
        s.forEach((pos,value)->{old.put(pos,level.getBlockState(pos));level.setBlock(pos,value,3);});
        var originalNetwork=network(s);data.addNetwork(originalNetwork);
        new Runnable(){int phase,attempts;public void run(){try{
            if(++attempts>150){h.fail("Latch runtime did not become ready");return;}
            NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
            var n=data.getNetwork(1);
            if(!n.getRuntimeStatus().equals("READY")){h.runAfterDelay(1,()->run());return;}
            if(phase==0){
                h.assertTrue(n.getEffectiveSignal(q)==0,"fresh latch output LOW");
                level.setBlock(d.below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
                h.assertTrue(n.getEffectiveSignal(q)==0,"D alone cannot write");
                level.setBlock(wde.below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
                h.assertTrue(level.getBlockState(q).getSignal(level,q,Direction.UP)==15,"WDE writes HIGH without a tick delay");
                level.setBlock(d.below(),Blocks.AIR.defaultBlockState(),3);
                h.assertTrue(n.getEffectiveSignal(q)==0,"Q tracks D with WDE held HIGH");
                level.setBlock(d.below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
                level.setBlock(wde.below(),Blocks.AIR.defaultBlockState(),3);
                level.setBlock(d.below(),Blocks.AIR.defaultBlockState(),3);
                h.assertTrue(n.getEffectiveSignal(q)==15,"closed latch holds HIGH");
                data.setDirty(false);
                level.setBlock(wde.below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
                h.assertTrue(data.isDirty()&&n.getEffectiveSignal(q)==0,"acyclic latch writes mark saved data dirty");
                level.setBlock(d.below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
                level.setBlock(wde.below(),Blocks.AIR.defaultBlockState(),3);
                level.setBlock(d.below(),Blocks.AIR.defaultBlockState(),3);
                data.replaceNetwork(CompiledNetwork.load(n.save()));
                h.assertTrue(data.getNetwork(1).getEffectiveSignal(q)==0,"replacement starts gated until validated");phase++;
                h.runAfterDelay(1,()->run());return;
            }
            h.assertTrue(n.getEffectiveSignal(q)==15,"held value returns after reload with D and WDE LOW");
            level.setBlock(wde.below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            h.assertTrue(n.getEffectiveSignal(q)==0,"new enable writes actual LOW after reload");
            cleanup.run();h.succeed();
        }catch(Throwable error){cleanup.run();throw error;}}}.run();
    }
    @GameTest(template="empty",batch="d_latch_port",timeoutTicks=100)
    public static void portToolMovesOnlyEnable(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,2));
        var old=level.getBlockState(pos);
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        var previous=player.getItemInHand(hand);
        var stack=new net.minecraft.world.item.ItemStack(com.example.compiledcircuits.registry.ModItems.WDE_PORT.get());
        player.setItemInHand(hand,stack);
        try {
            for(var state:ModBlocks.D_LATCH.get().getStateDefinition().getPossibleStates()) {
                for(Direction side:Direction.values()) {
                    level.setBlock(pos,state,3);
                    var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),side,pos,false);
                    var result=stack.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,hit));
                    var actual=level.getBlockState(pos);
                    boolean allowed=side.getAxis()!=state.getValue(EndpointBlock.FACING).getAxis();
                    h.assertTrue(actual==(allowed?DLatchBlock.withEnableSide(state,side):state),"tool moves WDE only to perpendicular clicked face");
                    h.assertTrue(result.consumesAction()==allowed,"D/Q faces reject tool");
                    h.assertTrue(stack.getCount()==1,"port tool is reusable");
                }
            }
            level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false);
            h.assertTrue(stack.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,hit))==net.minecraft.world.InteractionResult.PASS,"unrelated blocks unchanged");
        } finally {level.setBlock(pos,old,3);player.setItemInHand(hand,previous);}
        h.succeed();
    }
}
