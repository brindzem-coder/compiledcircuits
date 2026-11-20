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
public class KeyboardGameTests {
    private static final BlockPos C=new BlockPos(0,3,0);
    private static Map<BlockPos,BlockState> states(){
        var states=new HashMap<BlockPos,BlockState>();
        states.put(C,ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
        states.put(C.west(),ModBlocks.KEYBOARD.get().defaultBlockState());
        states.put(C.east(),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        states.put(C.south(),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        return states;
    }
    private static Map<BundleFace,BundleWireEntity.Port> faces(){
        return new HashMap<>(Map.of(new BundleFace(C,Direction.EAST),new BundleWireEntity.Port(5,"87"),
                new BundleFace(C,Direction.SOUTH),new BundleWireEntity.Port(5,"75+341")));
    }
    private static void rejects(GameTestHelper h,Runnable code){boolean failed=false;try{code.run();}catch(IllegalArgumentException expected){failed=true;}h.assertTrue(failed,"invalid configuration rejected");}
    @GameTest(template="empty",batch="keyboard")
    public static void independentInputsAndOptimization(GameTestHelper h){
        var states=states();var faces=faces();var graph=LogicProgram.compileFaces(states,Map.of(),faces);
        for(int pass=0;pass<3;pass++){
            if(pass==1)graph.optimize(states.keySet());
            if(pass==2){graph=LogicProgram.compileFaces(states,Map.of(),faces);var builder=new NetworkTruthTable.Builder(graph);while(!builder.step()){}graph.installTable(NetworkTruthTable.load(builder.finish().save()),states.keySet());}
            for(int bits=0;bits<4;bits++){
                var result=graph.evaluate(Map.of(C.west(),false),Map.of(new BundleFace(C,Direction.EAST),(bits&1)!=0,new BundleFace(C,Direction.SOUTH),(bits&2)!=0));
                h.assertTrue(result.get(C.east())==((bits&1)!=0)&&result.get(C.south())==((bits&2)!=0),"keyboard face inputs remain independent, including tables and optimization");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="keyboard")
    public static void requiresOneKeyboardAndPreservesNumericChannels(GameTestHelper h){
        var states=states();var faces=faces();states.remove(C.west());rejects(h,()->LogicProgram.compileFaces(states,Map.of(),faces));
        states.put(C.west(),ModBlocks.KEYBOARD.get().defaultBlockState());states.put(C.north(),ModBlocks.KEYBOARD.get().defaultBlockState());
        rejects(h,()->LogicProgram.compileFaces(states,Map.of(),faces));states.remove(C.north());
        states.put(C.above(),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());states.put(C.below(),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        faces.put(new BundleFace(C,Direction.UP),new BundleWireEntity.Port(1,"87"));faces.put(new BundleFace(C,Direction.DOWN),new BundleWireEntity.Port(2,"87"));
        var graph=LogicProgram.compileFaces(states,Map.of(),faces);
        var result=graph.evaluate(Map.of(C.above(),true),Map.of());
        h.assertTrue(result.get(C.below())&&!result.get(C.east())&&!result.get(C.south()),"numeric channel 87 does not collide with keyboard W code 87");h.succeed();
    }
    @GameTest(template="empty",batch="keyboard")
    public static void bindingsPersistenceAndReservedInputs(GameTestHelper h){
        var combination=KeyboardBinding.parse("1000+341+87");
        h.assertTrue(combination.encode().equals("87+341+1000")&&combination.matches(Set.of(341,87,1000)),"canonical keyboard and mouse chord");
        h.assertTrue(!combination.matches(Set.of(87,1000)),"missing modifier releases output");
        for(String bad:List.of("256","290+341","290+345","87+87","-1","999999",""))rejects(h,()->KeyboardBinding.parse(bad));
        Map<Direction,BundleWireEntity.Port> ports=Map.of(Direction.NORTH,new BundleWireEntity.Port(5,combination.encode()),Direction.SOUTH,new BundleWireEntity.Port(2,"123"));
        h.assertTrue(BundleWireEntity.decode(BundleWireEntity.encode(ports)).equals(ports),"keyboard and normal ports round-trip together");
        var tag=new CompiledCircuitElement(1,C,CircuitElementType.WIRE,CompiledBlockStateCodec.capture(ModBlocks.BUNDLED_WIRE.get().defaultBlockState())).save();tag.put("bundleFaces",BundleWireEntity.encode(ports));
        var loaded=CompiledCircuitElement.load(tag);h.assertTrue(loaded.resolveState().status()==CompiledBlockStateCodec.Status.EXACT&&loaded.bundleFaces().equals(tag.getCompound("bundleFaces")),"compiled snapshot preserves keyboard binding");h.succeed();
    }
    @GameTest(template="empty",batch="keyboard")
    public static void crossingLanesDoNotLeakKeyboardControl(GameTestHelper h){
        var states=new HashMap<BlockPos,BlockState>();var faces=new HashMap<BundleFace,BundleWireEntity.Port>();
        states.put(C,ModBlocks.CROSSING_WIRE.get().defaultBlockState().setValue(CrossingWireBlock.LANES[Direction.Axis.X.ordinal()],3).setValue(CrossingWireBlock.LANES[Direction.Axis.Z.ordinal()],3));
        states.put(C.west(),ModBlocks.KEYBOARD.get().defaultBlockState());states.put(C.north(),ModBlocks.KEYBOARD.get().defaultBlockState());
        states.put(C.east(),ModBlocks.BUNDLED_WIRE.get().defaultBlockState());states.put(C.south(),ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
        var east=new BundleFace(C.east(),Direction.EAST);var south=new BundleFace(C.south(),Direction.SOUTH);
        faces.put(east,new BundleWireEntity.Port(5,"87"));faces.put(south,new BundleWireEntity.Port(5,"87"));
        var routes=KeyboardTopology.resolve(states,faces,Map.of());
        h.assertTrue(routes.get(east).equals(C.west())&&routes.get(south).equals(C.north()),"crossing axes have separate keyboard owners");h.succeed();
    }
    @GameTest(template="empty",batch="keyboard")
    public static void packetRoundTripAndBoundedDecode(GameTestHelper h){
        var packet=new com.example.compiledcircuits.networking.KeyboardInputC2SPacket(UUID.randomUUID(),42,false,Set.of(87,341,1000));
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{com.example.compiledcircuits.networking.KeyboardInputC2SPacket.encode(packet,buffer);h.assertTrue(packet.equals(com.example.compiledcircuits.networking.KeyboardInputC2SPacket.decode(buffer)),"packet preserves token sequence and chord");}
        finally{buffer.release();}
        var bad=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{bad.writeUUID(UUID.randomUUID());bad.writeVarInt(0);bad.writeBoolean(false);bad.writeVarInt(1000000);rejects(h,()->com.example.compiledcircuits.networking.KeyboardInputC2SPacket.decode(bad));}finally{bad.release();}h.succeed();
    }
    private static UUID token(net.minecraft.server.level.ServerPlayer player) {
        try {
            var field=KeyboardSessions.class.getDeclaredField("sessions");field.setAccessible(true);
            var session=((Map<?,?>)field.get(null)).get(player.getUUID());if(session==null)return null;
            var token=session.getClass().getDeclaredField("token");token.setAccessible(true);return (UUID)token.get(session);
        } catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    @GameTest(template="empty",batch="keyboard_world",timeoutTicks=800)
    public static void sessionDrivesCompiledNetworkAndReleases(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();
        server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"keyboard-test"));player.getAbilities().mayBuild=true;
        var keyboard=h.absolutePos(new BlockPos(2,4,2));var cable=keyboard.east();var output=cable.east();
        var positions=List.of(keyboard,cable,output);Map<BlockPos,BlockState> old=new HashMap<>();Set<Long> forced=new HashSet<>();
        Runnable cleanup=()->{KeyboardSessions.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));CompilationJobs.stop(server);NetworkOperations.forget(player);
            for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());old.forEach((p,s)->level.setBlock(p,s,3));
            for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);
            server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();};
        try {
            for(var pos:positions){old.put(pos,level.getBlockState(pos));for(long c:RuntimeSignalReader.dependencyChunks(pos))if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}}
            level.setBlock(keyboard,ModBlocks.KEYBOARD.get().defaultBlockState(),3);level.setBlock(cable,ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),3);
            ((BundleWireEntity)level.getBlockEntity(cable)).setPort(Direction.EAST,new BundleWireEntity.Port(5,"87"));
            level.setBlock(output,ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState(),3);player.setPos(keyboard.getX()+.5,keyboard.getY()+1,keyboard.getZ()+.5);
            new Runnable(){int phase,attempts;CompiledNetwork network;
                public void run(){try{
                    if(++attempts>200){cleanup.run();h.fail("keyboard integration timeout phase "+phase);return;}
                    NetworkIntegrityManager.processPending(server);NetworkRuntime.tick(server);
                    if(CompilationJobs.isBusy(player)){h.runAfterDelay(1,()->run());return;}
                    if(phase==0){NetworkSelectionData.set(player,keyboard);h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.COMPILE,List.of(),0,"keyboard").code()==NetworkOperations.Code.QUEUED,"keyboard starts compilation");phase=1;}
                    else if(phase==1){
                        network=data.findNetworkContaining(level,keyboard);if(network==null||!network.getRuntimeStatus().equals("READY")){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(network.getElements().size()==3,"scanner includes keyboard, cable and output");KeyboardSessions.open(player,keyboard);var session=token(player);h.assertTrue(session!=null,"server opens session");
                        KeyboardSessions.input(player,new com.example.compiledcircuits.networking.KeyboardInputC2SPacket(UUID.randomUUID(),0,false,Set.of(87)));
                        h.assertTrue(network.getEffectiveSignal(output)==0,"forged token ignored");
                        KeyboardSessions.input(player,new com.example.compiledcircuits.networking.KeyboardInputC2SPacket(session,1,false,Set.of(87)));phase=2;
                    } else if(phase==2){
                        if(network.getEffectiveSignal(output)!=15){h.runAfterDelay(1,()->run());return;}
                        var session=token(player);KeyboardSessions.input(player,new com.example.compiledcircuits.networking.KeyboardInputC2SPacket(session,0,false,Set.of()));
                        h.assertTrue(network.getEffectiveSignal(output)==15,"stale packet ignored");
                        KeyboardSessions.input(player,new com.example.compiledcircuits.networking.KeyboardInputC2SPacket(session,2,true,Set.of()));phase=3;
                    } else if(phase==3){
                        if(network.getEffectiveSignal(output)!=0){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(token(player)==null&&!network.isDamaged(),"exit releases signal without damage");
                        KeyboardSessions.open(player,keyboard);var session=token(player);h.assertTrue(session!=null,"keyboard can be reentered");
                        KeyboardSessions.input(player,new com.example.compiledcircuits.networking.KeyboardInputC2SPacket(session,0,false,Set.of(87)));phase=4;
                    } else if(phase==4){
                        if(network.getEffectiveSignal(output)!=15){h.runAfterDelay(1,()->run());return;}
                        KeyboardSessions.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));phase=5;
                    } else if(phase==5){
                        if(network.getEffectiveSignal(output)!=0){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(token(player)==null,"disconnect clears session");cleanup.run();h.succeed();return;
                    }
                    h.runAfterDelay(1,()->run());
                }catch(Throwable failure){cleanup.run();throw failure;}}
            }.run();
        }catch(Throwable failure){cleanup.run();throw failure;}
    }

    @GameTest(template="empty",batch="keyboard_camera")
    public static void cameraSetupSavesLaterPoseAndSupportsCancel(GameTestHelper h) throws Exception {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,2));
        level.setBlock(pos,ModBlocks.KEYBOARD.get().defaultBlockState(),3);
        var entity=(KeyboardEntity)level.getBlockEntity(pos);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"camera-test"));player.getAbilities().mayBuild=true;
        player.setPos(pos.getX()+1,pos.getY()+1,pos.getZ());
        KeyboardCameraSessions.open(player,pos);
        var field=KeyboardCameraSessions.class.getDeclaredField("sessions");field.setAccessible(true);
        var sessions=(Map<?,?>)field.get(null);var session=sessions.get(player.getUUID());
        h.assertTrue(session!=null&&entity.camera()==null,"opening does not save the click pose");
        var method=session.getClass().getDeclaredMethod("token");method.setAccessible(true);var token=(UUID)method.invoke(session);
        player.setPos(pos.getX()+4,pos.getY()+1,pos.getZ()+3);player.setYRot(123);player.setXRot(-27);
        KeyboardCameraSessions.finish(player,UUID.randomUUID(),1);h.assertTrue(entity.camera()==null,"wrong token rejected");
        KeyboardCameraSessions.finish(player,token,1);
        h.assertTrue(entity.camera().equals(player.position())&&entity.yaw()==123&&entity.pitch()==-27,"Enter saves the later position and direction");
        var saved=entity.cameraData();KeyboardCameraSessions.open(player,pos);session=sessions.get(player.getUUID());token=(UUID)method.invoke(session);
        player.setYRot(50);KeyboardCameraSessions.finish(player,token,0);h.assertTrue(entity.cameraData().equals(saved),"cancel preserves previous pose");
        KeyboardCameraSessions.open(player,pos);session=sessions.get(player.getUUID());token=(UUID)method.invoke(session);
        KeyboardCameraSessions.finish(player,token,2);h.assertTrue(entity.camera()==null,"clear removes camera");
        level.removeBlock(pos,false);h.succeed();
    }

}

