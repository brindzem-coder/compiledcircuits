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
public class BundleGameTests {
    private static BlockPos p(int x, int z) { return new BlockPos(x, 2, z); }
    private static BlockState port(boolean input, boolean external, Direction facing) {
        return (external ? (input ? ModBlocks.BUNDLE_NETWORK_INPUT : ModBlocks.BUNDLE_NETWORK_OUTPUT)
                : (input ? ModBlocks.BUNDLE_INPUT : ModBlocks.BUNDLE_OUTPUT)).get().defaultBlockState().setValue(EndpointBlock.FACING, facing);
    }
    private static class Rig {
        final Map<BlockPos,BlockState> states = new HashMap<>(); final Map<BlockPos,String> ids = new HashMap<>();
        void cable(int start, int end, int z) { for (int x=start;x<=end;x++) states.put(p(x,z),ModBlocks.BUNDLED_WIRE.get().defaultBlockState()); }
        void port(int x,int z,boolean input,boolean external,Direction face,String id) { states.put(p(x,z),BundleGameTests.port(input,external,face)); ids.put(p(x,z),id); }
        void endpoint(int x,int z,boolean input) { states.put(p(x,z),(input?ModBlocks.INPUT_ENDPOINT:ModBlocks.OUTPUT_ENDPOINT).get().defaultBlockState()); }
        LogicProgram compile() { return LogicProgram.compile(states,ids); }
        CompiledNetwork network(int id) {
            List<CompiledCircuitElement> elements = new ArrayList<>();
            states.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                var block=e.getValue().getBlock();
                var role=block instanceof InputEndpointBlock?CircuitElementType.INPUT:block instanceof OutputEndpointBlock?CircuitElementType.OUTPUT:block instanceof NotGateBlock?CircuitElementType.GATE:CircuitElementType.WIRE;
                var element=new CompiledCircuitElement(elements.size()+1,e.getKey(),role,CompiledBlockStateCodec.capture(e.getValue()));
                var tag=element.save(); if(block instanceof BundlePortBlock)tag.putString("channelId",ids.get(e.getKey()));
                elements.add(CompiledCircuitElement.load(tag));
            });
            var network=new CompiledNetwork(id,"bundle-test",0,"minecraft:overworld",elements);
            network.restoreLogic(); network.runtimeState(true,true); return network;
        }
    }
    private static Rig internal() {
        var r=new Rig();r.cable(0,6,0);
        r.port(0,1,true,false,Direction.SOUTH,"9");r.endpoint(0,2,true);
        r.port(6,1,false,false,Direction.SOUTH,"9");r.endpoint(6,2,false);return r;
    }
    private static void rejects(GameTestHelper h,Runnable work,String expected) {
        try {work.run();h.fail("Expected rejection: "+expected);}
        catch(IllegalArgumentException invalid) {h.assertTrue(invalid.getMessage().contains(expected),"specific diagnostic: "+invalid.getMessage());}
    }
    @GameTest(template="empty",batch="bundles")
    public static void independentChannelsAndFanout(GameTestHelper h) {
        var r=internal();r.port(3,-1,true,false,Direction.NORTH,"123456789012345678901234567890");r.endpoint(3,-2,true);
        r.port(6,-1,false,false,Direction.NORTH,"123456789012345678901234567890");r.endpoint(6,-2,false);
        r.port(3,1,false,false,Direction.SOUTH,"9");r.endpoint(3,2,false);
        var graph=r.compile();
        for(int bits=0;bits<4;bits++){
            var result=graph.evaluate(Map.of(p(0,2),(bits&1)!=0,p(3,-2),(bits&2)!=0));
            h.assertTrue(result.get(p(6,2))==((bits&1)!=0)&&result.get(p(3,2))==((bits&1)!=0),"channel 9 fans out independently");
            h.assertTrue(result.get(p(6,-2))==((bits&2)!=0),"large numeric channel remains independent");
        }
        h.assertTrue(graph.nodeCount()==5,"internal ports and cable create no runtime nodes");h.succeed();
    }
    @GameTest(template="empty",batch="bundles")
    public static void duplicateAndMissingSources(GameTestHelper h) {
        var r=internal();r.port(3,1,true,false,Direction.SOUTH,"9");r.endpoint(3,2,true);
        rejects(h,r::compile,"multiple inputs");r.ids.put(p(3,1),"10");r.ids.put(p(6,1),"11");
        rejects(h,r::compile,"has no input");h.succeed();
    }
    @GameTest(template="empty",batch="bundles")
    public static void cableComponentsScopeIds(GameTestHelper h) {
        var r=internal();r.cable(0,6,6);r.port(0,7,true,false,Direction.SOUTH,"9");r.endpoint(0,8,true);
        r.port(6,7,false,false,Direction.SOUTH,"9");r.endpoint(6,8,false);
        var result=r.compile().evaluate(Map.of(p(0,2),true,p(0,8),false));
        h.assertTrue(result.get(p(6,2))&&!result.get(p(6,8)),"same ID on separate cables stays independent");h.succeed();
    }
    @GameTest(template="empty",batch="bundles")
    public static void gatesBetweenChannelsArePreserved(GameTestHelper h) {
        var r=internal();r.port(2,1,false,false,Direction.SOUTH,"9");r.states.put(p(2,2),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        r.states.put(p(3,2),ModBlocks.NOT_GATE.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));
        r.states.put(p(4,2),ModBlocks.BASIC_WIRE.get().defaultBlockState());r.port(4,1,true,false,Direction.SOUTH,"10");r.ids.put(p(6,1),"10");
        var graph=r.compile();h.assertTrue(!graph.evaluate(Map.of(p(0,2),true)).get(p(6,2)),"gate is not bypassed by cable routing");
        h.assertTrue(graph.evaluate(Map.of(p(0,2),false)).get(p(6,2)),"gate evaluates between distinct channels");
        r.states.remove(p(0,1));r.ids.remove(p(0,1));r.ids.put(p(4,1),"9");r.ids.put(p(6,1),"9");
        rejects(h,r::compile,"Feedback through a bundled");h.succeed();
    }
    @GameTest(template="empty",batch="bundles")
    public static void rawWiresCannotBypassPorts(GameTestHelper h) {
        var r=internal();r.states.put(p(3,1),ModBlocks.BASIC_WIRE.get().defaultBlockState());r.endpoint(3,2,false);
        var result=r.compile().evaluate(Map.of(p(0,2),true));h.assertTrue(!result.get(p(3,2)),"touching cable is not an electrical connection");
        h.assertTrue(!CircuitConnections.connects(r.states.get(p(3,0)),r.states.get(p(3,1)),Direction.SOUTH),"selector does not cross raw wire/cable adjacency");
        h.assertTrue(CircuitConnections.connects(r.states.get(p(0,0)),r.states.get(p(0,1)),Direction.SOUTH),"selector includes the port and entire cable");
        h.assertTrue(CompiledBlockStateMatcher.match(new CompiledCircuitElement(1,p(3,0),CircuitElementType.WIRE,CompiledBlockStateCodec.capture(r.states.get(p(3,0)))),ModBlocks.NETWORK_WIRE.get().defaultBlockState(),false)==CompiledBlockStateMatcher.Match.BLOCK_MISMATCH,"ordinary wire cannot replace bundled cable");h.succeed();
    }
    @GameTest(template="empty",batch="bundles")
    public static void transportOnlyCompilationAndReload(GameTestHelper h) {
        var r=new Rig();r.cable(0,4,0);r.port(-1,0,true,true,Direction.WEST,"0");r.port(5,0,false,true,Direction.EAST,"0");
        var graph=r.compile();h.assertTrue(graph.nodeCount()==2&&graph.evaluate(Map.of(p(-1,0),true)).get(p(5,0)),"transport compiles to boundary nodes only");
        var network=r.network(1);var loaded=CompiledNetwork.load(network.save());loaded.restoreLogic();
        h.assertTrue(loaded.directPorts().size()==2&&loaded.getElementAt(p(-1,0)).channelId().equals("0"),"ports and channel 0 persist");
        var builder=new CompiledNetwork.Builder();for(var e:network.getElements())builder.add(e);
        h.assertTrue(!builder.inputsEmpty()&&!builder.outputsEmpty(),"transport-only network satisfies compilation endpoint requirements");
        builder.seal("transport","minecraft:overworld");h.succeed();
    }
    @GameTest(template="empty",batch="bundles")
    public static void interNetworkCycleRejectedBeforePublication(GameTestHelper h) {
        var cable=new Rig();cable.cable(0,4,0);cable.port(-1,0,true,true,Direction.WEST,"7");cable.port(5,0,false,true,Direction.EAST,"7");
        var feedback=new Rig();
        feedback.states.put(p(-2,0),DirectLinkGameTests.port(Direction.EAST,3));feedback.states.put(p(6,0),DirectLinkGameTests.port(Direction.WEST,2));
        for(int x=-2;x<=6;x++)feedback.states.put(p(x,1),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        var table=new DirectLinks(new NetworkSavedData(),n->{});var b=feedback.network(2);table.add(b);
        rejects(h,()->table.validateCompilation(cable.network(1)),"Feedback through a bundled");
        h.assertTrue(table.linkCount()==0,"failed validation has no published endpoints");h.succeed();
    }
    @GameTest(template="empty",batch="bundles")
    public static void independentSignalsAllowNetworkLevelCycle(GameTestHelper h) {
        var cable=new Rig();cable.cable(0,6,0);
        cable.port(0,1,true,true,Direction.SOUTH,"1");cable.port(2,1,false,true,Direction.SOUTH,"1");
        cable.port(4,1,true,true,Direction.SOUTH,"2");cable.port(6,1,false,true,Direction.SOUTH,"2");
        var peer=new Rig();
        for(int x:new int[]{0,2,4,6}) {peer.states.put(p(x,2),DirectLinkGameTests.port(Direction.NORTH,x%4==0?3:2));peer.endpoint(x,3,x%4==0);}
        var table=new DirectLinks(new NetworkSavedData(),n->{});table.add(peer.network(2));table.validateCompilation(cable.network(1));h.succeed();
    }
    @GameTest(template="empty",batch="bundles")
    public static void interNetworkTransportAndUnavailableSource(GameTestHelper h) {
        var cable=new Rig();cable.cable(0,4,0);cable.port(-1,0,true,true,Direction.WEST,"8");cable.port(5,0,false,true,Direction.EAST,"8");
        var source=new Rig();source.states.put(p(-2,0),DirectLinkGameTests.port(Direction.EAST,3));source.endpoint(-3,0,true);
        var target=new Rig();target.states.put(p(6,0),DirectLinkGameTests.port(Direction.WEST,2));target.endpoint(7,0,false);
        var a=source.network(1);var b=cable.network(2);var c=target.network(3);
        var table=new DirectLinks(new NetworkSavedData(),n->{});
        table.add(a);table.add(c);table.validateCompilation(b);table.add(b);
        table.update(a,Map.of(p(-3,0),true));table.update(b,Map.of());table.update(c,Map.of());DirectLinkGameTests.settle(table);
        h.assertTrue(c.getEffectiveSignal(p(7,0))==15,"signal crosses separately compiled cable");
        table.invalidate(a);DirectLinkGameTests.settle(table);h.assertTrue(c.getEffectiveSignal(p(7,0))==0,"unavailable source gates transport");
        table.update(a,Map.of(p(-3,0),true));DirectLinkGameTests.settle(table);h.assertTrue(c.getEffectiveSignal(p(7,0))==15,"source recovery restores current signal");
        table.remove(a);DirectLinkGameTests.settle(table);h.assertTrue(c.getEffectiveSignal(p(7,0))==0,"decompiled source cannot leave stale HIGH");h.succeed();
    }
    @GameTest(template="empty",batch="bundles")
    public static void channelIdentityIntegrityAndItemPlacement(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));
        level.setBlock(pos,port(true,false,Direction.WEST),3);var entity=(BundlePortEntity)level.getBlockEntity(pos);entity.configure("98765432101234567890");
        var element=new CompiledCircuitElement(1,pos,CircuitElementType.WIRE,CompiledBlockStateCodec.capture(level.getBlockState(pos))).captureWire(level);
        var copy=CompiledCircuitElement.load(element.save());h.assertTrue(copy.channelId().equals(entity.channel()),"snapshot preserves full channel ID");
        entity.configure("2");h.assertTrue(CompiledBlockStateMatcher.match(copy,level,level.getBlockState(pos),false)==CompiledBlockStateMatcher.Match.STATE_MISMATCH,"channel edit is a compiled integrity error");
        entity.configure(copy.channelId());h.assertTrue(CompiledBlockStateMatcher.match(copy,level,level.getBlockState(pos),false)==CompiledBlockStateMatcher.Match.MATCH,"restored ID repairs identity");
        var tag=copy.save();tag.remove("channelId");h.assertTrue(CompiledCircuitElement.load(tag).resolveState().status()==CompiledBlockStateCodec.Status.UNRESOLVED,"missing metadata does not silently become channel 0");
        level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);h.succeed();
    }
    @GameTest(template="empty",batch="bundle_world",timeoutTicks=450)
    public static void queuedCompilationRejectionRepairAndUnlink(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();
        server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"bundle-compiler"));player.getAbilities().mayBuild=true;
        var anchor=h.absolutePos(new BlockPos(5,5,4));var in=anchor.west();var out=anchor.east(5);
        var source=in.west();var input=source.west();var target=out.east();var output=target.east();var power=input.below();var duplicate=anchor.east(2).above();
        Map<BlockPos,BlockState> old=new LinkedHashMap<>();Set<Long> forced=new HashSet<>();
        var positions=new ArrayList<>(List.of(in,out,source,input,target,output,power,duplicate));for(int x=0;x<=4;x++)positions.add(anchor.east(x));
        Runnable cleanup=()->{
            CompilationJobs.stop(server);RepairJobs.stop(server);NetworkOperations.forget(player);
            for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());
            old.forEach((pos,state)->level.setBlock(pos,state,3));for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);
            server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();
        };
        try {
            for(var pos:positions)for(long c:RuntimeSignalReader.dependencyChunks(pos)){
                if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}
                level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));
            }
            for(var pos:positions)old.put(pos,level.getBlockState(pos));
            for(int x=0;x<=4;x++)level.setBlock(anchor.east(x),ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),3);
            level.setBlock(in,port(true,true,Direction.WEST),3);level.setBlock(out,port(false,true,Direction.EAST),3);
            level.setBlock(duplicate,port(true,true,Direction.UP),3);
            for(var pos:List.of(in,out,duplicate))((BundlePortEntity)level.getBlockEntity(pos)).configure("8");
            level.setBlock(source,DirectLinkGameTests.port(Direction.EAST,3),3);level.setBlock(target,DirectLinkGameTests.port(Direction.WEST,2),3);
            level.setBlock(input,ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),3);level.setBlock(output,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState(),3);
            level.setBlock(power,net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            new Runnable(){int phase,attempts;CompiledNetwork cable,a,b;
                void compile(BlockPos pos,String name){NetworkSelectionData.set(player,pos);h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.COMPILE,List.of(),0,name).code()==NetworkOperations.Code.QUEUED,"compilation queued");}
                public void run(){try{
                    if(++attempts>180){String detail="phase "+phase+" networks="+data.getNetworks().size();for(var n:data.getNetworks())detail+=" "+n.getName()+":"+n.getRuntimeStatus()+":"+n.getLogicError()+" damaged="+n.getBrokenElements();cleanup.run();h.fail("bundle integration timeout at "+detail);return;}
                    NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
                    if(CompilationJobs.isBusy(player)){h.runAfterDelay(1,()->run());return;}
                    if(phase==0){compile(anchor,"invalid-bundle");phase=1;}
                    else if(phase==1){h.assertTrue(data.getNetworks().isEmpty(),"duplicate channel rejects publication atomically");level.setBlock(duplicate,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);phase=2;}
                    else if(phase==2){compile(anchor,"transport");phase=3;}
                    else if(phase==3){cable=data.findNetworkContaining(level,anchor);h.assertTrue(cable!=null&&data.getNetworks().size()==1,"standalone cable compiles without consuming peer networks");compile(source,"source");phase=4;}
                    else if(phase==4){a=data.findNetworkContaining(level,source);h.assertTrue(a!=null,"source compiles");compile(target,"target");phase=5;}
                    else if(phase==5){b=data.findNetworkContaining(level,target);h.assertTrue(b!=null,"target compiles");
                        if(b.getEffectiveSignal(output)!=15){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(data.getNetworks().size()==3&&data.runtime().directLinkCount()==2,"three compilations with two direct links");
                        level.setBlock(in,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);NetworkIntegrityManager.checkPosition(level,in);NetworkRuntime.flush(server);
                        h.assertTrue(cable.isDamaged()&&b.getEffectiveSignal(output)==0,"broken port disables downstream outputs");
                        h.assertTrue(NetworkRepairManager.attempt(level,cable,cable.getBrokenElement(cable.getElementAt(in).getId()),player)==NetworkRepairManager.Outcome.PLACED,"repair recreates port and channel");phase=6;
                    }else if(phase==6){
                        if(b.getEffectiveSignal(output)!=15){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(((BundlePortEntity)level.getBlockEntity(in)).channel().equals("8"),"repair restores compiled ID");
                        ((BundlePortEntity)level.getBlockEntity(in)).configure("999");NetworkIntegrityManager.checkPosition(level,in);
                        h.assertTrue(cable.isDamaged(),"changed channel damages compiled cable");
                        h.assertTrue(NetworkRepairManager.attempt(level,cable,cable.getBrokenElement(cable.getElementAt(in).getId()),player)==NetworkRepairManager.Outcome.PLACED,"repair restores channel on existing port");phase=7;
                    }else if(phase==7){
                        if(b.getEffectiveSignal(output)!=15){h.runAfterDelay(1,()->run());return;}
                        data.removeNetwork(a.getId());h.assertTrue(b.getEffectiveSignal(output)==0,"source decompilation clears downstream HIGH immediately");phase=8;
                    }else{h.assertTrue(b.getEffectiveSignal(output)==0,"unlinked transport remains unavailable");cleanup.run();h.succeed();return;}
                    h.runAfterDelay(2,()->run());
                }catch(Throwable failure){cleanup.run();throw failure;}}
            }.run();
        }catch(Throwable failure){cleanup.run();throw failure;}
    }
}
