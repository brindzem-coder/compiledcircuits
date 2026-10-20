package com.example.compiledcircuits.gametest;

import com.example.compiledcircuits.block.BasicWireBlock;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class WireShapeGameTests {
    // Independent copy of the pre-cache primitives and branch rules, not the production builder.
    private static final VoxelShape CENTER=Block.box(5.5,5.5,5.5,10.5,10.5,10.5);
    private static final VoxelShape N=Block.box(5.5,5.5,0,10.5,10.5,5.5);
    private static final VoxelShape S=Block.box(5.5,5.5,10.5,10.5,10.5,16);
    private static final VoxelShape E=Block.box(10.5,5.5,5.5,16,10.5,10.5);
    private static final VoxelShape W=Block.box(0,5.5,5.5,5.5,10.5,10.5);
    private static final VoxelShape U=Block.box(5.5,10.5,5.5,10.5,16,10.5);
    private static final VoxelShape D=Block.box(5.5,0,5.5,10.5,5.5,10.5);
    private static VoxelShape legacy(BlockState state) {
        VoxelShape shape=CENTER;
        if(state.getValue(BasicWireBlock.NORTH))shape=Shapes.joinUnoptimized(shape,N,BooleanOp.OR);
        if(state.getValue(BasicWireBlock.SOUTH))shape=Shapes.joinUnoptimized(shape,S,BooleanOp.OR);
        if(state.getValue(BasicWireBlock.EAST))shape=Shapes.joinUnoptimized(shape,E,BooleanOp.OR);
        if(state.getValue(BasicWireBlock.WEST))shape=Shapes.joinUnoptimized(shape,W,BooleanOp.OR);
        if(state.getValue(BasicWireBlock.UP))shape=Shapes.joinUnoptimized(shape,U,BooleanOp.OR);
        if(state.getValue(BasicWireBlock.DOWN))shape=Shapes.joinUnoptimized(shape,D,BooleanOp.OR);
        return shape;
    }
    private static void equivalent(GameTestHelper h,VoxelShape expected,VoxelShape actual,String message) {
        h.assertTrue(!Shapes.joinIsNotEmpty(expected,actual,BooleanOp.NOT_SAME),message);
    }
    private static net.minecraft.world.level.block.state.properties.BooleanProperty property(Direction d) {
        return switch(d) {
            case NORTH -> BasicWireBlock.NORTH; case SOUTH -> BasicWireBlock.SOUTH;
            case EAST -> BasicWireBlock.EAST; case WEST -> BasicWireBlock.WEST;
            case UP -> BasicWireBlock.UP; case DOWN -> BasicWireBlock.DOWN;
        };
    }
    @GameTest(template="empty",batch="stage11_shapes",timeoutTicks=100)
    public static void allWireShapes(GameTestHelper h) {
        var block=ModBlocks.BASIC_WIRE.get();var context=CollisionContext.empty();
        var player=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"shape-test"));
        var entityContext=CollisionContext.of(player);
        // Confirm XOR truth table and shape API behavior, including equal bounds/different volumes.
        for(boolean a:new boolean[]{false,true})for(boolean b:new boolean[]{false,true})
            h.assertTrue(BooleanOp.NOT_SAME.apply(a,b)==(a!=b),"NOT_SAME is symmetric difference");
        var shell=Shapes.joinUnoptimized(Block.box(0,0,0,16,16,16),CENTER,BooleanOp.ONLY_FIRST);
        h.assertTrue(Shapes.joinIsNotEmpty(shell,Shapes.block(),BooleanOp.NOT_SAME),"volume comparison detects holes, not only bounds");
        var states=block.getStateDefinition().getPossibleStates();
        h.assertTrue(states.size()==64,"exactly six boolean properties");
        var identities=Collections.newSetFromMap(new IdentityHashMap<VoxelShape,Boolean>());
        for(var state:states) {
            var shape=block.getShape(state,h.getLevel(),BlockPos.ZERO,context);
            equivalent(h,legacy(state),shape,"legacy volume for "+state);
            h.assertTrue(shape==block.getShape(state,h.getLevel(),new BlockPos(100,50,-100),entityContext),"same instance across positions and contexts");
            h.assertTrue(shape==block.getShape(state,null,null,null),"lookup independent of world/context access");
            equivalent(h,shape,state.getShape(h.getLevel(),BlockPos.ZERO),"BlockState outline route");
            h.assertTrue(block.getCollisionShape(state,h.getLevel(),BlockPos.ZERO,entityContext).isEmpty(),"wire remains non-colliding");
            h.assertTrue(state.getCollisionShape(h.getLevel(),BlockPos.ZERO).isEmpty(),"BlockState collision route remains empty");
            for(var d:Direction.values())h.assertTrue(((BasicWireBlock)block).canWireConnect(state,d),"connection contract unchanged");
            identities.add(shape);
        }
        h.assertTrue(identities.size()==64,"one distinct shared outline per distinct occupied volume");
        equivalent(h,CENTER,block.getShape(block.defaultBlockState(),null,null,null),"mask zero contains center");
        var arms=Map.of(Direction.NORTH,N,Direction.SOUTH,S,Direction.EAST,E,Direction.WEST,W,Direction.UP,U,Direction.DOWN,D);
        var full=block.defaultBlockState();var expected=CENTER;
        for(var d:Direction.values()) {
            var single=block.defaultBlockState().setValue(property(d),true);
            equivalent(h,Shapes.joinUnoptimized(CENTER,arms.get(d),BooleanOp.OR),block.getShape(single,null,null,null),"single direction "+d);
            full=full.setValue(property(d),true);expected=Shapes.joinUnoptimized(expected,arms.get(d),BooleanOp.OR);
        }
        equivalent(h,expected,block.getShape(full,null,null,null),"all six directions");
        // Same warmed sequence for both routes. Volatile sink prevents dead-code elimination.
        for(int i=0;i<4096;i++){sink=legacy(states.get(i&63));sink=block.getShape(states.get(i&63),null,null,null);}
        int calls=32768;long oldNanos=0,cachedNanos=0;
        for(int round=0;round<4;round++) {
            if((round&1)==0){oldNanos+=measure(block,states,calls,true);cachedNanos+=measure(block,states,calls,false);}
            else{cachedNanos+=measure(block,states,calls,false);oldNanos+=measure(block,states,calls,true);}
        }
        System.out.printf(Locale.ROOT,"WIRE_SHAPES states=64 sameInstance=PASS xorVolume=PASS collision=PASS callsPerRoute=%d legacyMs=%.3f cachedMs=%.3f steadyStateJoins=0%n",calls*4,oldNanos/1e6,cachedNanos/1e6);
        h.succeed();
    }
    private static volatile VoxelShape sink;
    private static long measure(Block block,List<BlockState> states,int calls,boolean old) {
        long start=System.nanoTime();
        for(int i=0;i<calls;i++)sink=old?legacy(states.get(i&63)):block.getShape(states.get(i&63),null,null,null);
        return System.nanoTime()-start;
    }
    @GameTest(template="empty",batch="stage11_neighbors",timeoutTicks=100)
    public static void neighborsChooseCachedShape(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,2));var block=ModBlocks.BASIC_WIRE.get();
        var original=new LinkedHashMap<BlockPos,BlockState>();original.put(pos,level.getBlockState(pos));
        for(var d:Direction.values())original.put(pos.relative(d),level.getBlockState(pos.relative(d)));
        try {
            for(var p:original.keySet())level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(pos,block.defaultBlockState(),3);
            for(var d:Direction.values())for(var neighbor:List.of(ModBlocks.BASIC_WIRE.get(),ModBlocks.INPUT_ENDPOINT.get(),ModBlocks.OUTPUT_ENDPOINT.get())) {
                level.setBlock(pos.relative(d),neighbor.defaultBlockState(),3);
                var connected=level.getBlockState(pos);
                h.assertTrue(connected.getValue(property(d)),"neighbor connects: "+neighbor+" "+d);
                equivalent(h,legacy(connected),connected.getShape(level,pos),"neighbor chooses connected shape");
                level.setBlock(pos.relative(d),Blocks.AIR.defaultBlockState(),3);
                var disconnected=level.getBlockState(pos);
                h.assertTrue(!disconnected.getValue(property(d)),"removed neighbor disconnects: "+d);
                equivalent(h,CENTER,disconnected.getShape(level,pos),"returns to center without cache invalidation");
            }
            for(var d:Direction.values())level.setBlock(pos.relative(d),Blocks.STONE.defaultBlockState(),3);
            equivalent(h,CENTER,level.getBlockState(pos).getShape(level,pos),"solid non-connectable neighbors do not add arms");
            System.out.println("WIRE_NEIGHBORS directions=6 neighborTypes=3 addRemove=PASS nonConnectable=PASS");
        } finally {original.forEach((p,state)->level.setBlock(p,state,3));}
        h.succeed();
    }
}
