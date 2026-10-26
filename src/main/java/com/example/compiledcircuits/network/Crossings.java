package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Lane identity is an axis bit, never a second world coordinate. */
public final class Crossings {
    private Crossings() {}
    public static int mask(CompiledCircuitElement e){return e.getBlockId().equals("compiledcircuits:crossing_wire")?e.resolveState().state().map(CrossingWireBlock::mask).orElse(7):7;}
    public static boolean overlaps(CompiledCircuitElement a,CompiledCircuitElement b){return (mask(a)&mask(b))!=0;}
    public static boolean free(NetworkSavedData data,String dim,BlockPos pos,int mask){return !data.hasUnknownMembershipReservations()&&data.getBlockingRecords(dim,pos).isEmpty()&&data.getMembershipOwners(dim,pos).stream().noneMatch(o->(mask(o.element())&mask)!=0);}
    public static BlockState project(BlockState s,Direction.Axis axis){return CrossingWireBlock.only(s,axis==null?7:1<<axis.ordinal());}
    public static BlockState ordinary(BlockState state,Direction.Axis axis){
        return switch(CrossingWireBlock.kind(state,axis)){case 1->ModBlocks.BASIC_WIRE.get().defaultBlockState();case 2->ModBlocks.ID_WIRE.get().defaultBlockState();case 3->ModBlocks.BUNDLED_WIRE.get().defaultBlockState();default->net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();};
    }
    public static String id(BlockGetter level,BlockPos pos,BlockState s,Direction.Axis axis){return s.getBlock() instanceof CrossingWireBlock&&level.getBlockEntity(pos) instanceof CrossingWireEntity e?e.id(axis):IdWireBlock.id(level,pos,s);}
    public static boolean connects(BlockGetter level,BlockPos pos,BlockState a,BlockState b,Direction side){
        boolean left=a.getBlock() instanceof CrossingWireBlock,right=b.getBlock() instanceof CrossingWireBlock;
        if(left&&CrossingWireBlock.kind(a,side.getAxis())==0||right&&CrossingWireBlock.kind(b,side.getAxis())==0)return false;
        var x=left?ordinary(a,side.getAxis()):a;var y=right?ordinary(b,side.getAxis()):b;
        if(x.getBlock() instanceof BundledWireBlock||y.getBlock() instanceof BundledWireBlock){
            if(x.getBlock() instanceof BundledWireBlock&&y.getBlock() instanceof BundledWireBlock)
                return (left||BundleConnections.port(level,pos,side)==null)&&(right||BundleConnections.port(level,pos.relative(side),side.getOpposite())==null);
            // Existing surface ports have one external wire; a crossing lane can terminate there.
            if(!left&&x.getBlock() instanceof BundledWireBlock)return BundleConnections.connects(level,pos,x,y,side);
            if(!right&&y.getBlock() instanceof BundledWireBlock)return BundleConnections.connects(level,pos.relative(side),y,x,side.getOpposite());
            var peer=x.getBlock() instanceof BundledWireBlock?y:x;var face=x.getBlock() instanceof BundledWireBlock?side.getOpposite():side;
            return peer.getBlock() instanceof BundlePortBlock&&BundlePortBlock.cableSide(peer)==face;
        }
        return CircuitConnections.connects(x,id(level,pos,a,side.getAxis()),y,id(level,pos.relative(side),b,side.getAxis()),side);
    }
    public static String graphIds(CompoundTag lanes){return Arrays.stream(Direction.Axis.values()).map(a->lanes.getCompound(a.getName()).getString("id")).collect(java.util.stream.Collectors.joining("/"));}
    private static String laneId(Map<BlockPos,String> ids,BlockPos p,BlockState s,Direction.Axis axis){
        String id=ids.getOrDefault(p,"0");if(!(s.getBlock() instanceof CrossingWireBlock))return id;
        String[] parts=id.split("/",-1);return parts.length==3&&!parts[axis.ordinal()].isEmpty()?parts[axis.ordinal()]:"0";
    }
    /** Find the next ordinary graph element along one lane, without joining crossing axes. */
    public static BlockPos next(Map<BlockPos,BlockState> states,BlockPos pos,Direction side){
        var next=pos.relative(side);int remaining=states.size();
        while(remaining-->0){var s=states.get(next);if(s==null||!(s.getBlock() instanceof CrossingWireBlock)||CrossingWireBlock.kind(s,side.getAxis())==0)return next;next=next.relative(side);}
        throw new IllegalArgumentException("Crossing chain exceeds graph size");
    }
    public static boolean graphConnects(Map<BlockPos,BlockState> states,Map<BlockPos,String> ids,BlockPos from,BlockPos to,Direction side){
        var a=states.get(from);if(a==null)return false;var pos=from;
        for(int remaining=states.size();remaining-->0;){
            var next=pos.relative(side);var b=states.get(next);if(b==null)return false;
            var x=a.getBlock() instanceof CrossingWireBlock?ordinary(a,side.getAxis()):a;
            var y=b.getBlock() instanceof CrossingWireBlock?ordinary(b,side.getAxis()):b;
            if(!CircuitConnections.connects(x,laneId(ids,pos,a,side.getAxis()),y,laneId(ids,next,b,side.getAxis()),side))return false;
            if(next.equals(to))return true;if(!(b.getBlock() instanceof CrossingWireBlock))return false;pos=next;a=b;
        }
        return false;
    }
    public static final class Owners extends HashMap<BlockPos,Integer>{
        private final Map<BundleFace,Integer> lanes=new HashMap<>();
        public void add(CompiledCircuitElement e,int owner){put(e.getPos(),owner);for(var side:Direction.values())if((mask(e)&(1<<side.getAxis().ordinal()))!=0)lanes.put(new BundleFace(e.getPos(),side),owner);}
    }
    public static Integer owner(Map<BlockPos,Integer> owners,BlockPos pos,Direction side){return owners instanceof Owners o?o.lanes.get(new BundleFace(pos,side)):owners.get(pos);}
    public static void mergeGraph(Map<BlockPos,BlockState> states,Map<BlockPos,String> ids,CompiledNetwork network){
        var incoming=network.wireIds();
        for(var e:network.getElements()){
            var state=e.resolveState().state().orElseThrow();var old=states.get(e.getPos());
            String next=incoming.getOrDefault(e.getPos(),"0");
            if(old!=null && state.getBlock() instanceof CrossingWireBlock && old.getBlock() instanceof CrossingWireBlock){
                String[] left=ids.getOrDefault(e.getPos(),"//").split("/",-1),right=next.split("/",-1);
                for(int a=0;a<3;a++)if(CrossingWireBlock.kind(state,Direction.Axis.values()[a])!=0)left[a]=right[a];
                next=String.join("/",left);state=CrossingWireBlock.merge(old,state);
            }
            states.put(e.getPos(),state);ids.put(e.getPos(),next);
        }
    }
    public static boolean matches(CompiledCircuitElement e,BlockGetter level,BlockState actual){
        var expected=e.resolveState().state().orElse(null);if(expected==null||!(actual.getBlock() instanceof CrossingWireBlock))return false;
        if(!(level.getBlockEntity(e.getPos()) instanceof CrossingWireEntity wire))return false;
        for(var axis:Direction.Axis.values())if(CrossingWireBlock.kind(expected,axis)!=0){
            if(CrossingWireBlock.kind(expected,axis)!=CrossingWireBlock.kind(actual,axis))return false;
            if(CrossingWireBlock.kind(expected,axis)==2&&!e.crossingLanes().getCompound(axis.getName()).getString("id").equals(wire.id(axis)))return false;
        }
        return true;
    }
    public static boolean remove(ServerLevel level,BlockPos pos,int mask){
        var state=level.getBlockState(pos);if(!(state.getBlock() instanceof CrossingWireBlock))return false;
        var remaining=CrossingWireBlock.only(state,CrossingWireBlock.mask(state)&~mask);
        return level.setBlock(pos,CrossingWireBlock.mask(remaining)==0?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():remaining,3);
    }
}
