package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Bounded structural capture, with the same adjacency and lane boundaries as compilation. */
final class UncompiledBlueprint {
    private record Node(BlockPos pos,BlockState state) {}
    static CircuitBlueprint capture(ServerLevel level,BlockPos start,int selectedMask) {
        var data=NetworkSavedData.get(level.getServer());var dimension=level.dimension().location().toString();
        var states=new TreeMap<BlockPos,BlockState>();var queue=new ArrayDeque<Node>();
        var first=CrossingWireBlock.only(read(level,start),selectedMask);
        if(!(first.getBlock() instanceof IWireConnectable)||first.getBlock() instanceof CrossingWireBlock&&CrossingWireBlock.mask(first)==0)
            throw new IllegalArgumentException("Select a circuit block to copy");
        if(!Crossings.free(data,dimension,start,selectedMask))throw new IllegalArgumentException("Selected circuit is reserved by a compilation");
        enqueue(states,queue,start,first,start);
        while(!queue.isEmpty()){
            var current=queue.remove();
            for(var side:Direction.values()){
                if(!(current.state().getBlock() instanceof GateBodyBlock)&&!((IWireConnectable)current.state().getBlock()).canWireConnect(current.state(),side))continue;
                var next=current.pos().relative(side);if(level.isOutsideBuildHeight(next))continue;
                var state=Crossings.project(read(level,next),side.getAxis());
                int mask=state.getBlock() instanceof CrossingWireBlock?CrossingWireBlock.mask(state):7;
                if(mask!=0&&Crossings.free(data,dimension,next,mask)&&CircuitConnections.connects(level,current.pos(),current.state(),state,side))
                    enqueue(states,queue,next,state,start);
            }
        }
        var entries=new ListTag();int id=0;
        for(var entry:states.entrySet()){
            var state=entry.getValue();var block=state.getBlock();
            var type=block instanceof InputEndpointBlock?CircuitElementType.INPUT:block instanceof OutputEndpointBlock?CircuitElementType.OUTPUT
                    :block instanceof GatePortBlock port?(port.isInput()?CircuitElementType.GATE_INPUT:CircuitElementType.GATE_OUTPUT)
                    :block instanceof GateBodyBlock||block instanceof NotGateBlock||block instanceof DLatchBlock?CircuitElementType.GATE:CircuitElementType.WIRE;
            entries.add(new CompiledCircuitElement(++id,entry.getKey(),type,CompiledBlockStateCodec.capture(state)).captureWire(level,true).save());
        }
        var origin=states.keySet().stream().reduce((a,b)->new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()))).orElseThrow();
        var tag=new CompoundTag();tag.put("elements",entries);tag.putLong("origin",origin.asLong());tag.putString("sourceDimension",dimension);
        tag.putBoolean("uncompiled",true);tag.putString("name","Uncompiled circuit ("+states.size()+" blocks)");
        return new CircuitBlueprint(tag);
    }
    private static BlockState read(ServerLevel level,BlockPos pos){
        if(!level.getWorldBorder().isWithinBounds(pos)||!NetworkRuntime.isChunkAvailable(level,RuntimeSignalReader.chunk(pos)))
            throw new IllegalArgumentException("Copy reaches an unavailable area; load the whole circuit and retry");
        return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4).getBlockState(pos);
    }
    private static void enqueue(Map<BlockPos,BlockState> states,ArrayDeque<Node> queue,BlockPos pos,BlockState state,BlockPos start){
        var old=states.get(pos);
        if(old!=null){
            if(!(state.getBlock() instanceof CrossingWireBlock))return;
            int fresh=CrossingWireBlock.mask(state)&~CrossingWireBlock.mask(old);if(fresh==0)return;
            state=CrossingWireBlock.only(state,fresh);states.put(pos,CrossingWireBlock.merge(old,state));queue.add(new Node(pos,state));return;
        }
        if(states.size()>=CircuitBlueprint.MAX_ELEMENTS)throw new IllegalArgumentException("Blueprint limit: 4096 blocks");
        var delta=pos.subtract(start);if(Math.abs((long)delta.getX())>256||Math.abs((long)delta.getY())>256||Math.abs((long)delta.getZ())>256)
            throw new IllegalArgumentException("Blueprint size limit: 256 blocks per axis");
        pos=pos.immutable();states.put(pos,state);queue.add(new Node(pos,state));
    }
}
