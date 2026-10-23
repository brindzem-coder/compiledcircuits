package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class SignalBlockGameTests {
    @GameTest(template="empty",batch="signal_blocks")
    public static void dynamicStatesAndConnections(GameTestHelper h) {
        var input=ModBlocks.SIGNAL_SWITCH.get().defaultBlockState();
        var output=ModBlocks.SIGNAL_LAMP.get().defaultBlockState();
        h.assertTrue(!input.getValue(BlockStateProperties.POWERED)&&!output.getValue(BlockStateProperties.LIT),"new blocks default to LOW");
        for(var side:Direction.values()) for(var wire:List.of(ModBlocks.BASIC_WIRE.get(),ModBlocks.ID_WIRE.get())) {
            h.assertTrue(CircuitConnections.connects(SignalPorts.with(input,side,true),"0",wire.defaultBlockState(),"42",side),"switch accepts either wire on all sides");
            h.assertTrue(CircuitConnections.connects(SignalPorts.with(output,side,true),"0",wire.defaultBlockState(),"42",side),"lamp accepts either wire on all sides");
        }
        for(var pair:List.of(Map.entry(input,CircuitElementType.INPUT),Map.entry(output,CircuitElementType.OUTPUT))) {
            var state=pair.getKey();var element=new CompiledCircuitElement(1,BlockPos.ZERO,pair.getValue(),CompiledBlockStateCodec.capture(state));
            var changed=state.cycle(pair.getValue()==CircuitElementType.INPUT?BlockStateProperties.POWERED:BlockStateProperties.LIT);
            h.assertTrue(CompiledBlockStateMatcher.liveSignalChange(state,changed),"signal toggle is not a topology mutation");
            h.assertTrue(CompiledBlockStateMatcher.liveSignalChange(changed,state),"signal toggle off is not a topology mutation");
            var withArm=changed.setValue(RedstoneEndpointBlock.connectionProperty(Direction.NORTH),true);
            h.assertTrue(!CompiledBlockStateMatcher.liveSignalChange(state,withArm),"port edits change topology");
            h.assertTrue(CompiledBlockStateMatcher.match(element,withArm,false)==CompiledBlockStateMatcher.Match.STATE_MISMATCH,"port edits invalidate even non-exact snapshots");
            h.assertTrue(!CompiledBlockStateMatcher.liveSignalChange(state,Blocks.AIR.defaultBlockState()),"removing a signal block is a topology mutation");
            h.assertTrue(CompiledBlockStateMatcher.match(CompiledCircuitElement.load(element.save()),changed,true)==CompiledBlockStateMatcher.Match.MATCH,"live signal survives save/load and exact integrity checks");
            h.assertTrue(state.getShape(h.getLevel(),BlockPos.ZERO).bounds().getSize()==1,"full cube outline");
        }
        h.assertTrue(output.setValue(BlockStateProperties.LIT,true).getLightEmission()==15&&output.getLightEmission()==0,"lamp light level follows its state");
        for (var side:Direction.values()) {
            h.assertTrue(!CircuitConnections.connects(input,ModBlocks.BASIC_WIRE.get().defaultBlockState(),side),"bare switch does not connect");
            h.assertTrue(!CircuitConnections.connects(output,ModBlocks.BASIC_WIRE.get().defaultBlockState(),side),"bare lamp does not connect");
            for(var left:List.of(input,output)) for(var right:List.of(input,output)) {
                h.assertTrue(!CircuitConnections.connects(SignalPorts.with(left,side,true),SignalPorts.with(right,side.getOpposite(),true),side),"controls never connect directly");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="signal_ports")
    public static void controlsAreDirectedTerminals(GameTestHelper h) {
        var west=new BlockPos(-1,0,0); var east=new BlockPos(1,0,0);
        var drive=new BlockPos(-2,0,0); var probe=new BlockPos(2,0,0);
        for(var block:List.of(ModBlocks.SIGNAL_SWITCH.get(),ModBlocks.SIGNAL_LAMP.get())) {
            var control=SignalPorts.with(SignalPorts.with(block.defaultBlockState(),Direction.WEST,true),Direction.EAST,true);
            var states=new HashMap<BlockPos,BlockState>();
            states.put(BlockPos.ZERO,control);
            states.put(west,ModBlocks.BASIC_WIRE.get().defaultBlockState());
            states.put(east,ModBlocks.BASIC_WIRE.get().defaultBlockState());
            states.put(drive,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
            states.put(probe,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
            var program=LogicProgram.compile(states);
            var result=program.evaluate(Map.of(drive,true,BlockPos.ZERO,false));
            h.assertTrue(!result.getOrDefault(probe,false),"control never bridges wire buses");
            if(block instanceof SignalSwitchBlock) {
                result=program.evaluate(Map.of(drive,false,BlockPos.ZERO,true));
                h.assertTrue(result.getOrDefault(probe,false),"switch drives its output port");
            } else h.assertTrue(result.getOrDefault(BlockPos.ZERO,false),"lamp receives its input");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="signal_blocks_world",timeoutTicks=500)
    public static void switchDrivesCompiledLamp(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();
        server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"signal-blocks"));player.getAbilities().mayBuild=true;
        var input=h.absolutePos(new BlockPos(2,4,2));var basic=input.east();var id=basic.east();var lamp=id.east();
        var isolated=input.north();
        var positions=List.of(input,basic,id,lamp,input.below(),isolated);
        Map<BlockPos,BlockState> old=new LinkedHashMap<>();Set<Long> forced=new HashSet<>();
        Runnable cleanup=()->{
            CompilationJobs.stop(server);RepairJobs.stop(server);NetworkOperations.forget(player);
            for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());
            old.forEach((pos,state)->level.setBlock(pos,state,3));
            for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);
            server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();
        };
        try {
            for(var pos:positions)for(long c:RuntimeSignalReader.dependencyChunks(pos)) {
                if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}
                level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));
            }
            for(var pos:positions)old.put(pos,level.getBlockState(pos));
            level.setBlock(input,ModBlocks.SIGNAL_SWITCH.get().defaultBlockState(),3);
            var tool=new net.minecraft.world.item.ItemStack(ModItems.SWITCH_OUTPUT.get());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,tool);
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(input),Direction.EAST,input,false);
            h.assertTrue(level.getBlockState(input).use(level,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit)==net.minecraft.world.InteractionResult.PASS,"switch lets port tool handle click");
            var context=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            tool.useOn(context); h.assertTrue(SignalPorts.has(level.getBlockState(input),Direction.EAST),"tool installs port");
            tool.useOn(context); h.assertTrue(!SignalPorts.has(level.getBlockState(input),Direction.EAST),"tool removes port");
            tool.useOn(context);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
            level.setBlock(isolated,SignalPorts.with(ModBlocks.SIGNAL_SWITCH.get().defaultBlockState(),Direction.SOUTH,true),3);
            level.setBlock(input.below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            level.setBlock(basic,ModBlocks.BASIC_WIRE.get().defaultBlockState(),3);
            level.setBlock(id,ModBlocks.ID_WIRE.get().defaultBlockState(),3);((IdWireEntity)level.getBlockEntity(id)).configure("42",0xff0000);
            level.setBlock(lamp,SignalPorts.with(ModBlocks.SIGNAL_LAMP.get().defaultBlockState(),Direction.WEST,true),3);
            new Runnable(){int phase,attempts;CompiledNetwork network;
                void toggle(){var result=level.getBlockState(input).use(level,player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(input),Direction.UP,input,false));h.assertTrue(result.consumesAction(),"switch click accepted: "+result);}
                void repair(){
                    h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.REPAIR,List.of(network.getId()),0,"").code()==NetworkOperations.Code.QUEUED,"GUI repair action queues signal blocks");
                }
                void damage(BlockPos pos,BlockState state){level.setBlock(pos,state,3);NetworkIntegrityManager.checkPosition(level,pos);}
                boolean lit(){return level.getBlockState(lamp).getValue(BlockStateProperties.LIT);}
                public void run(){try{
                    if(++attempts>220){String details=" state="+level.getBlockState(input)+" value="+(network==null?"none":network.getEffectiveSignal(lamp)+" "+network.getRuntimeStatus())+" read="+new RuntimeSignalReader(level).readInput(input);cleanup.run();h.fail("signal integration timeout phase "+phase+details);return;}
                    NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
                    if(CompilationJobs.isBusy(player)){h.runAfterDelay(1,()->run());return;}
                    if(phase==0){
                        NetworkSelectionData.set(player,input);
                        h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.COMPILE,List.of(),0,"signal blocks").code()==NetworkOperations.Code.QUEUED,"switch can start compiler discovery");phase=1;
                    }else if(phase==1){
                        network=data.findNetworkContaining(level,input);
                        if(network==null||!network.getRuntimeStatus().equals("READY")){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(network.getElements().size()==4,"compiler includes switch, both wires and lamp");
                        h.assertTrue(data.findNetworkContaining(level,isolated)==null,"adjacent switch is not selected for compilation");
                        h.assertTrue(new RuntimeSignalReader(level).readInput(input)==0&&!lit(),"redstone next to OFF switch cannot power the circuit");
                        toggle();phase=2;
                    }else if(phase==2){
                        if(!lit()){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(!network.isDamaged()&&network.getEffectiveSignal(lamp)==15,"toggle drives compiled output without damage");
                        toggle();phase=3;
                    }else if(phase==3){
                        if(lit()){h.runAfterDelay(1,()->run());return;}
                        toggle();phase=4;
                    }else if(phase==4){
                        if(!lit()){h.runAfterDelay(1,()->run());return;}
                        level.setBlock(basic,Blocks.AIR.defaultBlockState(),3);NetworkIntegrityManager.checkPosition(level,basic);phase=5;
                    }else if(phase==5){
                        if(lit()){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(network.isDamaged(),"damaged network extinguishes lamp");
                        level.setBlock(basic,ModBlocks.BASIC_WIRE.get().defaultBlockState(),3);NetworkIntegrityManager.checkPosition(level,basic);phase=6;
                    }else if(phase==6){
                        if(!lit()){h.runAfterDelay(1,()->run());return;}
                        damage(input,Blocks.AIR.defaultBlockState());damage(lamp,Blocks.AIR.defaultBlockState());repair();phase=9;
                    }else if(phase==7){
                        if(lit()){h.runAfterDelay(1,()->run());return;}
                        // Simulate loading a saved lit block after its compilation was removed.
                        level.setBlock(lamp,level.getBlockState(lamp).setValue(BlockStateProperties.LIT,true),2);
                        ((SignalLampEntity)level.getBlockEntity(lamp)).onLoad();phase=8;
                    }else if(phase==9){
                        if(RepairJobs.isBusy(player)||network.isDamaged()||!network.getRuntimeStatus().equals("READY")){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(level.getBlockState(input).is(ModBlocks.SIGNAL_SWITCH.get())&&level.getBlockEntity(lamp) instanceof SignalLampEntity,"GUI repair recreates both blocks and lamp entity");
                        h.assertTrue(SignalPorts.has(level.getBlockState(input),Direction.EAST)&&SignalPorts.has(level.getBlockState(lamp),Direction.WEST),"GUI repair restores saved ports");
                        h.assertTrue(!level.getBlockState(input).getValue(BlockStateProperties.POWERED)&&!lit(),"missing switch restores saved OFF state");
                        toggle();phase=10;
                    }else if(phase==10){
                        if(!lit()){h.runAfterDelay(1,()->run());return;}
                        damage(input,SignalPorts.with(level.getBlockState(input),Direction.EAST,false));
                        damage(lamp,SignalPorts.with(level.getBlockState(lamp),Direction.WEST,false));repair();phase=11;
                    }else if(phase==11){
                        if(RepairJobs.isBusy(player)||network.isDamaged()||!lit()){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(SignalPorts.has(level.getBlockState(input),Direction.EAST)&&SignalPorts.has(level.getBlockState(lamp),Direction.WEST),"GUI repair restores ports in place");
                        h.assertTrue(level.getBlockState(input).getValue(BlockStateProperties.POWERED),"port repair preserves live ON switch");
                        // Protection cancellation must also restore an existing lamp and its ports.
                        damage(lamp,SignalPorts.with(level.getBlockState(lamp),Direction.WEST,false));
                        var before=level.getBlockState(lamp);
                        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> deny=e->{if(e.getEntity()==player)e.setCanceled(true);};
                        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
                        try{
                            var target=network.getBrokenElement(network.getElementAt(lamp).getId());
                            h.assertTrue(NetworkRepairManager.attempt(level,network,target,player)==NetworkRepairManager.Outcome.PROTECTED,"lamp repair honors placement protection");
                            h.assertTrue(level.getBlockState(lamp)==before&&level.getBlockEntity(lamp) instanceof SignalLampEntity,"denial restores original lamp and entity");
                        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);}
                        repair();phase=12;
                    }else if(phase==12){
                        if(RepairJobs.isBusy(player)||network.isDamaged()||!lit()){h.runAfterDelay(1,()->run());return;}
                        data.removeNetwork(network.getId());phase=7;
                    }else{
                        if(lit()){h.runAfterDelay(1,()->run());return;}
                        cleanup.run();h.succeed();return;
                    }
                    h.runAfterDelay(6,()->run());
                }catch(Throwable failure){cleanup.run();throw failure;}}
            }.run();
        }catch(Throwable failure){cleanup.run();throw failure;}
    }
}
