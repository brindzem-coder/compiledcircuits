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
public class ScreenGameTests {
    static final BlockPos C=new BlockPos(24,3,0), SCREEN=C.east();
    static Map<BlockPos,BlockState> states(){
        Map<BlockPos,BlockState> states=new HashMap<>();for(int x=0;x<=24;x++)states.put(new BlockPos(x,3,0),ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
        for(int i=0;i<8;i++)states.put(new BlockPos(i*3,3,-1),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        states.put(SCREEN,ModBlocks.COLOR_SCREEN.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST));return states;
    }
    static Map<BundleFace,BundleWireEntity.Port> faces(){
        Map<BundleFace,BundleWireEntity.Port> faces=new HashMap<>();for(int i=0;i<8;i++)faces.put(new BundleFace(new BlockPos(i*3,3,0),Direction.NORTH),new BundleWireEntity.Port(1,""+(100+i)));
        faces.put(new BundleFace(C,Direction.EAST),new BundleWireEntity.Port(6,"100"));return faces;
    }
    static Map<BlockPos,Boolean> values(int color){Map<BlockPos,Boolean> values=new HashMap<>();for(int i=0;i<8;i++)values.put(new BlockPos(i*3,3,-1),(color&(1<<i))!=0);return values;}
    static CompiledNetwork network(){var states=states();var faces=faces();var elements=new ArrayList<CompiledCircuitElement>();
        states.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{var role=e.getValue().getBlock() instanceof InputEndpointBlock?CircuitElementType.INPUT:e.getValue().getBlock() instanceof OutputEndpointBlock?CircuitElementType.OUTPUT:CircuitElementType.WIRE;
            var tag=new CompiledCircuitElement(elements.size()+1,e.getKey(),role,CompiledBlockStateCodec.capture(e.getValue())).save();
            if(e.getValue().getBlock() instanceof BundledWireBlock){Map<Direction,BundleWireEntity.Port> local=new EnumMap<>(Direction.class);for(var f:faces.entrySet())if(f.getKey().pos().equals(e.getKey()))local.put(f.getKey().side(),f.getValue());tag.put("bundleFaces",BundleWireEntity.encode(local));}
            elements.add(CompiledCircuitElement.load(tag));});
        var n=new CompiledNetwork(1,"screen",0,"minecraft:overworld",elements);n.restoreLogic();n.runtimeState(true,true);return n;
    }
    @GameTest(template="empty",batch="screens") public static void all256ColorsAndOptimization(GameTestHelper h){
        var states=states();var graph=LogicProgram.compileFaces(states,Map.of(),faces());
        for(int pass=0;pass<2;pass++){if(pass==1)graph.optimize(states.keySet());for(int c=0;c<256;c++){graph.evaluate(values(c));h.assertTrue(graph.screenColors().get(SCREEN)==c,"all bits survive evaluation and optimization");}}
        h.assertTrue(ScreenColors.rgb(0)==0&&ScreenColors.rgb(255)==0xffffff&&ScreenColors.rgb(7)==0xff0000&&ScreenColors.rgb(56)==0x00ff00&&ScreenColors.rgb(192)==0x0000ff,"RGB332 primary colors");h.succeed();
    }
    @GameTest(template="empty",batch="screens") public static void duplicateScreensAndValidation(GameTestHelper h){
        var states=states();var faces=faces();var second=C.south();states.put(second,ModBlocks.COLOR_SCREEN.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.SOUTH));faces.put(new BundleFace(C,Direction.SOUTH),new BundleWireEntity.Port(6,"100"));
        var graph=LogicProgram.compileFaces(states,Map.of(),faces);graph.evaluate(values(173));h.assertTrue(graph.screenColors().get(SCREEN)==173&&graph.screenColors().get(second)==173,"same base fans out");
        states.put(SCREEN,states.get(SCREEN).setValue(EndpointBlock.FACING,Direction.WEST));boolean rejected=false;try{LogicProgram.compileFaces(states,Map.of(),faces);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"wrong screen side rejected");
        h.assertTrue(ScreenColors.channel("999999999999999999999999999999",7).equals("1000000000000000000000000000006"),"arbitrary precision IDs");
        rejected=false;try{new BundleWireEntity.Port(6,"9".repeat(64));}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"range overflow rejected");h.succeed();
    }
    @GameTest(template="empty",batch="screens") public static void colorOnlyChangesAndPersistence(GameTestHelper h){
        var n=network();h.assertTrue(n.evaluateLogic(values(1))&&n.screenColor(SCREEN)==1,"first color");h.assertTrue(n.evaluateLogic(values(2))&&n.screenColor(SCREEN)==2,"color changes even while boolean output stays HIGH");h.assertTrue(!n.evaluateLogic(values(2)),"unchanged color does not notify");
        var loaded=CompiledNetwork.load(n.save());loaded.restoreLogic();loaded.runtimeState(true,true);loaded.evaluateLogic(values(255));h.assertTrue(loaded.screenColor(SCREEN)==255,"wiring survives reload");loaded.runtimeState(false,false);h.assertTrue(loaded.screenColor(SCREEN)==0,"unavailable network blanks screen");h.succeed();
    }
    @GameTest(template="empty",batch="screen_world",timeoutTicks=500)
    public static void compiledScreenReceivesAtomicColors(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"screen-test"));player.getAbilities().mayBuild=true;
        var origin=h.absolutePos(new BlockPos(2,4,2));var target=origin.offset(25,0,0);var old=new HashMap<BlockPos,BlockState>();var forced=new HashSet<Long>();
        Runnable cleanup=()->{CompilationJobs.stop(server);NetworkOperations.forget(player);for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());old.forEach((p,state)->level.setBlock(p,state,3));for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();};
        try{
            for(int x=-2;x<=27;x++)for(int z=-3;z<=2;z++){var pos=origin.offset(x,0,z);long c=RuntimeSignalReader.chunk(pos);if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}}
            for(int x=0;x<=24;x++){var pos=origin.offset(x,0,0);old.put(pos,level.getBlockState(pos));level.setBlock(pos,ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),3);}
            for(int bit=0;bit<8;bit++){var cable=origin.offset(bit*3,0,0);var input=cable.north();old.put(input,level.getBlockState(input));level.setBlock(input,SignalPorts.with(ModBlocks.SIGNAL_SWITCH.get().defaultBlockState(),Direction.SOUTH,true),3);((BundleWireEntity)level.getBlockEntity(cable)).setPort(Direction.NORTH,new BundleWireEntity.Port(1,""+(100+bit)));}
            old.put(target,level.getBlockState(target));level.setBlock(target,ModBlocks.COLOR_SCREEN.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST),3);((BundleWireEntity)level.getBlockEntity(target.west())).setPort(Direction.EAST,new BundleWireEntity.Port(6,"100"));
            new Runnable(){int phase,attempts;CompiledNetwork network;
                void color(int value){for(int i=0;i<8;i++){var input=origin.offset(i*3,0,-1);level.setBlock(input,level.getBlockState(input).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED,(value&(1<<i))!=0),3);NetworkRuntime.inputChanged(level,input);}}
                public void run(){try{
                    if(++attempts>200){cleanup.run();h.fail("screen runtime timeout phase "+phase);return;}
                    NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
                    if(CompilationJobs.isBusy(player)){h.runAfterDelay(1,()->run());return;}
                    var screen=(ColorScreenEntity)level.getBlockEntity(target);
                    if(phase==0){NetworkSelectionData.set(player,target);h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.COMPILE,List.of(),0,"screen").code()==NetworkOperations.Code.QUEUED,"screen starts discovery");phase=1;}
                    else if(phase==1){network=data.findNetworkContaining(level,target);if(network!=null&&network.getRuntimeStatus().equals("READY")){color(7);phase=2;}}
                    else if(phase==2&&screen.color()==7){color(56);phase=3;}
                    else if(phase==3&&screen.color()==56){h.assertTrue(!network.isDamaged(),"color is not a structural mutation");data.removeNetwork(network.getId());phase=4;}
                    else if(phase==4&&screen.color()==0){cleanup.run();h.succeed();return;}
                    h.runAfterDelay(1,()->run());
                }catch(Throwable error){cleanup.run();throw error;}}
            }.run();
        }catch(Throwable error){cleanup.run();throw error;}
    }

}
