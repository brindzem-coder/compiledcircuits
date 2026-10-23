package com.example.compiledcircuits.network;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("compiledcircuits") @PrefixGameTestTemplate(false)
public class BlueprintGameTests {
    static CompiledNetwork source() {
        var a=new BlockPos(0,3,0);var b=a.east();var c=b.above();
        var input=new CompiledCircuitElement(1,a,CircuitElementType.INPUT,CompiledBlockStateCodec.capture(SignalPorts.with(ModBlocks.SIGNAL_SWITCH.get().defaultBlockState().setValue(BlockStateProperties.POWERED,true),Direction.EAST,true)));
        var wire=new CompiledCircuitElement(2,b,CircuitElementType.WIRE,CompiledBlockStateCodec.capture(NetworkWireBlock.withFace(NetworkWireBlock.withFace(ModBlocks.ID_WIRE.get().defaultBlockState(),Direction.WEST,1),Direction.UP,1))).save();wire.putString("wireId","42");wire.putInt("wireColor",0x123456);
        var output=new CompiledCircuitElement(3,c,CircuitElementType.OUTPUT,CompiledBlockStateCodec.capture(SignalPorts.with(ModBlocks.SIGNAL_LAMP.get().defaultBlockState(),Direction.DOWN,true)));
        var n=new CompiledNetwork(1,"blueprint source",0,"minecraft:overworld",List.of(input,CompiledCircuitElement.load(wire),output));n.restoreLogic();
        var builder=new NetworkTruthTable.Builder(LogicProgram.compileFaces(n.tableStates(),n.wireIds(),n.bundleFaces()));while(!builder.step()){}n.setNetworkTable(builder.finish());n.enableOptimization();return n;
    }
    @GameTest(template="empty",batch="blueprint_transform")
    public static void rotateSpatialCircuitAndPreserveTable(GameTestHelper h){
        var original=source();var blueprint=CircuitBlueprint.capture(original,h.getLevel());var storage=new BlueprintStorage();var key=storage.put(blueprint);blueprint=BlueprintStorage.load(storage.save(new CompoundTag())).get(key);
        for(int turn=0;turn<4;turn++){
            var anchor=new BlockPos(20,30,40);var n=blueprint.network(2,"minecraft:overworld",anchor,turn);
            h.assertTrue(n.isOptimized()&&n.networkTable()!=null,"optimization and whole-network table survive rotation and storage");
            var output=blueprint.position(new BlockPos(1,4,0),anchor,turn);n.runtimeState(true,true);
            for(boolean value:List.of(false,true)){n.evaluateLogic(Map.of(anchor,value));h.assertTrue(n.getEffectiveSignal(output)==(value?15:0),"relocated table preserves HIGH/LOW");}
            var wire=n.getElementAt(blueprint.position(new BlockPos(1,3,0),anchor,turn));
            h.assertTrue(wire.wireId().equals("42")&&wire.wireColor()==0x123456,"wire identity preserved");
            h.assertTrue(NetworkWireBlock.face(wire.resolveState().state().orElseThrow(),CircuitBlueprint.rotation(turn).rotate(Direction.WEST))==1,"arm rotates with coordinates");
        }h.succeed();
    }
    @GameTest(template="empty",batch="blueprint_transform")
    public static void rotatedBundleTablePermutesIndependentChannels(GameTestHelper h){
        var faces=SurfaceBundleGameTests.ports(true);faces.put(new BundleFace(SurfaceBundleGameTests.C,Direction.DOWN),new BundleWireEntity.Port(3,"0"));faces.put(new BundleFace(SurfaceBundleGameTests.C,Direction.UP),new BundleWireEntity.Port(4,"0"));
        var original=SurfaceBundleGameTests.cable(1,faces);var builder=new NetworkTruthTable.Builder(LogicProgram.compileFaces(original.tableStates(),original.wireIds(),original.bundleFaces()));while(!builder.step()){}original.setNetworkTable(builder.finish());original.enableOptimization();
        var blueprint=CircuitBlueprint.capture(original,h.getLevel());var anchor=new BlockPos(50,12,15);
        for(boolean mirrorZ:List.of(false,true))for(boolean mirror:List.of(false,true))for(int turn=0;turn<4;turn++){
            var n=blueprint.network(2,"minecraft:overworld",anchor,turn,mirror,mirrorZ);var graph=LogicProgram.compileFaces(n.tableStates(),n.wireIds(),n.bundleFaces());graph.installTable(n.networkTable(),n.tableStates().keySet());var rotate=CircuitBlueprint.rotation(turn);
            for(int bits=0;bits<8;bits++){
                graph.evaluate(Map.of(),Map.of(new BundleFace(anchor,CircuitBlueprint.transformSide(Direction.WEST,turn,mirror,mirrorZ)),(bits&1)!=0,new BundleFace(anchor,CircuitBlueprint.transformSide(Direction.NORTH,turn,mirror,mirrorZ)),(bits&2)!=0,new BundleFace(anchor,Direction.DOWN),(bits&4)!=0));
                h.assertTrue(graph.faceOutput(new BundleFace(anchor,CircuitBlueprint.transformSide(Direction.EAST,turn,mirror,mirrorZ)))==((bits&1)!=0),"first channel after permutation");
                h.assertTrue(graph.faceOutput(new BundleFace(anchor,CircuitBlueprint.transformSide(Direction.SOUTH,turn,mirror,mirrorZ)))==((bits&2)!=0),"second channel after permutation");
                h.assertTrue(graph.faceOutput(new BundleFace(anchor,Direction.UP))==((bits&4)!=0),"vertical channel after permutation");
            }
        }h.succeed();
    }
    @GameTest(template="empty",batch="blueprint_group_transform")
    public static void groupedMirrorPreservesSpacingAndTables(GameTestHelper h) {
        var first=CircuitBlueprint.capture(source(),h.getLevel());
        var second=CircuitBlueprint.capture(first.network(99,"minecraft:overworld",first.origin().offset(7,2,-5),0),h.getLevel());
        var group=first.append(second);
        h.assertTrue(group.networkCount()==2 && group.append(second).networkCount()==2,"append adds once and preserves separate networks");
        var storage=new BlueprintStorage();var id=storage.put(group);group=BlueprintStorage.load(storage.save(new CompoundTag())).get(id);
        var anchor=new BlockPos(80,30,70);
        for(boolean mirrorZ:List.of(false,true))for(boolean mirror:List.of(false,true))for(int turns=0;turns<4;turns++) {
            var copies=group.networks(10,"minecraft:overworld",anchor,turns,mirror,mirrorZ);
            var expected=new HashSet<BlockPos>();for(var e:group.elements(anchor,turns,mirror,mirrorZ))expected.add(e.getPos());
            var actual=new HashSet<BlockPos>();for(var n:copies)for(var e:n.getElements())actual.add(e.getPos());
            h.assertTrue(actual.equals(expected),"preview and grouped placement use the same transform");
            var displacement=CircuitBlueprint.rotate(new BlockPos(mirror?-7:7,2,mirrorZ?5:-5),turns);
            var firstInput=copies.get(0).getInputs().iterator().next();var secondInput=copies.get(1).getInputs().iterator().next();
            h.assertTrue(secondInput.subtract(firstInput).equals(displacement),"relative spacing and height preserved");
            for(var n:copies) {
                h.assertTrue(n.networkTable()!=null && n.isOptimized(),"each copied network retains optimization");n.runtimeState(true,true);
                for(boolean high:List.of(false,true)) { n.evaluateLogic(Map.of(n.getInputs().iterator().next(),high));h.assertTrue(n.getEffectiveSignal(n.getOutputs().iterator().next())==(high?15:0),"mirrored table preserves logic"); }
            }
        }
        var latch=ModBlocks.D_LATCH.get().defaultBlockState();
        for(boolean mirrorZ:List.of(false,true))for(boolean mirrorX:List.of(false,true))for(var facing:Direction.values())for(int side=0;side<4;side++)for(int turns=0;turns<4;turns++) {
            var state=latch.setValue(EndpointBlock.FACING,facing).setValue(DLatchBlock.WDE_SIDE,side);
            var result=CircuitBlueprint.transformState(state,turns,mirrorX,mirrorZ);
            h.assertTrue(result.getValue(EndpointBlock.FACING)==CircuitBlueprint.transformSide(facing,turns,mirrorX,mirrorZ) && DLatchBlock.enableSide(result)==CircuitBlueprint.transformSide(DLatchBlock.enableSide(state),turns,mirrorX,mirrorZ),"mirror preserves latch port orientation");
        }
        var wrong=second.save();wrong.putString("sourceDimension","minecraft:the_nether");
        boolean denied=false;try{first.append(new CircuitBlueprint(wrong));}catch(IllegalArgumentException expected){denied=true;}
        h.assertTrue(denied,"cross-dimension append cannot invent relative coordinates");
        h.succeed();
    }
    @GameTest(template="empty",batch="blueprint_world",timeoutTicks=400)
    public static void atomicPlacementProtectionAndRuntime(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"blueprint-test"));player.getAbilities().mayBuild=true;player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(ModItems.CIRCUIT_COPIER.get()));
        var anchor=h.absolutePos(new BlockPos(2,7,2));var savedSource=source();data.addNetwork(savedSource);var blueprint=CircuitBlueprint.capture(savedSource,level);var elements=blueprint.elements(anchor,1);Map<BlockPos,BlockState> old=new HashMap<>();Set<Long> forced=new HashSet<>();
        Runnable cleanup=()->{for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());old.forEach((p,s)->level.setBlock(p,s,3));for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();};
        try{
            for(var e:elements)for(long c:RuntimeSignalReader.dependencyChunks(e.getPos())){if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));}
            for(var e:elements){old.put(e.getPos(),level.getBlockState(e.getPos()));level.setBlock(e.getPos(),Blocks.AIR.defaultBlockState(),3);}
            new Runnable(){int attempts;CompiledNetwork copy;
                public void run(){try{
                    if(++attempts>150){String status=copy==null?"none":copy.getRuntimeStatus();cleanup.run();h.fail("blueprint runtime timeout "+status);return;}
                    if(elements.stream().anyMatch(e->!NetworkRuntime.isChunkAvailable(level,RuntimeSignalReader.chunk(e.getPos())))){h.runAfterDelay(1,()->run());return;}
                    if(copy==null){
                        var obstruction=elements.get(1).getPos();level.setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
                        boolean blocked=false;try{BlueprintService.paste(player,blueprint,anchor,1);}catch(IllegalArgumentException expected){blocked=true;}
                        h.assertTrue(blocked&&level.getBlockState(anchor).isAir()&&data.getNetworks().size()==1,"occupied destination leaves no partial construction");level.setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
                        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> deny=e->{if(e.getEntity()==player)e.setCanceled(true);};net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
                        try{boolean denied=false;try{BlueprintService.paste(player,blueprint,anchor,1);}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied&&elements.stream().allMatch(e->level.getBlockState(e.getPos()).isAir())&&data.getNetworks().size()==1,"protection denial rolls back every block and membership");}
                        finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);}
                        copy=BlueprintService.paste(player,blueprint,anchor,1);h.assertTrue(copy.getId()!=savedSource.getId()&&data.getNetwork(savedSource.getId())==savedSource,"copy receives an independent network ID");h.assertTrue(copy.networkTable()!=null&&copy.isOptimized(),"paste activates saved optimizations");
                    }
                    NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
                    var output=elements.stream().filter(e->e.getType()==CircuitElementType.OUTPUT).findFirst().orElseThrow().getPos();
                    if(!level.getBlockState(output).getValue(BlockStateProperties.LIT)){h.runAfterDelay(1,()->run());return;}
                    h.assertTrue(!copy.isDamaged()&&copy.getEffectiveSignal(output)==15,"pasted 3D circuit runs automatically");cleanup.run();h.succeed();
                }catch(Throwable failure){cleanup.run();throw failure;}}
            }.run();
        }catch(Throwable failure){cleanup.run();throw failure;}
    }
    @GameTest(template="empty",batch="blueprint_group_world",timeoutTicks=400)
    public static void atomicGroupPlacementProtectionAndRuntime(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"blueprint-test"));player.getAbilities().mayBuild=true;player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(ModItems.CIRCUIT_COPIER.get()));
        var anchor=h.absolutePos(new BlockPos(2,7,2));var savedSource=source();data.addNetwork(savedSource);var first=CircuitBlueprint.capture(savedSource,level);var second=CircuitBlueprint.capture(first.network(99,"minecraft:overworld",first.origin().offset(4,0,0),0),level);var blueprint=first.append(second);var elements=blueprint.elements(anchor,1,true);Map<BlockPos,BlockState> old=new HashMap<>();Set<Long> forced=new HashSet<>();
        Runnable cleanup=()->{for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());old.forEach((p,s)->level.setBlock(p,s,3));for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();};
        try{
            for(var e:elements)for(long c:RuntimeSignalReader.dependencyChunks(e.getPos())){if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));}
            for(var e:elements){old.put(e.getPos(),level.getBlockState(e.getPos()));level.setBlock(e.getPos(),Blocks.AIR.defaultBlockState(),3);}
            new Runnable(){int attempts;CompiledNetwork copy;List<CompiledNetwork> copies;
                public void run(){try{
                    if(++attempts>150){String status=copy==null?"none":copy.getRuntimeStatus();cleanup.run();h.fail("blueprint runtime timeout "+status);return;}
                    if(elements.stream().anyMatch(e->!NetworkRuntime.isChunkAvailable(level,RuntimeSignalReader.chunk(e.getPos())))){h.runAfterDelay(1,()->run());return;}
                    if(copy==null){
                        var obstruction=elements.get(elements.size()-1).getPos();level.setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
                        boolean blocked=false;try{BlueprintService.pasteAll(player,blueprint,anchor,1,true);}catch(IllegalArgumentException expected){blocked=true;}
                        h.assertTrue(blocked&&level.getBlockState(anchor).isAir()&&data.getNetworks().size()==1,"occupied destination leaves no partial construction");level.setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
                        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> deny=e->{if(e.getEntity()==player)e.setCanceled(true);};net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
                        try{boolean denied=false;try{BlueprintService.pasteAll(player,blueprint,anchor,1,true);}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied&&elements.stream().allMatch(e->level.getBlockState(e.getPos()).isAir())&&data.getNetworks().size()==1,"protection denial rolls back every block and membership");}
                        finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);}
                        copies=BlueprintService.pasteAll(player,blueprint,anchor,1,true);copy=copies.get(0);h.assertTrue(copies.size()==2 && data.getNetworks().size()==3,"group creates two independent networks atomically");h.assertTrue(copy.getId()!=savedSource.getId()&&data.getNetwork(savedSource.getId())==savedSource,"copy receives an independent network ID");h.assertTrue(copy.networkTable()!=null&&copy.isOptimized(),"paste activates saved optimizations");
                    }
                    NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
                    var output=elements.stream().filter(e->e.getType()==CircuitElementType.OUTPUT).findFirst().orElseThrow().getPos();
                    if(copies.stream().anyMatch(n->!level.getBlockState(n.getOutputs().iterator().next()).getValue(BlockStateProperties.LIT))){h.runAfterDelay(1,()->run());return;}
                    h.assertTrue(!copy.isDamaged()&&copy.getEffectiveSignal(output)==15,"pasted 3D circuit runs automatically");cleanup.run();h.succeed();
                }catch(Throwable failure){cleanup.run();throw failure;}}
            }.run();
        }catch(Throwable failure){cleanup.run();throw failure;}
    }
}
