package com.example.compiledcircuits.client;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.network.Crossings;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import java.util.*;

/** Bounded lane-aware traversal shared by hover and explicit selection. */
final class ClientLaneTraversal {
    record Part(BlockPos pos,int mask){}
    final ClientLevel level;
    final Map<BlockPos,Integer> masks=new HashMap<>();
    final ArrayDeque<Part> queue=new ArrayDeque<>();
    boolean failed;
    ClientLaneTraversal(ClientLevel level,BlockPos start,int mask){
        this.level=level;
        Integer owner=ClientCompiledElements.networkIdAt(start,mask);
        if(owner!=null){for(var p:ClientCompiledElements.positionsForNetwork(owner))masks.put(p,ClientCompiledElements.maskAt(p,owner));}
        else if(!ClientCompiledElements.occupied(start,mask))enqueue(start,mask);
    }
    private void enqueue(BlockPos pos,int mask){int fresh=mask&~masks.getOrDefault(pos,0);if(fresh==0)return;if(masks.size()>=50000){failed=true;queue.clear();return;}masks.merge(pos,mask,(a,b)->a|b);queue.add(new Part(pos.immutable(),fresh));}
    void step(int budget){while(!queue.isEmpty()&&budget-->0&&!failed){
        var part=queue.removeFirst();if(!level.hasChunkAt(part.pos)){failed=true;break;}
        var state=CrossingWireBlock.only(level.getBlockState(part.pos),part.mask);
        if(!(state.getBlock() instanceof IWireConnectable))continue;
        for(var side:Direction.values()){
            var next=part.pos.relative(side);if(!level.hasChunkAt(next)||ClientCompiledElements.isBlocked(next))continue;
            var other=Crossings.project(level.getBlockState(next),side.getAxis());
            int mask=other.getBlock() instanceof CrossingWireBlock?CrossingWireBlock.mask(other):7;
            if(mask==0||ClientCompiledElements.occupied(next,mask))continue;
            if(CircuitConnections.connects(level,part.pos,state,other,side))enqueue(next,mask);
        }
    }}
    boolean done(){return queue.isEmpty()||failed;}
}
