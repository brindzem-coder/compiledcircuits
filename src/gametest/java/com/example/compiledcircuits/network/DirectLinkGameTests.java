package com.example.compiledcircuits.network;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class DirectLinkGameTests {
    static BlockState port(Direction side,int kind){return NetworkWireBlock.withFace(ModBlocks.NETWORK_WIRE.get().defaultBlockState(),side,kind);}
    static CompiledNetwork network(int id,Map<BlockPos,BlockState> states){
        List<CompiledCircuitElement> elements=new ArrayList<>();
        states.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            var b=e.getValue().getBlock();var role=b instanceof InputEndpointBlock?CircuitElementType.INPUT:b instanceof OutputEndpointBlock?CircuitElementType.OUTPUT:b instanceof NotGateBlock?CircuitElementType.GATE:CircuitElementType.WIRE;
            elements.add(new CompiledCircuitElement(elements.size()+1,e.getKey(),role,CompiledBlockStateCodec.capture(e.getValue())));
        });
        var n=new CompiledNetwork(id,"direct-test",0,"minecraft:overworld",elements);n.restoreLogic();n.runtimeState(true,true);return n;
    }
    static void settle(DirectLinks table){for(int i=0;i<100&&table.hasWork();i++)table.step();if(table.hasWork())throw new AssertionError("direct queue failed to drain");}
    @GameTest(template="empty",batch="direct_tables",timeoutTicks=100)
    public static void longChainAndUnlink(GameTestHelper h){
        var data=new NetworkSavedData();var table=new DirectLinks(data,n->{});List<CompiledNetwork> nets=new ArrayList<>();
        BlockPos input=new BlockPos(0,-1,0),output=new BlockPos(79,1,0);
        for(int i=0;i<80;i++){
            Map<BlockPos,BlockState> states=new HashMap<>();states.put(new BlockPos(i,0,0),NetworkWireBlock.withFace(port(Direction.WEST,2),Direction.EAST,3));
            if(i==0)states.put(input,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
            if(i==79)states.put(output,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
            nets.add(network(i+1,states));
        }
        for(int i=79;i>=0;i--){table.add(nets.get(i));table.update(nets.get(i),i==0?Map.of(input,true):Map.of());}
        settle(table);h.assertTrue(table.linkCount()==79,"reverse registration finds every link");
        h.assertTrue(nets.get(79).getEffectiveSignal(output)==15,"80 networks propagate in one settle without world reads");
        table.update(nets.get(0),Map.of(input,true));h.assertTrue(!table.hasWork(),"unchanged inputs do not enqueue work");
        table.update(nets.get(0),Map.of(input,false));settle(table);h.assertTrue(nets.get(79).getEffectiveSignal(output)==0,"LOW propagates");
        table.update(nets.get(0),Map.of(input,true));settle(table);
        table.remove(nets.get(0));h.assertTrue(table.linkCount()==78,"decompilation deletes table entry immediately");
        h.assertTrue(nets.get(79).getEffectiveSignal(output)==0,"unlink immediately gates stale HIGH");
        settle(table);h.assertTrue(nets.get(79).getEffectiveSignal(output)==0,"disconnected input settles LOW");
        table.add(nets.get(0));table.update(nets.get(0),Map.of(input,true));settle(table);
        h.assertTrue(nets.get(79).getEffectiveSignal(output)==15,"reconnection imports current HIGH");
        table.invalidate(nets.get(0));settle(table);h.assertTrue(nets.get(79).getEffectiveSignal(output)==0,"unavailable source gates linked group");
        table.update(nets.get(0),Map.of(input,true));settle(table);h.assertTrue(nets.get(79).getEffectiveSignal(output)==15,"availability recovery");
        h.succeed();
    }
    static List<CompiledNetwork> loop(boolean secondNot){
        Map<BlockPos,BlockState> a=new HashMap<>(),b=new HashMap<>();
        a.put(new BlockPos(0,0,0),port(Direction.SOUTH,2));
        a.put(new BlockPos(-1,0,0),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.WEST));
        a.put(new BlockPos(-2,0,0),port(Direction.SOUTH,3));
        a.put(new BlockPos(0,1,0),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        b.put(new BlockPos(-2,0,1),port(Direction.NORTH,2));
        b.put(new BlockPos(-1,0,1),secondNot?ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST):ModBlocks.BASIC_WIRE.get().defaultBlockState());
        b.put(new BlockPos(0,0,1),port(Direction.NORTH,3));
        b.put(new BlockPos(-2,-1,1),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        return List.of(network(1,a),network(2,b));
    }
    @GameTest(template="empty",batch="direct_tables",timeoutTicks=100)
    public static void feedbackMemoryReloadAndOscillation(GameTestHelper h){
        var aIn=new BlockPos(0,1,0);var bIn=new BlockPos(-2,-1,1);var aOut=new BlockPos(-2,0,0);var bOut=new BlockPos(0,0,1);
        var nets=loop(true);var table=new DirectLinks(new NetworkSavedData(),n->{});
        for(var n:nets)table.add(n);
        table.update(nets.get(0),Map.of(aIn,true));table.update(nets.get(1),Map.of(bIn,false));settle(table);
        h.assertTrue(nets.get(0).getEffectiveSignal(aOut)==0&&nets.get(1).getEffectiveSignal(bOut)==15,"set cross-network latch");
        table.update(nets.get(0),Map.of(aIn,false));settle(table);
        h.assertTrue(nets.get(1).getEffectiveSignal(bOut)==15,"hold cross-network state");
        var loaded=new ArrayList<CompiledNetwork>();for(var n:nets){var copy=CompiledNetwork.load(n.save());copy.restoreLogic();copy.runtimeState(true,true);loaded.add(copy);}
        table=new DirectLinks(new NetworkSavedData(),n->{});for(var n:loaded){table.add(n);table.update(n,Map.of());}settle(table);
        h.assertTrue(loaded.get(1).getEffectiveSignal(bOut)==15,"feedback memory survives per-network NBT reload");
        table.update(loaded.get(1),Map.of(bIn,true));settle(table);h.assertTrue(loaded.get(0).getEffectiveSignal(aOut)==15,"reset cross-network latch");
        nets=loop(false);table=new DirectLinks(new NetworkSavedData(),n->{});for(var n:nets)table.add(n);
        table.update(nets.get(0),Map.of(aIn,true));table.update(nets.get(1),Map.of());settle(table);
        var memory=nets.get(0).nodeMemory();table.update(nets.get(0),Map.of(aIn,false));settle(table);
        h.assertTrue(!nets.get(0).getLogicError().isEmpty(),"oscillation reported instead of recursive loop");
        h.assertTrue(nets.get(0).nodeMemory().equals(memory),"unstable evaluation preserves last stable memory");
        table.update(nets.get(0),Map.of(aIn,true));settle(table);h.assertTrue(nets.get(0).getLogicError().isEmpty(),"input change recovers oscillator");
        h.succeed();
    }

    @GameTest(template="empty",batch="direct_server",timeoutTicks=400)
    public static void compileTransferRepairAndDecompile(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();
        server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"direct-compiler"));player.getAbilities().mayBuild=true;
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        var source=h.absolutePos(new BlockPos(4,5,4));var target=source.east();var input=source.west();var output=target.north();var power=input.below();var plain=target.above();
        Map<BlockPos,BlockState> old=new LinkedHashMap<>();Set<Long> forced=new HashSet<>();
        Runnable cleanup=()->{
            CompilationJobs.stop(server);RepairJobs.stop(server);NetworkOperations.forget(player);
            for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());
            old.forEach((p,state)->level.setBlock(p,state,3));for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);
            server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();
        };
        try {
            for(var p:List.of(source,target,input,output,power,plain))for(long c:RuntimeSignalReader.dependencyChunks(p)){
                if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));
            }
            for(var p:List.of(source,target,input,output,power,plain))old.put(p,level.getBlockState(p));
            level.setBlock(input,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),3);level.setBlock(output,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState(),3);
            level.setBlock(source,ModBlocks.BASIC_WIRE.get().defaultBlockState(),3);level.setBlock(target,ModBlocks.BASIC_WIRE.get().defaultBlockState(),3);
            level.setBlock(plain,ModBlocks.BASIC_WIRE.get().defaultBlockState(),3);
            level.setBlock(power,net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            for(var p:List.of(source,target)){
                boolean out=p.equals(source);var item=out?ModItems.NETWORK_OUTPUT.get():ModItems.NETWORK_INPUT.get();var stack=new net.minecraft.world.item.ItemStack(item);player.setItemInHand(hand,stack);
                var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(p),out?Direction.EAST:Direction.WEST,p,false);
                h.assertTrue(item.useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,hit)).consumesAction(),"port tool installs on wire");
                h.assertTrue(stack.getCount()==1,"port tool reusable");
            }
            h.assertTrue(!CircuitConnections.connects(level.getBlockState(source),level.getBlockState(target),Direction.EAST),"uncompiled ports separate selection/compilation graphs");
            NetworkSelectionData.set(player,source);
            h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.COMPILE,List.of(),0,"source").code()==NetworkOperations.Code.QUEUED,"source with direct output compiles");
            new Runnable(){int phase,attempts;CompiledNetwork a,b;net.minecraft.nbt.CompoundTag saved;public void run(){try{
                if(++attempts>350){cleanup.run();h.fail("direct integration timeout at phase "+phase);return;}
                NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
                if(CompilationJobs.isBusy(player)){h.runAfterDelay(1,()->run());return;}
                if(phase==0){
                    h.assertTrue(data.getNetworks().size()==1,"source compilation excludes target wire");
                    a=data.findNetworkContaining(level,source);NetworkSelectionData.set(player,target);
                    h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.COMPILE,List.of(),0,"target").code()==NetworkOperations.Code.QUEUED,"target with direct input compiles");phase=1;
                }else if(phase==1){
                    b=data.findNetworkContaining(level,target);h.assertTrue(b!=null&&b!=a,"distinct networks");
                    if(!a.getRuntimeStatus().equals("READY")||!b.getRuntimeStatus().equals("READY")||b.getEffectiveSignal(output)!=15){h.runAfterDelay(1,()->run());return;}
                    h.assertTrue(data.runtime().directLinkCount()==1,"registered one link");
                    h.assertTrue(level.getBlockState(source).getSignal(level,source,Direction.EAST)==0,"direct port emits no vanilla redstone");
                    level.setBlock(power,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);NetworkRuntime.inputChanged(level,input);phase=2;
                }else if(phase==2){
                    if(b.getEffectiveSignal(output)!=0){h.runAfterDelay(1,()->run());return;}
                    level.setBlock(power,net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(),3);NetworkRuntime.inputChanged(level,input);phase=3;
                }else if(phase==3){
                    if(b.getEffectiveSignal(output)!=15){h.runAfterDelay(1,()->run());return;}
                    saved=a.save();data.removeNetwork(a.getId());h.assertTrue(data.runtime().directLinkCount()==0,"decompile unlinks immediately");h.assertTrue(b.getEffectiveSignal(output)==0,"decompile removes stale HIGH immediately");
                    a=CompiledNetwork.load(saved);data.addNetwork(a);phase=4;
                }else if(phase==4){
                    if(b.getEffectiveSignal(output)!=15){h.runAfterDelay(1,()->run());return;}
                    level.setBlock(target,NetworkWireBlock.withFace(level.getBlockState(target),Direction.WEST,0),3);NetworkIntegrityManager.checkPosition(level,target);NetworkRuntime.flush(server);
                    h.assertTrue(b.isDamaged()&&b.getEffectiveSignal(output)==0,"edited direct port gates damaged network");
                    var e=b.getElementAt(target);h.assertTrue(NetworkRepairManager.attempt(level,b,b.getBrokenElement(e.getId()),player)==NetworkRepairManager.Outcome.PLACED,"repair restores direct port in place");phase=5;
                }else if(phase==5 || phase==7 || phase==9){
                    if(b.isDamaged()||b.isIntegrityPending()||b.getEffectiveSignal(output)!=15){h.runAfterDelay(1,()->run());return;}
                    var pos=phase==9?target:plain;
                    var side=phase==9?Direction.WEST:Direction.UP;
                    player.setItemInHand(hand,new net.minecraft.world.item.ItemStack(ModItems.NETWORK_INPUT.get()));
                    var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),side,pos,false);
                    ModItems.NETWORK_INPUT.get().useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,hit));
                    NetworkIntegrityManager.checkPosition(level,pos);
                    h.assertTrue(b.isDamaged(),"port edit detected on either wire variant");phase++;
                }else if(phase==6 || phase==10){
                    var pos=phase==6?plain:target;var side=phase==6?Direction.UP:Direction.WEST;
                    player.setItemInHand(hand,new net.minecraft.world.item.ItemStack(ModItems.NETWORK_INPUT.get()));
                    var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),side,pos,false);
                    ModItems.NETWORK_INPUT.get().useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,hit));
                    NetworkIntegrityManager.checkPosition(level,pos);
                    h.assertTrue(!b.isDamaged(),"manual port restoration clears damage");phase++;
                }else if(phase==8){
                    h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.REPAIR,List.of(b.getId()),0,"").code()==NetworkOperations.Code.QUEUED,"GUI repair queues wire variant restoration");phase++;
                }else {
                    if(b.isDamaged()||b.isIntegrityPending()||b.getEffectiveSignal(output)!=15){h.runAfterDelay(1,()->run());return;}
                    cleanup.run();h.succeed();return;
                }
                h.runAfterDelay(1,()->run());
            }catch(Throwable failure){cleanup.run();throw failure;}}}.run();
        }catch(Throwable failure){cleanup.run();throw failure;}
    }

}
