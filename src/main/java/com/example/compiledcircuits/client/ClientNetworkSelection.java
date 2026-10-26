package com.example.compiledcircuits.client;

import com.example.compiledcircuits.block.IWireConnectable;
import com.example.compiledcircuits.block.InputEndpointBlock;
import com.example.compiledcircuits.block.OutputEndpointBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public final class ClientNetworkSelection {

    // Захист від випадкового сканування надто великої мережі.
    private static final int MAX_SCAN_SIZE = 50_000;

    private static Set<BlockPos> WIRES = new HashSet<>();
    private static Set<BlockPos> INPUTS = new HashSet<>();
    private static Set<BlockPos> OUTPUTS = new HashSet<>();

    private static java.util.Map<BlockPos,Integer> laneMasks=java.util.Map.of();
    public static int laneMask(BlockPos pos){return laneMasks.getOrDefault(pos,7);}
    private static Object selectionLevel;
    public static boolean isForLevel(Object level){return selectionLevel==level;}
    public static void publish(Object level,com.example.compiledcircuits.networking.HighlightReplica.Selection selected){
        selectionLevel=level;laneMasks=selected.lanes();WIRES=selected.wires();INPUTS=selected.inputs();OUTPUTS=selected.outputs();
    }
    private ClientNetworkSelection() {
    }

    public static boolean selectNetwork(
            ClientLevel level,
            BlockPos startPos
    ) {

        return selectNetwork(level,startPos,7);
    }
    public static boolean selectNetwork(ClientLevel level,BlockPos startPos,int mask){
        clear();selectionLevel=level;
        if(!(level.getBlockState(startPos).getBlock() instanceof IWireConnectable)||!ClientCompiledElements.isReadyFor(level.dimension().location().toString())||ClientCompiledElements.isBlocked(startPos))return false;
        var job=new ClientLaneTraversal(level,startPos,mask);while(!job.done())job.step(1024);
        if(job.failed){clear();return false;}
        laneMasks=java.util.Map.copyOf(job.masks);
        for(var pos:job.masks.keySet())if(level.hasChunkAt(pos))classify(pos,level.getBlockState(pos));else WIRES.add(pos);
        return getTotalSize()>0;
    }

    private static void classify(
            BlockPos pos,
            BlockState state
    ) {

        BlockPos immutablePos = pos.immutable();

        if (state.getBlock() instanceof InputEndpointBlock) {

            INPUTS.add(immutablePos);

        } else if (state.getBlock() instanceof OutputEndpointBlock) {

            OUTPUTS.add(immutablePos);

        } else {

            WIRES.add(immutablePos);
        }
    }

    public static void clear() {
        laneMasks=java.util.Map.of();WIRES=new HashSet<>();INPUTS=new HashSet<>();OUTPUTS=new HashSet<>();selectionLevel=null;
    }

    public static Set<BlockPos> getWires() {
        return Collections.unmodifiableSet(WIRES);
    }

    public static Set<BlockPos> getInputs() {
        return Collections.unmodifiableSet(INPUTS);
    }

    public static Set<BlockPos> getOutputs() {
        return Collections.unmodifiableSet(OUTPUTS);
    }

    public static int getTotalSize() {
        return WIRES.size()
                + INPUTS.size()
                + OUTPUTS.size();
    }

    public static void setSelection(
            java.util.Collection<BlockPos> wires,
            java.util.Collection<BlockPos> inputs,
            java.util.Collection<BlockPos> outputs
    ) {
        clear();

        for (BlockPos pos : wires) {
            WIRES.add(pos.immutable());
        }

        for (BlockPos pos : inputs) {
            INPUTS.add(pos.immutable());
        }

        for (BlockPos pos : outputs) {
            OUTPUTS.add(pos.immutable());
        }
    }
}
