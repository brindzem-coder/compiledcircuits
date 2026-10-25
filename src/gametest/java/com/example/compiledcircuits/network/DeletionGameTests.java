package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.ModBlocks;
import com.example.compiledcircuits.networking.NetworkBulkActionC2SPacket;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class DeletionGameTests {
    @GameTest(template="empty",batch="delete_world",timeoutTicks=600)
    public static void selectedAndBulkDeletion(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();
        server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"delete-test"));
        player.getAbilities().mayBuild=true;
        var a=h.absolutePos(new BlockPos(2,4,2));var b=a.east();var c=b.east();var d=c.east();var e=d.east();
        var positions=List.of(a,b,c,d,e,a.north());
        Map<BlockPos,BlockState> old=new LinkedHashMap<>();Set<Long> forced=new HashSet<>();
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> deny=event->{if(event.getPlayer()==player&&event.getPos().equals(a))event.setCanceled(true);};
        Runnable cleanup=()->{
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);
            DeletionJobs.stop(server);CompilationJobs.stop(server);NetworkOperations.forget(player);
            for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());
            old.forEach((pos,state)->level.setBlock(pos,state,3));
            for(long chunk:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk),net.minecraft.world.level.ChunkPos.getZ(chunk),false);
            server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();
        };
        try {
            for(var pos:positions)for(long chunk:RuntimeSignalReader.dependencyChunks(pos)) {
                if(!level.getForcedChunks().contains(chunk)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk),net.minecraft.world.level.ChunkPos.getZ(chunk),true);forced.add(chunk);}
                level.getChunk(net.minecraft.world.level.ChunkPos.getX(chunk),net.minecraft.world.level.ChunkPos.getZ(chunk));
            }
            for(var pos:positions)old.put(pos,level.getBlockState(pos));
            new Runnable(){int phase,tries;CompiledNetwork first,second,neighbor;
                void put(BlockPos pos,BlockState state){level.setBlock(pos,state,3);}
                void selectDelete(BlockPos pos){NetworkSelectionData.set(player,pos);NetworkOperations.forget(player);
                    var result=NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.DELETE,List.of(),0,"");
                    h.assertTrue(result.code()==NetworkOperations.Code.QUEUED,"selected deletion queued: "+result);}
                public void run(){try{
                    if(++tries>300){cleanup.run();h.fail("Deletion timeout phase "+phase);return;}
                    if(DeletionJobs.isBusy(server)){h.runAfterDelay(1,()->run());return;}
                    var wire=ModBlocks.BASIC_WIRE.get().defaultBlockState();
                    if(phase==0){
                        put(a,SignalPorts.with(ModBlocks.SIGNAL_SWITCH.get().defaultBlockState(),Direction.EAST,true));put(b,wire);
                        put(c,SignalPorts.with(ModBlocks.SIGNAL_LAMP.get().defaultBlockState(),Direction.WEST,true));
                        put(a.north(),ModBlocks.SIGNAL_SWITCH.get().defaultBlockState());
                        phase++;h.runAfterDelay(5,()->run());return;
                    }else if(phase==1){selectDelete(a);phase++;}
                    else if(phase==2){
                        h.assertTrue(level.isEmptyBlock(a)&&level.isEmptyBlock(b)&&level.isEmptyBlock(c),"uncompiled switch, wire and lamp deleted");
                        h.assertTrue(level.getBlockState(a.north()).is(ModBlocks.SIGNAL_SWITCH.get()),"adjacent disconnected switch survives");
                        put(a,NetworkWireBlock.withFace(ModBlocks.NETWORK_WIRE.get().defaultBlockState(),Direction.EAST,3));
                        put(b,NetworkWireBlock.withFace(ModBlocks.NETWORK_WIRE.get().defaultBlockState(),Direction.WEST,2));
                        phase++;h.runAfterDelay(5,()->run());return;
                    }else if(phase==3){selectDelete(a);phase++;}
                    else if(phase==4){
                        h.assertTrue(level.isEmptyBlock(a)&&!level.isEmptyBlock(b),"uncompiled deletion stops at inter-network ports");
                        put(a,wire);put(b,wire);put(c,wire);put(d,wire);put(e,wire);
                        first=DirectLinkGameTests.network(data.getNextNetworkId(),Map.of(a,wire,b,wire));data.addNetwork(first);
                        second=DirectLinkGameTests.network(data.getNextNetworkId(),Map.of(c,wire,d,wire));data.addNetwork(second);
                        neighbor=DirectLinkGameTests.network(data.getNextNetworkId(),Map.of(e,wire));data.addNetwork(neighbor);
                        put(b,Blocks.STONE.defaultBlockState());put(d,Blocks.AIR.defaultBlockState());
                        phase++;h.runAfterDelay(5,()->run());return;
                    }else if(phase==5){
                        NetworkOperations.forget(player);
                        var result=NetworkBulkActionC2SPacket.execute(new NetworkBulkActionC2SPacket(NetworkBulkActionC2SPacket.BulkAction.DELETE_NETWORKS,List.of(first.getId(),second.getId()),0),player);
                        h.assertTrue(result.code()==NetworkOperations.Code.QUEUED,"GUI bulk deletion queued");phase++;
                    }else if(phase==6){
                        h.assertTrue(data.getNetwork(first.getId())==null&&data.getNetwork(second.getId())==null,"both compilation records removed");
                        h.assertTrue(level.isEmptyBlock(a)&&level.isEmptyBlock(c),"remaining broken network elements deleted");
                        h.assertTrue(level.getBlockState(b).is(Blocks.STONE),"unrelated replacement preserved");
                        h.assertTrue(data.getNetwork(neighbor.getId())==neighbor&&level.getBlockState(e).is(ModBlocks.BASIC_WIRE.get()),"unselected adjacent compiled network survives");
                        put(a,wire);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
                        phase++;h.runAfterDelay(5,()->run());return;
                    }else if(phase==7){selectDelete(a);phase++;}
                    else if(phase==8){
                        h.assertTrue(level.getBlockState(a).is(ModBlocks.BASIC_WIRE.get()),"Forge break protection respected");
                        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);
                        NetworkSelectionData.set(player,a);NetworkOperations.forget(player);player.getAbilities().mayBuild=false;
                        h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.DELETE,List.of(),0,"").code()==NetworkOperations.Code.FORBIDDEN,"adventure player cannot delete");
                        player.getAbilities().mayBuild=true;selectDelete(new BlockPos(29000000,64,29000000));phase++;
                    }else{
                        h.assertTrue(!level.hasChunk(29000000>>4,29000000>>4),"unloaded selection never loads chunks");
                        h.assertTrue(level.getBlockState(a).is(ModBlocks.BASIC_WIRE.get()),"failed request leaves unrelated circuit intact");
                        cleanup.run();h.succeed();return;
                    }
                    h.runAfterDelay(1,()->run());
                }catch(Throwable error){cleanup.run();throw error;}}
            }.run();
        }catch(Throwable error){cleanup.run();throw error;}
    }
}
