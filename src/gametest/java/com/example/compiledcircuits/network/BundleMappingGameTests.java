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
public class BundleMappingGameTests {
    static final BlockPos A=new BlockPos(0,3,0), B=A.east();
    static BundleFace f(BlockPos p,Direction d){return new BundleFace(p,d);}
    static void port(Map<BundleFace,BundleWireEntity.Port> faces,BlockPos p,Direction d,int kind,String id){faces.put(f(p,d),new BundleWireEntity.Port(kind,id));}
    static Map<BlockPos,BlockState> cables(){return Map.of(A,ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),B,ModBlocks.BUNDLED_WIRE.get().defaultBlockState());}
    static void rejects(GameTestHelper h,Runnable r,String message){boolean rejected=false;try{r.run();}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,message);}
    @GameTest(template="empty",batch="bundle_mapping")
    public static void parserRangesFanoutReductionAndLimits(GameTestHelper h){
        var m=ChannelMapping.parse(" 01, 2 = 3, 3 - 6 = 4 - 7, 7-9 ");
        h.assertTrue(m.text().equals("1,2=3,3-6=4-7,7-9")&&m.edges().size()==9,"canonical mapping preserves all nine routes");
        h.assertTrue(m.edges().contains(new ChannelMapping.Edge("4","5"))&&m.edges().contains(new ChannelMapping.Edge("7","7")),"ranges zip and collisions survive");
        h.assertTrue(ChannelMapping.parse("0-7=1").edges().size()==8&&ChannelMapping.parse("1=0-7").targets().size()==8,"reduce and fanout");
        h.assertTrue(ChannelMapping.parse("0-2").equivalent(ChannelMapping.parse("2,0,1")),"equivalence uses routes");
        for(var bad:List.of("","1,","-1","3-1","1=2=3","0-2=4-5","0-99999999999999999999","1 2"))rejects(h,()->ChannelMapping.parse(bad),"reject malformed or unbounded mapping "+bad);
        h.succeed();
    }
    @GameTest(template="empty",batch="bundle_mapping")
    public static void internalMappingOrAndFanout(GameTestHelper h){
        var faces=new HashMap<BundleFace,BundleWireEntity.Port>();
        port(faces,A,Direction.WEST,3,"0");port(faces,A,Direction.UP,3,"1");port(faces,A,Direction.DOWN,3,"2");
        port(faces,A,Direction.EAST,7,"0-1=7,2=8-9");
        port(faces,B,Direction.EAST,4,"7");port(faces,B,Direction.UP,4,"8");port(faces,B,Direction.DOWN,4,"9");
        for(boolean optimize:List.of(false,true)){
            var graph=LogicProgram.compileFaces(cables(),Map.of(),faces);if(optimize)graph.optimize(cables().keySet());
            for(int bits=0;bits<8;bits++){
                graph.evaluate(Map.of(),Map.of(f(A,Direction.WEST),(bits&1)!=0,f(A,Direction.UP),(bits&2)!=0,f(A,Direction.DOWN),(bits&4)!=0));
                h.assertTrue(graph.faceOutput(f(B,Direction.EAST))==((bits&3)!=0),"many-to-one OR");
                h.assertTrue(graph.faceOutput(f(B,Direction.UP))==((bits&4)!=0)&&graph.faceOutput(f(B,Direction.DOWN))==((bits&4)!=0),"one-to-many fanout");
            }
        }h.succeed();
    }
    @GameTest(template="empty",batch="bundle_mapping")
    public static void missingChannelsAndDestinationReject(GameTestHelper h){
        var faces=new HashMap<BundleFace,BundleWireEntity.Port>();port(faces,A,Direction.WEST,3,"0");port(faces,A,Direction.EAST,7,"0-1");
        rejects(h,()->LogicProgram.compileFaces(cables(),Map.of(),faces),"nonexistent selected source is rejected");
        port(faces,A,Direction.EAST,7,"0");
        rejects(h,()->LogicProgram.compileFaces(Map.of(A,cables().get(A)),Map.of(),faces),"internal mapping needs destination cable");
        port(faces,B,Direction.EAST,4,"1");
        rejects(h,()->LogicProgram.compileFaces(cables(),Map.of(),faces),"unselected channel is not created");h.succeed();
    }
    @GameTest(template="empty",batch="bundle_mapping")
    public static void multipleSourcesMergeBeforeMappingAndOptimization(GameTestHelper h){
        var faces=new HashMap<BundleFace,BundleWireEntity.Port>();
        port(faces,A,Direction.WEST,3,"0");port(faces,A,Direction.UP,3,"0");port(faces,A,Direction.DOWN,3,"1");
        port(faces,A,Direction.EAST,7,"0=8,1=9");
        port(faces,B,Direction.UP,3,"8");port(faces,B,Direction.EAST,4,"8");port(faces,B,Direction.DOWN,4,"9");
        for(boolean optimize:List.of(false,true)){
            var graph=LogicProgram.compileFaces(cables(),Map.of(),faces);if(optimize)graph.optimize(cables().keySet());
            for(int bits=0;bits<16;bits++){
                graph.evaluate(Map.of(),Map.of(f(A,Direction.WEST),(bits&1)!=0,f(A,Direction.UP),(bits&2)!=0,f(B,Direction.UP),(bits&4)!=0,f(A,Direction.DOWN),(bits&8)!=0));
                h.assertTrue(graph.faceOutput(f(B,Direction.EAST))==((bits&7)!=0),"original and mapped sources merge through OR");
                h.assertTrue(graph.faceOutput(f(B,Direction.DOWN))==((bits&8)!=0),"other channel stays independent");
            }
        }h.succeed();
    }
    static CompiledNetwork network(int id,boolean source,String rules){
        var p=source?A:B;var side=source?Direction.EAST:Direction.WEST;var endpoint=p.relative(side.getOpposite());
        var local=new EnumMap<Direction,BundleWireEntity.Port>(Direction.class);
        local.put(side,new BundleWireEntity.Port(source?8:9,rules));local.put(side.getOpposite(),new BundleWireEntity.Port(source?1:2,source?"0":"9"));
        var tag=new CompiledCircuitElement(1,p,CircuitElementType.WIRE,CompiledBlockStateCodec.capture(ModBlocks.BUNDLED_WIRE.get().defaultBlockState())).save();tag.put("bundleFaces",BundleWireEntity.encode(local));
        var terminal=new CompiledCircuitElement(2,endpoint,source?CircuitElementType.INPUT:CircuitElementType.OUTPUT,CompiledBlockStateCodec.capture((source?ModBlocks.INPUT_ENDPOINT:ModBlocks.OUTPUT_ENDPOINT).get().defaultBlockState()));
        var n=new CompiledNetwork(id,"mapping",0,"minecraft:overworld",List.of(CompiledCircuitElement.load(tag),terminal));n.restoreLogic();n.runtimeState(true,true);return n;
    }
    @GameTest(template="empty",batch="bundle_mapping")
    public static void externalLinksReloadOptimizationAndDisconnect(GameTestHelper h){
        for(boolean optimized:List.of(false,true)){
            var a=network(1,true,"0=9");var b=network(2,false,"0=9");
            if(optimized){a.enableOptimization();b.enableOptimization();}
            a=CompiledNetwork.load(a.save());b=CompiledNetwork.load(b.save());a.restoreLogic();b.restoreLogic();a.runtimeState(true,true);b.runtimeState(true,true);
            var table=new DirectLinks(new NetworkSavedData(),n->{});table.add(a);table.validateCompilation(b);table.add(b);table.update(b,Map.of());
            for(boolean high:List.of(true,false,true)){table.update(a,Map.of(A.west(),high));DirectLinkGameTests.settle(table);h.assertTrue(b.getEffectiveSignal(B.east())==(high?15:0),"external mapped channel survives reload and graph optimization");}
            table.remove(a);DirectLinkGameTests.settle(table);h.assertTrue(b.getEffectiveSignal(B.east())==0,"disconnect clears mapped output");
        }h.succeed();
    }
    static CompiledNetwork multiNetwork(int id,boolean source){
        var p=source?A:B;var side=source?Direction.EAST:Direction.WEST;
        var local=new EnumMap<Direction,BundleWireEntity.Port>(Direction.class);
        local.put(side,new BundleWireEntity.Port(source?8:9,"0=9,1=8"));
        local.put(side.getOpposite(),new BundleWireEntity.Port(source?1:2,source?"0":"9"));
        local.put(Direction.UP,new BundleWireEntity.Port(source?1:2,source?"1":"8"));
        var tag=new CompiledCircuitElement(1,p,CircuitElementType.WIRE,CompiledBlockStateCodec.capture(ModBlocks.BUNDLED_WIRE.get().defaultBlockState())).save();tag.put("bundleFaces",BundleWireEntity.encode(local));
        var elements=new ArrayList<CompiledCircuitElement>();elements.add(CompiledCircuitElement.load(tag));
        for(var d:List.of(side.getOpposite(),Direction.UP))elements.add(new CompiledCircuitElement(elements.size()+1,p.relative(d),source?CircuitElementType.INPUT:CircuitElementType.OUTPUT,CompiledBlockStateCodec.capture((source?ModBlocks.INPUT_ENDPOINT:ModBlocks.OUTPUT_ENDPOINT).get().defaultBlockState())));
        var n=new CompiledNetwork(id,"multi-mapping",0,"minecraft:overworld",elements);n.restoreLogic();n.runtimeState(true,true);return n;
    }
    @GameTest(template="empty",batch="bundle_mapping")
    public static void independentChannelsAcrossOneBoundary(GameTestHelper h){
        var a=multiNetwork(1,true);var b=multiNetwork(2,false);a.enableOptimization();b.enableOptimization();
        var table=new DirectLinks(new NetworkSavedData(),n->{});table.add(a);table.validateCompilation(b);table.add(b);table.update(b,Map.of());
        for(int bits=0;bits<4;bits++){
            table.update(a,Map.of(A.west(),(bits&1)!=0,A.above(),(bits&2)!=0));DirectLinkGameTests.settle(table);
            h.assertTrue(b.getEffectiveSignal(B.east())==((bits&1)!=0?15:0)&&b.getEffectiveSignal(B.above())==((bits&2)!=0?15:0),"two channels on a single boundary do not collapse into one bit");
        }h.succeed();
    }
    @GameTest(template="empty",batch="bundle_mapping")
    public static void mappingChainsAndFeedback(GameTestHelper h){
        var c=B.east();var states=new HashMap<BlockPos,BlockState>(cables());states.put(c,ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
        var faces=new HashMap<BundleFace,BundleWireEntity.Port>();port(faces,A,Direction.WEST,3,"0");port(faces,A,Direction.EAST,7,"0=7");port(faces,B,Direction.EAST,7,"7=9");port(faces,c,Direction.EAST,4,"9");
        var graph=LogicProgram.compileFaces(states,Map.of(),faces);
        for(boolean high:List.of(true,false)){graph.evaluate(Map.of(),Map.of(f(A,Direction.WEST),high));h.assertTrue(graph.faceOutput(f(c,Direction.EAST))==high,"mapped channels feed later mappings");}
        var loop=new HashMap<BundleFace,BundleWireEntity.Port>();port(loop,A,Direction.EAST,7,"0=1");port(loop,B,Direction.WEST,7,"1=0");
        rejects(h,()->LogicProgram.compileFaces(cables(),Map.of(),loop),"facing internal mappings are not bare destinations");h.succeed();
    }
    @GameTest(template="empty",batch="bundle_mapping")
    public static void externalMismatchRejected(GameTestHelper h){
        var table=new DirectLinks(new NetworkSavedData(),n->{});table.add(network(1,true,"0=9"));
        rejects(h,()->table.validateCompilation(network(2,false,"1=9")),"mapping mismatch is rejected");
        var n=network(3,true,"0=9");rejects(h,()->new NetworkTruthTable.Builder(LogicProgram.compileFaces(n.tableStates(),n.wireIds(),n.bundleFaces())),"scalar truth tables cannot erase multichannel boundaries");h.succeed();
    }
    @GameTest(template="empty",batch="bundle_mapping")
    public static void receiverCopiesRulesAndSeparatesDiscovery(GameTestHelper h){
        var level=h.getLevel();var a=h.absolutePos(new BlockPos(2,3,2));var b=a.east();
        level.setBlock(a,ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),3);level.setBlock(b,ModBlocks.BUNDLED_WIRE.get().defaultBlockState(),3);
        var source=(BundleWireEntity)level.getBlockEntity(a);source.setPort(Direction.EAST,new BundleWireEntity.Port(7,"0=9"));
        h.assertTrue(BundleConnections.connects(level,a,level.getBlockState(a),level.getBlockState(b),Direction.EAST),"internal mapping is traversable for discovery");
        source.setPort(Direction.EAST,new BundleWireEntity.Port(8,"0=9"));
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"mapping-tool"));player.getAbilities().mayBuild=true;
        var stack=new net.minecraft.world.item.ItemStack(ModItems.BUNDLE_MAPPING_INPUT.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(b),Direction.WEST,b,false);
        var context=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertTrue(stack.useOn(context).consumesAction(),"receiver placement");
        h.assertTrue(((BundleWireEntity)level.getBlockEntity(b)).port(Direction.WEST).id().equals("0=9"),"receiver imports source rules");
        h.assertTrue(!BundleConnections.connects(level,a,level.getBlockState(a),level.getBlockState(b),Direction.EAST),"external mapping stops discovery");
        stack.useOn(context);h.assertTrue(((BundleWireEntity)level.getBlockEntity(b)).port(Direction.WEST)==null,"repeat placement removes receiver");
        level.removeBlock(a,false);level.removeBlock(b,false);h.succeed();
    }
}
