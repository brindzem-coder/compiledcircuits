package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits") @PrefixGameTestTemplate(false)
public class UncompiledBlueprintGameTests {
    @GameTest(template="empty",batch="uncompiled_blueprint_world",timeoutTicks=300)
    public static void capturePasteAndAppendWithoutCompiling(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();
        server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"raw-copy-test"));
        player.getAbilities().mayBuild=true;player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(ModItems.CIRCUIT_COPIER.get()));
        Map<BlockPos,BlockState> old=new HashMap<>();Set<Long> forced=new HashSet<>();
        java.util.function.BiConsumer<BlockPos,BlockState> set=(p,s)->{old.putIfAbsent(p,level.getBlockState(p));level.setBlock(p,s,3);};
        var base=h.absolutePos(new BlockPos(3,7,3));var target=base.offset(16,0,0);var basic=ModBlocks.BASIC_WIRE.get().defaultBlockState();
        Runnable cleanup=()->{for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());old.forEach((p,s)->level.setBlock(p,s,3));for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();};
        for(int x=(base.getX()-3)>>4;x<=(base.getX()+24)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+24)>>4;z++){
            long c=net.minecraft.world.level.ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(c)){forced.add(c);level.setChunkForced(x,z,true);}level.getChunk(x,z);
        }
        new Runnable(){int attempts;public void run(){try{
            if(++attempts>150)throw new IllegalStateException("Chunks did not become available");
            if(forced.stream().anyMatch(c->!NetworkRuntime.isChunkAvailable(level,c))){h.runAfterDelay(1,()->run());return;}
            // AND deliberately has no output: capture and paste must not invoke logic compilation.
            set.accept(base,GateBodyBlock.withPort(ModBlocks.AND_GATE.get().defaultBlockState(),Direction.WEST,1));
            set.accept(base.above(),ModBlocks.AND_GATE.get().defaultBlockState());set.accept(base.west(),basic);set.accept(base.west(2),basic);
            var compiled=new CompiledNetwork(1,"boundary",0,level.dimension().location().toString(),List.of(new CompiledCircuitElement(1,base.west(2),CircuitElementType.WIRE,CompiledBlockStateCodec.capture(basic))));data.addNetwork(compiled);
            var raw=UncompiledBlueprint.capture(level,base.west(),7);
            h.assertTrue(raw.uncompiled()&&raw.elements(BlockPos.ZERO,0).size()==3,"connected bodies included, compiled neighbor excluded");
            var storage=new BlueprintStorage();var key=storage.put(raw);raw=BlueprintStorage.load(storage.save(new CompoundTag())).get(key);
            h.assertTrue(raw.networks(2,compiled.getDimension(),target,0,false).isEmpty(),"draft never builds a compiled network");
            var elements=raw.elements(target,1,true);for(var e:elements)set.accept(e.getPos(),Blocks.AIR.defaultBlockState());
            var blocked=elements.get(0).getPos();set.accept(blocked,Blocks.STONE.defaultBlockState());
            boolean rejected=false;try{BlueprintService.pasteAll(player,raw,target,1,true);}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected&&elements.stream().skip(1).allMatch(e->level.getBlockState(e.getPos()).isAir()),"occupied raw placement is atomic");set.accept(blocked,Blocks.AIR.defaultBlockState());
            java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> deny=e->{if(e.getEntity()==player)e.setCanceled(true);};net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
            try{boolean denied=false;try{BlueprintService.pasteAll(player,raw,target,1,true);}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied&&elements.stream().allMatch(e->level.getBlockState(e.getPos()).isAir()),"denied raw paste rolls back");}
            finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);}
            int next=data.getNextNetworkId();h.assertTrue(BlueprintService.pasteAll(player,raw,target,1,true).isEmpty(),"raw paste creates blocks only");
            h.assertTrue(data.getNetworks().size()==1&&data.getNextNetworkId()==next,"raw paste allocates no network ID");
            for(var e:elements)h.assertTrue(level.getBlockState(e.getPos()).is(e.resolveState().state().orElseThrow().getBlock())&&CircuitBoundary.uncompiled(data,compiled.getDimension(),e.getPos()),"pasted blocks remain editable and uncompiled");
            // Unconfigured ID wire is a legitimate unfinished draft, but still invalid as compiled data.
            var unset=base.offset(0,0,6);set.accept(unset,ModBlocks.ID_WIRE.get().defaultBlockState());
            var draft=UncompiledBlueprint.capture(level,unset,7);var draftElement=draft.elements(BlockPos.ZERO,0).get(0);
            h.assertTrue(draftElement.wireId().isEmpty()&&CompiledCircuitElement.load(draftElement.save()).resolveState().status()!=CompiledBlockStateCodec.Status.EXACT,"draft ID support does not relax compilation validation");
            var joined=raw.append(draft);h.assertTrue(joined.networkCount()==2&&joined.append(draft).networkCount()==2,"append distinguishes separate drafts and deduplicates repeated capture");
            var dest=base.offset(16,0,7);for(var e:draft.elements(dest,0))set.accept(e.getPos(),Blocks.AIR.defaultBlockState());BlueprintService.pasteAll(player,draft,dest,0,false);
            h.assertTrue(((IdWireEntity)level.getBlockEntity(dest)).wireId().isEmpty(),"unset ID remains unset after paste");
            // Crossing traversal follows only the selected lane.
            var cross=base.offset(0,0,12);var state=ModBlocks.CROSSING_WIRE.get().defaultBlockState().setValue(CrossingWireBlock.LANES[0],1).setValue(CrossingWireBlock.LANES[2],1);
            set.accept(cross,state);for(var side:new Direction[]{Direction.WEST,Direction.EAST,Direction.NORTH,Direction.SOUTH})set.accept(cross.relative(side),basic);
            var lane=UncompiledBlueprint.capture(level,cross,1<<Direction.Axis.X.ordinal());
            h.assertTrue(lane.elements(BlockPos.ZERO,0).size()==3,"crossing does not collect perpendicular network");
            var laneElement=lane.elements(BlockPos.ZERO,0).stream().filter(e->e.getBlockId().endsWith("crossing_wire")).findFirst().orElseThrow();
            h.assertTrue(Crossings.mask(laneElement)==1<<Direction.Axis.X.ordinal(),"only selected crossing axis captured");
            // Mixed append retains compilation only for parts that were compiled at capture.
            var mixed=raw.append(CircuitBlueprint.capture(compiled,level));var mixedTarget=base.offset(16,0,12);
            for(var e:mixed.elements(mixedTarget,0))set.accept(e.getPos(),Blocks.AIR.defaultBlockState());
            var compiledCopies=BlueprintService.pasteAll(player,mixed,mixedTarget,0,false);
            h.assertTrue(compiledCopies.size()==1&&data.getNetworks().size()==2,"mixed append compiles only its compiled part");
            // Live configuration survives raw capture, storage, rotation and mirrored paste.
            var cable=base.offset(0,0,18);set.accept(cable,ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
            set.accept(cable.west(),ModBlocks.KEYBOARD.get().defaultBlockState());
            set.accept(cable.east(),ModBlocks.COLOR_SCREEN.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
            set.accept(cable.below(),basic);set.accept(cable.below(2),ModBlocks.ID_WIRE.get().defaultBlockState());
            ((IdWireEntity)level.getBlockEntity(cable.below(2))).configure("987654321",0x123456);
            var ports=BundleWireEntity.encode(Map.of(Direction.EAST,new BundleWireEntity.Port(6,"100"),Direction.DOWN,new BundleWireEntity.Port(1,"7")));
            ((BundleWireEntity)level.getBlockEntity(cable)).restorePorts(ports);
            var camera=new CompoundTag();camera.putDouble("x",2);camera.putDouble("y",1);camera.putDouble("z",-3);camera.putFloat("yaw",90);camera.putFloat("pitch",15);
            ((KeyboardEntity)level.getBlockEntity(cable.west())).restoreCamera(camera);
            var configured=UncompiledBlueprint.capture(level,cable,7);h.assertTrue(configured.elements(BlockPos.ZERO,0).size()==5,"raw scan includes screen, keyboard and configured ID wire");
            var configKey=storage.put(configured);configured=BlueprintStorage.load(storage.save(new CompoundTag())).get(configKey);
            var configTarget=base.offset(16,0,20);var configElements=configured.elements(configTarget,2,true);
            for(var e:configElements)set.accept(e.getPos(),Blocks.AIR.defaultBlockState());BlueprintService.pasteAll(player,configured,configTarget,2,true);
            for(var e:configElements){
                var entity=level.getBlockEntity(e.getPos());
                if(entity instanceof IdWireEntity wire)h.assertTrue(wire.wireId().equals("987654321")&&wire.color()==0x123456,"wire ID and color preserved");
                if(entity instanceof BundleWireEntity wire)h.assertTrue(wire.portData().equals(e.bundleFaces()),"port IDs and transformed faces preserved");
                if(entity instanceof KeyboardEntity keyboard)h.assertTrue(keyboard.cameraData().equals(e.keyboardCamera()),"relative camera transformed with copy");
            }
            var far=new BlockPos(15000000,90,15000000);boolean unavailable=false;
            try{UncompiledBlueprint.capture(level,far,7);}catch(IllegalArgumentException expected){unavailable=true;}
            h.assertTrue(unavailable&&!level.hasChunkAt(far),"capture never loads distant chunks");
            cleanup.run();h.succeed();
        }catch(Throwable failure){cleanup.run();throw failure;}}}.run();
    }
}
