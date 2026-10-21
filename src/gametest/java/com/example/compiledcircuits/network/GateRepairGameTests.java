package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.ModBlocks;
import com.example.compiledcircuits.networking.NetworkBulkActionC2SPacket;
import com.example.compiledcircuits.config.ServerConfig;
import net.minecraft.core.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class GateRepairGameTests {
    @GameTest(template="empty",batch="gate_gui_repair",timeoutTicks=300)
    public static void guiRepairRestoresAllGatesPortsAndNotDirections(GameTestHelper h) {
        repairFixture(h,false);
    }
    @GameTest(template="empty",batch="gate_gui_face_repair",timeoutTicks=300)
    public static void guiRepairRestoresEditedFacesInPlace(GameTestHelper h) {
        repairFixture(h,true);
    }
    private static void repairFixture(GameTestHelper h, boolean editFaces) {
        var level=h.getLevel();var server=level.getServer();var storage=server.overworld().getDataStorage();
        var original=NetworkSavedData.get(server);var data=new NetworkSavedData();storage.set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"gate-repair-test"));
        player.getAbilities().mayBuild=true;
        var policies=List.of(ServerConfig.TOTAL_WORK,ServerConfig.REPAIR_WORK,ServerConfig.POINT_WORK,ServerConfig.RECHECK_WORK,ServerConfig.AUDIT_WORK);
        var savedPolicies=policies.stream().map(v->v.get()).toList();for(var policy:policies)policy.set(policy.getDefault());
        Map<BlockPos,BlockState> old=new LinkedHashMap<>(),expected=new LinkedHashMap<>();Set<Long> forced=new HashSet<>();
        List<CompiledNetwork> networks=new ArrayList<>();
        Runnable cleanup=()->{
            RepairJobs.stop(server);NetworkOperations.forget(player);
            for(var n:networks)data.removeNetwork(n.getId());
            old.forEach((pos,state)->level.setBlock(pos,state,3));
            for(long c:forced)level.setChunkForced(ChunkPos.getX(c),ChunkPos.getZ(c),false);
            storage.set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();
            for(int i=0;i<policies.size();i++)policies.get(i).set(savedPolicies.get(i));
        };
        try {
            List<Map<BlockPos,BlockState>> layouts=new ArrayList<>();int row=0;
            Block[] bodies={ModBlocks.AND_GATE.get(),ModBlocks.OR_GATE.get(),ModBlocks.NAND_GATE.get(),ModBlocks.NOR_GATE.get(),ModBlocks.XOR_GATE.get(),ModBlocks.XNOR_GATE.get()};
            for(Block body:bodies){
                BlockPos center=h.absolutePos(new BlockPos(5,6,5+row++*5));Map<BlockPos,BlockState> s=new LinkedHashMap<>();
                s.put(center.west(2),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
                s.put(center.west(),ModBlocks.GATE_INPUT.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.UP));
                s.put(center,GateBodyBlock.withPort(GateBodyBlock.withPort(GateBodyBlock.withPort(body.defaultBlockState(),Direction.UP,1),Direction.DOWN,2),Direction.NORTH,3));
                s.put(center.east(),ModBlocks.GATE_OUTPUT.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.DOWN));
                s.put(center.east(2),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());layouts.add(s);
            }
            for(Direction direction:Direction.values()){
                BlockPos center=h.absolutePos(new BlockPos(5,6,5+row++*5));Map<BlockPos,BlockState> s=new LinkedHashMap<>();
                s.put(center.relative(direction.getOpposite()),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
                s.put(center,ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,direction));
                s.put(center.relative(direction),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());layouts.add(s);
            }
            for(var state:ModBlocks.D_LATCH.get().getStateDefinition().getPossibleStates()){
                BlockPos center=h.absolutePos(new BlockPos(5,6,5+row++*5));Map<BlockPos,BlockState> s=new LinkedHashMap<>();
                Direction q=state.getValue(EndpointBlock.FACING);
                s.put(center,state);s.put(center.relative(q.getOpposite()),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
                s.put(center.relative(DLatchBlock.enableSide(state)),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
                s.put(center.relative(q),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());layouts.add(s);
            }
            for(var s:layouts){
                for(var pos:s.keySet())for(long c:RuntimeSignalReader.dependencyChunks(pos)){
                    if(!level.getForcedChunks().contains(c)){level.setChunkForced(ChunkPos.getX(c),ChunkPos.getZ(c),true);forced.add(c);}
                    level.getChunk(ChunkPos.getX(c),ChunkPos.getZ(c));
                }
                s.forEach((pos,state)->{old.putIfAbsent(pos,level.getBlockState(pos));level.setBlock(pos,state,3);});
                List<CompiledCircuitElement> elements=new ArrayList<>();
                for(var item:s.entrySet()){
                    var state=level.getBlockState(item.getKey());Block b=state.getBlock();
                    var type=b instanceof GateBodyBlock||b instanceof NotGateBlock||b instanceof DLatchBlock?CircuitElementType.GATE:b instanceof GatePortBlock port?(port.isInput()?CircuitElementType.GATE_INPUT:CircuitElementType.GATE_OUTPUT):b instanceof InputEndpointBlock?CircuitElementType.INPUT:CircuitElementType.OUTPUT;
                    elements.add(new CompiledCircuitElement(elements.size()+1,item.getKey(),type,CompiledBlockStateCodec.capture(state)));
                    if(type==CircuitElementType.GATE||type==CircuitElementType.GATE_INPUT||type==CircuitElementType.GATE_OUTPUT)expected.put(item.getKey(),state);
                }
                var n=new CompiledNetwork(data.getNextNetworkId(),"repair-gates-"+networks.size(),0,level.dimension().location().toString(),elements);
                data.addNetwork(n);networks.add(n);
            }
            expected.forEach((pos,state)-> {
                var damaged=Blocks.AIR.defaultBlockState();
                if(editFaces && state.getBlock() instanceof GateBodyBlock) {
                    // Remove input, change output to input, remove separator, add a new separator and output.
                    damaged=GateBodyBlock.withPort(GateBodyBlock.withPort(GateBodyBlock.withPort(
                            GateBodyBlock.withPort(GateBodyBlock.withPort(state,Direction.UP,0),Direction.DOWN,1),
                            Direction.NORTH,0),Direction.SOUTH,3),Direction.EAST,2);
                }
                level.setBlock(pos,damaged,3);
            });
            for(var n:networks)for(var element:n.getElements())NetworkIntegrityManager.checkElement(level,data,n,element);
            h.assertTrue(networks.stream().mapToInt(n->n.getBrokenElements().size()).sum()==48,"all gate bodies, ports, NOT and 24 latch orientations marked damaged");
            if(editFaces) {
                var n=networks.get(0);
                var element=n.getElements().stream().filter(e->e.resolveState().state().orElseThrow().getBlock() instanceof GateBodyBlock).findFirst().orElseThrow();
                var pos=element.getPos();var edited=level.getBlockState(pos);
                for(var obstructing:new BlockState[]{Blocks.STONE.defaultBlockState(),ModBlocks.OR_GATE.get().defaultBlockState()}) {
                    level.setBlock(pos,obstructing,3);NetworkIntegrityManager.checkElement(level,data,n,element);
                    h.assertTrue(NetworkRepairManager.attempt(level,n,n.getBrokenElement(element.getId()),player)==NetworkRepairManager.Outcome.OCCUPIED,"different block is never overwritten");
                    h.assertTrue(level.getBlockState(pos)==obstructing,"obstruction remains intact");
                }
                level.setBlock(pos,edited,3);NetworkIntegrityManager.checkElement(level,data,n,element);
            }
            var packet=new NetworkBulkActionC2SPacket(NetworkBulkActionC2SPacket.BulkAction.REPAIR_NETWORKS,networks.stream().map(CompiledNetwork::getId).toList(),0);
            h.assertTrue(NetworkBulkActionC2SPacket.execute(packet,player).code()==NetworkOperations.Code.QUEUED,"GUI bulk repair request accepted");
            new Runnable(){int attempts;public void run(){try{
                NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
                if(RepairJobs.isBusy(player)||networks.stream().anyMatch(n->n.isDamaged()||n.isIntegrityPending())){
                    if(++attempts>200){h.fail("Repair did not restore all gate elements");return;}
                    h.runAfterDelay(1,()->run());return;
                }
                for(var item:expected.entrySet())h.assertTrue(level.getBlockState(item.getKey()).equals(item.getValue()),"exact saved state restored at "+item.getKey());
                h.assertTrue(networks.stream().noneMatch(CompiledNetwork::isDamaged),"integrity confirms all repairs");
                cleanup.run();h.succeed();
            }catch(Throwable failure){cleanup.run();throw failure;}}}.run();
        }catch(Throwable failure){cleanup.run();throw failure;}
    }
}
