package com.example.compiledcircuits.block;

import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Independent straight lanes: 0 absent, 1 basic, 2 ID, 3 bundled. */
public final class CrossingWireBlock extends Block implements EntityBlock, IWireConnectable {
    public static final IntegerProperty[] LANES={IntegerProperty.create("x",0,3),IntegerProperty.create("y",0,3),IntegerProperty.create("z",0,3)};
    public CrossingWireBlock(Properties properties){super(properties);var s=defaultBlockState();for(var p:LANES)s=s.setValue(p,0);registerDefaultState(s);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(LANES);}
    public static int kind(BlockState s,Direction.Axis a){return s.getBlock() instanceof CrossingWireBlock?s.getValue(LANES[a.ordinal()]):0;}
    public static int mask(BlockState s){int m=0;for(var a:Direction.Axis.values())if(kind(s,a)!=0)m|=1<<a.ordinal();return m;}
    public static BlockState only(BlockState s,int mask){if(!(s.getBlock() instanceof CrossingWireBlock))return s;for(var a:Direction.Axis.values())if((mask&(1<<a.ordinal()))==0)s=s.setValue(LANES[a.ordinal()],0);return s;}
    public static BlockState merge(BlockState a,BlockState b){for(var axis:Direction.Axis.values())if(kind(b,axis)!=0){if(kind(a,axis)!=0&&kind(a,axis)!=kind(b,axis))throw new IllegalArgumentException("Conflicting crossing lanes");a=a.setValue(LANES[axis.ordinal()],kind(b,axis));}if(Integer.bitCount(mask(a))>2)throw new IllegalArgumentException("Only two lanes fit in one block");return a;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new CrossingWireEntity(p,s);}
    @Override public boolean canWireConnect(BlockState s,Direction d){return kind(s,d.getAxis())!=0;}
    public static VoxelShape laneShape(BlockState s,Direction.Axis axis){
        if(kind(s,axis)==0||Integer.bitCount(mask(s))>2)return Shapes.empty();
        double r=kind(s,axis)==3?2.5:2;
        int a=axis.ordinal(),offset=-1,other=-1;
        for(var b:Direction.Axis.values())if(b!=axis){if(kind(s,b)==0)offset=b.ordinal();else other=b.ordinal();}
        double shift=other<0?0:(a<other?3:-3);var result=Shapes.empty();
        for(int part=0;part<5;part++){
            double[] lo={8-r,8-r,8-r},hi={8+r,8+r,8+r};
            lo[a]=new double[]{0,3,3,11,13}[part];hi[a]=new double[]{3,5,13,13,16}[part];
            if(shift!=0&&part>0&&part<4){lo[offset]+=part==2?shift:Math.min(0,shift);hi[offset]+=part==2?shift:Math.max(0,shift);}
            result=Shapes.or(result,Block.box(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]));
        }
        return result;
    }
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){var result=Shapes.empty();for(var a:Direction.Axis.values())result=Shapes.or(result,laneShape(s,a));return result;}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
    public static Direction.Axis hit(BlockState s,BlockPos p,Vec3 point){
        var local=point.subtract(p.getX(),p.getY(),p.getZ());Direction.Axis best=null;double distance=Double.POSITIVE_INFINITY;
        for(var a:Direction.Axis.values())for(var box:laneShape(s,a).toAabbs()){
            double x=Math.max(box.minX-local.x,Math.max(0,local.x-box.maxX)),y=Math.max(box.minY-local.y,Math.max(0,local.y-box.maxY)),z=Math.max(box.minZ-local.z,Math.max(0,local.z-box.maxZ));
            double d=x*x+y*y+z*z;if(d<distance){distance=d;best=a;}
        }
        return best;
    }
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult target,BlockGetter level,BlockPos pos,net.minecraft.world.entity.player.Player player){
        var axis=hit(state,pos,target.getLocation());if(axis==null)return net.minecraft.world.item.ItemStack.EMPTY;
        int kind=kind(state,axis);var stack=new net.minecraft.world.item.ItemStack((kind==3?com.example.compiledcircuits.registry.ModItems.BUNDLED_WIRE:kind==2?com.example.compiledcircuits.registry.ModItems.ID_WIRE:com.example.compiledcircuits.registry.ModItems.BASIC_WIRE).get());
        if(kind==2&&level.getBlockEntity(pos) instanceof CrossingWireEntity wire)com.example.compiledcircuits.item.IdWireItem.configure(stack,wire.id(axis),wire.color(axis));
        return stack;
    }
    @Override public BlockState rotate(BlockState s,Rotation r){var out=only(s,0);for(var a:Direction.Axis.values())out=out.setValue(LANES[r.rotate(Direction.get(Direction.AxisDirection.POSITIVE,a)).getAxis().ordinal()],kind(s,a));return out;}
    @Override public BlockState mirror(BlockState s,Mirror m){return s;}
}
