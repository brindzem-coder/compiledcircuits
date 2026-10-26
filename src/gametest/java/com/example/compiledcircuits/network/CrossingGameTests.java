package com.example.compiledcircuits.network;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits") @PrefixGameTestTemplate(false)
public class CrossingGameTests {
    static BlockState crossing(Direction.Axis a,int kindA,Direction.Axis b,int kindB){return ModBlocks.CROSSING_WIRE.get().defaultBlockState().setValue(CrossingWireBlock.LANES[a.ordinal()],kindA).setValue(CrossingWireBlock.LANES[b.ordinal()],kindB);}
    static void branch(Map<BlockPos,BlockState> states,Map<BlockPos,String> ids,Direction.Axis axis,int kind){
        var side=Direction.get(Direction.AxisDirection.POSITIVE,axis);
        for(boolean input:new boolean[]{true,false}){
            var direction=input?side.getOpposite():side;var inner=BlockPos.ZERO.relative(direction);var end=inner.relative(direction);
            if(kind==3){states.put(inner,(input?ModBlocks.BUNDLE_INPUT:ModBlocks.BUNDLE_OUTPUT).get().defaultBlockState().setValue(EndpointBlock.FACING,direction));ids.put(inner,"42");}
            else {states.put(inner,(kind==2?ModBlocks.ID_WIRE:ModBlocks.BASIC_WIRE).get().defaultBlockState());if(kind==2)ids.put(inner,"7");}
            states.put(end,(input?ModBlocks.INPUT_ENDPOINT:ModBlocks.OUTPUT_ENDPOINT).get().defaultBlockState());
        }
    }
    @GameTest(template="empty",batch="crossing_graphs")
    public static void allAxesAndWirePairs(GameTestHelper h){
        for(var a:Direction.Axis.values())for(var b:Direction.Axis.values())if(a.ordinal()<b.ordinal())for(int ka=1;ka<=3;ka++)for(int kb=1;kb<=3;kb++){
            var state=crossing(a,ka,b,kb);var states=new HashMap<BlockPos,BlockState>();var ids=new HashMap<BlockPos,String>();states.put(BlockPos.ZERO,state);ids.put(BlockPos.ZERO,"7/7/7");branch(states,ids,a,ka);branch(states,ids,b,kb);
            var graph=LogicProgram.compile(states,ids);var da=Direction.get(Direction.AxisDirection.POSITIVE,a);var db=Direction.get(Direction.AxisDirection.POSITIVE,b);
            for(int bits=0;bits<4;bits++){
                var result=graph.evaluate(Map.of(BlockPos.ZERO.relative(da,-2),(bits&1)!=0,BlockPos.ZERO.relative(db,-2),(bits&2)!=0));
                h.assertTrue(result.getOrDefault(BlockPos.ZERO.relative(da,2),false)==((bits&1)!=0),"first crossing lane isolated: "+a+ka+" / "+b+kb);
                h.assertTrue(result.getOrDefault(BlockPos.ZERO.relative(db,2),false)==((bits&2)!=0),"second crossing lane isolated");
            }
            h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(CrossingWireBlock.laneShape(state,a),CrossingWireBlock.laneShape(state,b),net.minecraft.world.phys.shapes.BooleanOp.AND),"wire geometry never intersects");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="crossing_placement_visuals")
    public static void wirePlacedBesideExistingCrossingHasArm(GameTestHelper h){
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(3,5,3));
        var original=new LinkedHashMap<BlockPos,BlockState>();original.put(center,level.getBlockState(center));
        for(var side:Direction.values())original.put(center.relative(side),level.getBlockState(center.relative(side)));
        try {
            for(var a:Direction.Axis.values())for(var b:Direction.Axis.values())if(a!=b){
                level.setBlock(center,crossing(a,1,b,3),3);
                for(var side:Direction.values()){
                    var pos=center.relative(side);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                    var context=new net.minecraft.world.item.context.DirectionalPlaceContext(level,pos,side,
                            new net.minecraft.world.item.ItemStack(ModItems.BASIC_WIRE.get()),side.getOpposite());
                    var placed=ModBlocks.BASIC_WIRE.get().getStateForPlacement(context);
                    var arm=switch(side.getOpposite()){
                        case NORTH->BasicWireBlock.NORTH;case SOUTH->BasicWireBlock.SOUTH;
                        case EAST->BasicWireBlock.EAST;case WEST->BasicWireBlock.WEST;
                        case UP->BasicWireBlock.UP;case DOWN->BasicWireBlock.DOWN;
                    };
                    h.assertTrue(placed.getValue(arm)==(side.getAxis()==a),"placement arm matches scalar crossing lane on "+side);
                    level.setBlock(pos,placed,3);
                    h.assertTrue(level.getBlockState(pos).getValue(arm)==(side.getAxis()==a),"placed arm survives neighbor updates on "+side);
                    level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                }
            }
        } finally {original.forEach((pos,state)->level.setBlock(pos,state,3));}
        h.succeed();
    }
    @GameTest(template="empty",batch="crossing_world",timeoutTicks=800)
    public static void compileSaveCopyRepairDeleteIndependently(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"crossings"));player.getAbilities().mayBuild=true;
        var p=h.absolutePos(new BlockPos(3,5,3));var copy=p.offset(0,0,8);
        Map<BlockPos,BlockState> old=new LinkedHashMap<>();Set<Long> forced=new HashSet<>();
        var positions=new ArrayList<BlockPos>();for(var center:List.of(p,copy)){positions.add(center);for(var side:List.of(Direction.WEST,Direction.EAST,Direction.NORTH,Direction.SOUTH))positions.add(center.relative(side));}
        Runnable cleanup=()->{DeletionJobs.stop(server);CompilationJobs.stop(server);RepairJobs.stop(server);NetworkOperations.forget(player);for(var n:new ArrayList<>(data.getNetworks()))data.removeNetwork(n.getId());old.forEach((pos,state)->level.setBlock(pos,state,3));for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();};
        try{
            for(var pos:positions)for(long c:RuntimeSignalReader.dependencyChunks(pos)){if(!level.getForcedChunks().contains(c)){level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),true);forced.add(c);}level.getChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c));}
            for(var pos:positions){old.put(pos,level.getBlockState(pos));level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
            level.setBlock(p.west(),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),3);level.setBlock(p.east(),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState(),3);level.setBlock(p,ModBlocks.BASIC_WIRE.get().defaultBlockState(),3);
            var secondWire=new net.minecraft.world.item.ItemStack(ModItems.ID_WIRE.get(),2);com.example.compiledcircuits.item.IdWireItem.configure(secondWire,"22",0x22ddaa);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,secondWire);
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(p).add(0,0,-.2),Direction.NORTH,p,false);
            var placed=CrossingPlacement.place(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
            h.assertTrue(placed.consumesAction(),"second wire placement succeeds");h.assertTrue(level.getBlockState(p).getBlock() instanceof CrossingWireBlock,"crossing created");
            h.assertTrue(((CrossingWireEntity)level.getBlockEntity(p)).id(Direction.Axis.Z).equals("22"),"second lane keeps configured ID");
            var thirdHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(p).add(0,.2,0),Direction.UP,p,false);
            h.assertTrue(CrossingPlacement.place(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,thirdHit))==net.minecraft.world.InteractionResult.FAIL,"third wire rejected");
            h.assertTrue(CrossingWireBlock.mask(level.getBlockState(p))==5&&secondWire.getCount()==1,"rejected third wire changes neither block nor inventory");
            var portContext=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(ModItems.NETWORK_INPUT.get().useOn(portContext)==net.minecraft.world.InteractionResult.FAIL,"network ports rejected on two wires");
            h.assertTrue(BundlePortPlacement.apply(portContext,1,"42")==net.minecraft.world.InteractionResult.FAIL,"bundle ports rejected on two wires");
            level.setBlock(p.north(),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(),3);level.setBlock(p.south(),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState(),3);
            new Runnable(){int phase,ticks;CompiledNetwork x,z;
                void select(Direction side){NetworkSelectionData.set(player,p,net.minecraft.world.phys.Vec3.atCenterOf(p).add(side.getStepX()*.49,side.getStepY()*.49,side.getStepZ()*.49));}
                void compile(Direction side){select(side);NetworkOperations.forget(player);var r=NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.COMPILE,List.of(),0,null);h.assertTrue(r.code()==NetworkOperations.Code.QUEUED,"lane compile queued: "+r);}
                public void run(){try{
                    if(ticks++==0){h.runAfterDelay(5,()->run());return;}
                    if(ticks>500){cleanup.run();h.fail("crossing phase "+phase+" timed out");return;}
                    if(CompilationJobs.isBusy(player)||DeletionJobs.isBusy(server)){h.runAfterDelay(1,()->run());return;}
                    if(phase==0){compile(Direction.WEST);phase++;}
                    else if(phase==1){x=data.findNetworkContaining(level,p.west());h.assertTrue(x!=null&&x.getElements().size()==3,"only X line compiled: "+(x==null?"missing":x.getElements().size())+" mask="+NetworkSelectionData.mask(player));h.assertTrue(data.findNetworkContaining(level,p.north())==null,"Z remains uncompiled");compile(Direction.NORTH);phase++;}
                    else if(phase==2){
                        z=data.findNetworkContaining(level,p.north());h.assertTrue(z!=null&&z!=x&&z.getElements().size()==3,"Z compiles separately");h.assertTrue(data.getMembershipOwners(level.dimension().location().toString(),p).size()==2,"two independent owners");
                        var loaded=NetworkSavedData.load(data.save(new CompoundTag()));h.assertTrue(loaded.getNetworks().size()==2&&loaded.getInvalidMembershipRecordCount()==0,"save/load preserves both lanes");
                        select(Direction.WEST);h.assertTrue(NetworkSelectionData.network(player)==x,"hit selects X owner");select(Direction.NORTH);h.assertTrue(NetworkSelectionData.network(player)==z,"hit selects Z owner");
                        var blueprint=CircuitBlueprint.capture(x,level).append(CircuitBlueprint.capture(z,level));
                        for(var transformed:blueprint.elements(BlockPos.ZERO,1,true,true))if(transformed.getBlockId().equals("compiledcircuits:crossing_wire")){
                            var state=transformed.resolveState().state().orElseThrow();
                            if(CrossingWireBlock.kind(state,Direction.Axis.X)==2)h.assertTrue(transformed.crossingLanes().getCompound("x").getString("id").equals("22"),"rotated mirrored ID follows its lane");
                        }
                        var copies=BlueprintService.pasteAll(player,blueprint,copy.offset(-1,0,0),0,false,false);
                        h.assertTrue(copies.size()==2&&Integer.bitCount(CrossingWireBlock.mask(level.getBlockState(copy)))==2,"group paste merges independent lanes");
                        h.assertTrue(((CrossingWireEntity)level.getBlockEntity(copy)).id(Direction.Axis.Z).equals("22"),"group paste preserves ID metadata");
                        h.assertTrue(Crossings.remove(level,p,1),"remove one lane");NetworkIntegrityManager.checkPosition(level,p);
                        h.assertTrue(x.isDamaged()&&!z.isDamaged(),"only removed lane is damaged");
                        NetworkOperations.forget(player);h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.REPAIR,List.of(x.getId()),0,"").success(),"repair queued");phase++;
                    }else if(phase==3){
                        if(x.isDamaged()){h.runAfterDelay(1,()->run());return;}
                        h.assertTrue(CrossingWireBlock.mask(level.getBlockState(p))==5&&!z.isDamaged(),"repair preserves Z");
                        NetworkOperations.forget(player);h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.DELETE,List.of(x.getId()),0,"").success(),"delete X queued");phase++;
                    }else{
                        h.assertTrue(data.getNetwork(x.getId())==null&&data.getNetwork(z.getId())==z,"delete removes only X compilation");
                        h.assertTrue(CrossingWireBlock.mask(level.getBlockState(p))==4,"Z physical wire survives deletion");
                        h.assertTrue(level.isEmptyBlock(p.west())&&level.isEmptyBlock(p.east())&&!level.isEmptyBlock(p.north())&&!level.isEmptyBlock(p.south()),"other line endpoints remain");
                        h.assertTrue(((CrossingWireEntity)level.getBlockEntity(p)).id(Direction.Axis.Z).equals("22"),"deletion preserves other lane ID");
                        h.assertTrue(ModItems.NETWORK_INPUT.get().useOn(portContext).consumesAction(),"port allowed once only one wire remains");
                        h.assertTrue(level.getBlockState(p).getBlock() instanceof IdWireBlock&&((IdWireEntity)level.getBlockEntity(p)).wireId().equals("22"),"single-wire port conversion preserves ID");
                        cleanup.run();h.succeed();return;
                    }
                    h.runAfterDelay(2,()->run());
                }catch(Throwable failure){cleanup.run();throw failure;}}
            }.run();
        }catch(Throwable failure){cleanup.run();throw failure;}
    }
}
