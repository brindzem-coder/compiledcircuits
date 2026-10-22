package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class SurfaceBundleGameTests {
    static final BlockPos C = new BlockPos(0, 3, 0);
    static Map<BundleFace,BundleWireEntity.Port> ports(boolean external) {
        var result = new HashMap<BundleFace,BundleWireEntity.Port>();
        result.put(new BundleFace(C,Direction.WEST), new BundleWireEntity.Port(external?3:1,"7"));
        result.put(new BundleFace(C,Direction.EAST), new BundleWireEntity.Port(external?4:2,"7"));
        result.put(new BundleFace(C,Direction.NORTH), new BundleWireEntity.Port(external?3:1,"9999999999999999999999999999999999999999999999999999999999999999"));
        result.put(new BundleFace(C,Direction.SOUTH), new BundleWireEntity.Port(external?4:2,"9999999999999999999999999999999999999999999999999999999999999999"));
        return result;
    }
    static CompiledNetwork cable(int id,Map<BundleFace,BundleWireEntity.Port> faces) {
        var tag=new CompiledCircuitElement(1,C,CircuitElementType.WIRE,CompiledBlockStateCodec.capture(ModBlocks.BUNDLED_WIRE.get().defaultBlockState())).save();
        Map<Direction,BundleWireEntity.Port> local=new EnumMap<>(Direction.class); faces.forEach((face,port)->local.put(face.side(),port));
        tag.put("bundleFaces",BundleWireEntity.encode(local));
        var n=new CompiledNetwork(id,"surface",0,"minecraft:overworld",List.of(CompiledCircuitElement.load(tag)));n.restoreLogic();n.runtimeState(true,true);return n;
    }
    @GameTest(template="empty",batch="bundle_faces")
    public static void fourFacesCarryIndependentInternalChannels(GameTestHelper h) {
        Map<BlockPos,BlockState> states=new HashMap<>();states.put(C,ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
        for(var side:List.of(Direction.WEST,Direction.EAST,Direction.NORTH,Direction.SOUTH)) states.put(C.relative(side),
                (side==Direction.WEST||side==Direction.NORTH?ModBlocks.INPUT_ENDPOINT:ModBlocks.OUTPUT_ENDPOINT).get().defaultBlockState());
        var graph=LogicProgram.compileFaces(states,Map.of(),ports(false));h.assertTrue(graph.nodeCount()==4,"surface ports introduce no internal runtime nodes");
        for(int bits=0;bits<4;bits++){
            var result=graph.evaluate(Map.of(C.west(),(bits&1)!=0,C.north(),(bits&2)!=0));
            h.assertTrue(result.get(C.east())==((bits&1)!=0)&&result.get(C.south())==((bits&2)!=0),"channels on one block remain independent");
        }
        var faces=ports(false);faces.put(new BundleFace(C,Direction.NORTH),new BundleWireEntity.Port(1,"7"));
        boolean rejected=false;try{LogicProgram.compileFaces(states,Map.of(),faces);}catch(IllegalArgumentException expected){rejected=expected.getMessage().contains("multiple inputs");}
        h.assertTrue(rejected,"duplicate input on another face rejects compilation");h.succeed();
    }
    @GameTest(template="empty",batch="bundle_faces")
    public static void allSixFacesAndTablePersistence(GameTestHelper h) {
        var faces=ports(true);faces.put(new BundleFace(C,Direction.DOWN),new BundleWireEntity.Port(3,"0"));faces.put(new BundleFace(C,Direction.UP),new BundleWireEntity.Port(4,"0"));
        var n=cable(1,faces);h.assertTrue(n.directPorts().size()==6,"six independently addressed boundary terminals");
        var graph=LogicProgram.compileFaces(n.tableStates(),n.wireIds(),n.bundleFaces());
        var builder=new NetworkTruthTable.Builder(graph);while(!builder.step()){}var table=NetworkTruthTable.load(builder.finish().save());
        n.setNetworkTable(table);n.enableOptimization();var loaded=CompiledNetwork.load(n.save());loaded.restoreLogic();
        h.assertTrue(loaded.bundleFaces().equals(faces)&&loaded.networkTable()!=null,"face IDs and truth table survive reload");
        var optimized=LogicProgram.compileFaces(loaded.tableStates(),loaded.wireIds(),loaded.bundleFaces());optimized.installTable(table,Set.of(C));optimized.optimize(Set.of(C));
        for(int bits=0;bits<8;bits++){
            optimized.evaluate(Map.of(),Map.of(new BundleFace(C,Direction.WEST),(bits&1)!=0,new BundleFace(C,Direction.NORTH),(bits&2)!=0,new BundleFace(C,Direction.DOWN),(bits&4)!=0));
            h.assertTrue(optimized.faceOutput(new BundleFace(C,Direction.EAST))==((bits&1)!=0),"first output uses only first input");
            h.assertTrue(optimized.faceOutput(new BundleFace(C,Direction.SOUTH))==((bits&2)!=0),"second output uses only second input");
            h.assertTrue(optimized.faceOutput(new BundleFace(C,Direction.UP))==((bits&4)!=0),"vertical pair stays independent");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="bundle_faces")
    public static void linkedChannelsStayIndependentWithOptimization(GameTestHelper h) {
        var transport=cable(1,ports(true));
        var builder=new NetworkTruthTable.Builder(LogicProgram.compileFaces(transport.tableStates(),transport.wireIds(),transport.bundleFaces()));while(!builder.step()){}transport.setNetworkTable(builder.finish());transport.enableOptimization();
        var table=new DirectLinks(new NetworkSavedData(),n->{});List<CompiledNetwork> peers=new ArrayList<>();
        for(var side:List.of(Direction.WEST,Direction.NORTH,Direction.EAST,Direction.SOUTH)){
            boolean source=side==Direction.WEST||side==Direction.NORTH;
            var n=DirectLinkGameTests.network(peers.size()+2,Map.of(C.relative(side),DirectLinkGameTests.port(side.getOpposite(),source?3:2),C.relative(side,2),(source?ModBlocks.INPUT_ENDPOINT:ModBlocks.OUTPUT_ENDPOINT).get().defaultBlockState()));
            peers.add(n);table.add(n);
        }
        table.validateCompilation(transport);table.add(transport);table.update(transport,Map.of());
        for(int bits=0;bits<4;bits++){
            table.update(peers.get(0),Map.of(C.west(2),(bits&1)!=0));table.update(peers.get(1),Map.of(C.north(2),(bits&2)!=0));
            table.update(peers.get(2),Map.of());table.update(peers.get(3),Map.of());DirectLinkGameTests.settle(table);
            h.assertTrue(peers.get(2).getEffectiveSignal(C.east(2))==((bits&1)!=0?15:0),"first face signal crosses networks independently");
            h.assertTrue(peers.get(3).getEffectiveSignal(C.south(2))==((bits&2)!=0?15:0),"second face signal crosses networks independently");
        }
        table.remove(peers.get(0));DirectLinkGameTests.settle(table);h.assertTrue(peers.get(3).getEffectiveSignal(C.south(2))==0,"missing required source gates linked group");h.succeed();
    }
    @GameTest(template="empty",batch="bundle_faces")
    public static void facePortsSeparateCableComponents(GameTestHelper h) {
        var b=C.east();Map<BlockPos,BlockState> states=Map.of(C,ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),b,ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
        Map<BundleFace,BundleWireEntity.Port> faces=Map.of(new BundleFace(C,Direction.WEST),new BundleWireEntity.Port(3,"1"),new BundleFace(C,Direction.EAST),new BundleWireEntity.Port(4,"1"),new BundleFace(b,Direction.WEST),new BundleWireEntity.Port(3,"1"),new BundleFace(b,Direction.EAST),new BundleWireEntity.Port(4,"1"));
        var graph=LogicProgram.compileFaces(states,Map.of(),faces);graph.evaluate(Map.of(),Map.of(new BundleFace(C,Direction.WEST),true));
        h.assertTrue(graph.faceOutput(new BundleFace(C,Direction.EAST))&&!graph.faceOutput(new BundleFace(b,Direction.EAST)),"facing external ports do not merge cable components or duplicate channel IDs");h.succeed();
    }
    @GameTest(template="empty",batch="bundle_faces")
    public static void toolAppliesEditsAndRemovesWithoutExtraBlock(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,2));level.setBlock(pos,ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),3);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"surface-tool"));player.getAbilities().mayBuild=true;
        var stack=new net.minecraft.world.item.ItemStack(ModItems.BUNDLE_INPUT.get());stack.getOrCreateTag().putString("ChannelId","42");player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        for(var side:Direction.values()){
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),side,pos,false);
            var context=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(stack.useOn(context).consumesAction(),"port tool applies to each face");
            var entity=(BundleWireEntity)level.getBlockEntity(pos);h.assertTrue(entity.port(side).id().equals("42"),"face remembers selected ID");
            h.assertTrue(!level.getBlockState(pos.relative(side)).getBlock().equals(ModBlocks.BUNDLE_INPUT.get()),"port occupies no neighboring block");
        }
        var entity=(BundleWireEntity)level.getBlockEntity(pos);h.assertTrue(entity.ports().size()==6&&stack.getCount()==1,"six faces and reusable tool");
        var element=new CompiledCircuitElement(1,pos,CircuitElementType.WIRE,CompiledBlockStateCodec.capture(level.getBlockState(pos))).captureWire(level);
        var snapshot=CompiledCircuitElement.load(element.save());h.assertTrue(snapshot.bundleFaces().equals(entity.portData()),"compiled snapshot retains every face");
        entity.setPort(Direction.UP,new BundleWireEntity.Port(1,"43"));h.assertTrue(CompiledBlockStateMatcher.match(snapshot,level,level.getBlockState(pos),false)==CompiledBlockStateMatcher.Match.STATE_MISMATCH,"ID-only change damages compiled snapshot");
        entity.restorePorts(snapshot.bundleFaces());h.assertTrue(CompiledBlockStateMatcher.match(snapshot,level,level.getBlockState(pos),false)==CompiledBlockStateMatcher.Match.MATCH,"restoring metadata repairs identity");
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false);stack.useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        h.assertTrue(entity.port(Direction.UP)==null&&entity.ports().size()==5,"repeat click removes only selected face");
        level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);h.succeed();
    }
    @GameTest(template="empty",batch="bundle_faces")
    public static void legacyBareCableAndOldTablesRemainReadable(GameTestHelper h) {
        var element=new CompiledCircuitElement(1,C,CircuitElementType.WIRE,CompiledBlockStateCodec.capture(ModBlocks.BUNDLED_WIRE.get().defaultBlockState()));
        var old=CompiledCircuitElement.load(element.save());h.assertTrue(old.bundleFaces().isEmpty()&&old.resolveState().status()==CompiledBlockStateCodec.Status.EXACT,"old bare cable needs no migration");
        var table=new NetworkTruthTable("old",List.of(C),List.of(C.east()),new long[]{0,1});var tag=table.save();tag.putInt("version",1);tag.remove("faceInputs");tag.remove("faceOutputs");
        var loaded=NetworkTruthTable.load(tag);h.assertTrue(loaded.faceInputs.isEmpty()&&loaded.output(1,0),"version 1 tables keep their meaning");h.succeed();
    }
    @GameTest(template="empty",batch="bundle_faces")
    public static void benchmarkIncludesIndependentFaceInputs(GameTestHelper h) {
        var network=cable(1,ports(true));var job=new CircuitBenchmark.Job(network,UUID.randomUUID(),true);
        for(int i=0;i<10000&&!job.done;i++)job.step();
        h.assertTrue(job.done&&job.status.equals("Complete")&&job.total==4&&job.failed==0,"benchmark exercises all face input combinations");h.succeed();
    }
    @GameTest(template="empty",batch="bundle_faces")
    public static void deniedPlacementRestoresFaceMetadata(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,2));level.setBlock(pos,ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),3);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"surface-denied"));player.getAbilities().mayBuild=true;
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(ModItems.BUNDLE_INPUT.get()));
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> deny=event->{if(event.getEntity()==player&&event.getPos().equals(pos))event.setCanceled(true);};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
        try {
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.WEST,pos,false);
            var result=player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
            h.assertTrue(result==net.minecraft.world.InteractionResult.FAIL&&((BundleWireEntity)level.getBlockEntity(pos)).ports().isEmpty(),"claim denial restores block entity metadata");
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);}
        h.succeed();
    }
    @GameTest(template="empty",batch="bundle_faces_world",timeoutTicks=450)
    public static void compiledCompactCircuitRepairsAllFaces(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();
        server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"surface-compiler"));player.getAbilities().mayBuild=true;
        var center=h.absolutePos(new BlockPos(4,5,4));var input=center.west();var second=center.north();var output=center.east();var secondOut=center.south();
        Map<BlockPos,BlockState> old=new LinkedHashMap<>();Set<Long> forced=new HashSet<>();
        var positions=List.of(center,input,second,output,secondOut,input.below(),second.below());
        Runnable cleanup=()->{
            CompilationJobs.stop(server);RepairJobs.stop(server);NetworkOperations.forget(player);
            for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());
            old.forEach((pos,state)->level.setBlock(pos,state,3));for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);
            server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();
        };
        try{
            for(var pos:positions)for(long c:RuntimeSignalReader.dependencyChunks(pos)){
                if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}
                level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));
            }
            for(var pos:positions)old.put(pos,level.getBlockState(pos));
            level.setBlock(center,ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),3);
            var wire=(BundleWireEntity)level.getBlockEntity(center);ports(false).forEach((face,config)->wire.setPort(face.side(),config));wire.refreshConnections();
            for(var pos:List.of(input,second))level.setBlock(pos,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),3);
            for(var pos:List.of(output,secondOut))level.setBlock(pos,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState(),3);
            level.setBlock(input.below(),net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(),3);level.setBlock(second.below(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            new Runnable(){int phase,attempts;CompiledNetwork network;
                public void run(){try{
                    if(++attempts>180){String status=network==null?"no network":network.getRuntimeStatus();cleanup.run();h.fail("surface integration timeout phase "+phase+" "+status);return;}
                    NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
                    if(CompilationJobs.isBusy(player)){h.runAfterDelay(1,()->run());return;}
                    if(phase==0){NetworkSelectionData.set(player,center);h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.COMPILE,List.of(),0,"compact").code()==NetworkOperations.Code.QUEUED,"compact circuit queues compilation");phase=1;}
                    else if(phase==1){network=data.findNetworkContaining(level,center);h.assertTrue(network!=null&&network.getElements().size()==5,"compiler discovers face connections without extra blocks");
                        if(network.getEffectiveSignal(output)!=15){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(network.getEffectiveSignal(secondOut)==0,"second internal channel remains LOW");
                        level.setBlock(input.below(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);level.setBlock(second.below(),net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(),3);phase=2;
                    }else if(phase==2){
                        if(network.getEffectiveSignal(output)!=0||network.getEffectiveSignal(secondOut)!=15){h.runAfterDelay(1,()->run());return;}
                        level.setBlock(center,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);NetworkIntegrityManager.checkPosition(level,center);
                        h.assertTrue(network.isDamaged(),"broken compact cable is detected");
                        h.assertTrue(NetworkRepairManager.attempt(level,network,network.getBrokenElement(network.getElementAt(center).getId()),player)==NetworkRepairManager.Outcome.PLACED,"repair restores wire and all four ports together");phase=3;
                    }else if(phase==3){
                        if(network.getEffectiveSignal(secondOut)!=15){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(((BundleWireEntity)level.getBlockEntity(center)).ports().size()==4,"all four IDs restored");
                        h.assertTrue(level.getBlockState(center).getValue(BasicWireBlock.WEST),"repair refreshes the connection arm after restoring metadata");
                        ((BundleWireEntity)level.getBlockEntity(center)).setPort(Direction.NORTH,new BundleWireEntity.Port(1,"123"));NetworkIntegrityManager.checkPosition(level,center);
                        h.assertTrue(NetworkRepairManager.attempt(level,network,network.getBrokenElement(network.getElementAt(center).getId()),player)==NetworkRepairManager.Outcome.PLACED,"repair restores ID with identical block state");phase=4;
                    }else{
                        if(network.getEffectiveSignal(secondOut)!=15){h.runAfterDelay(1,()->run());return;}
                        cleanup.run();h.succeed();return;
                    }
                    h.runAfterDelay(2,()->run());
                }catch(Throwable failure){cleanup.run();throw failure;}}
            }.run();
        }catch(Throwable failure){cleanup.run();throw failure;}
    }
}
