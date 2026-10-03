package com.example.compiledcircuits.network;

import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

public final class NetworkRepairManager {
    private NetworkRepairManager() {}
    public enum Outcome { PLACED, OCCUPIED, UNSUPPORTED, FAILED, CORRECT, UNLOADED, INVALID, PROTECTED, CHANGED }
    /** All callers use the same authorized queued operation, including a single-element repair. */
    public static NetworkOperations.Result repairNetwork(ServerLevel level, CompiledNetwork network, ServerPlayer player) {
        if(player==null || NetworkSavedData.get(level.getServer()).getNetwork(network.getId())!=network)
            return new NetworkOperations.Result(NetworkOperations.Code.NOT_FOUND,"Current network required.",java.util.List.of());
        return NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.REPAIR,java.util.List.of(network.getId()),0,"");
    }
    static Outcome attempt(ServerLevel level,CompiledNetwork network,BrokenCircuitElement target,ServerPlayer player) {
        if(!NetworkOperations.canModify(player) || NetworkSavedData.get(level.getServer()).getNetwork(network.getId())!=network)return Outcome.FAILED;
        var element=network.getElement(target.getElementId());if(element==null)return Outcome.FAILED;
        var current=network.getBrokenElement(target.getElementId());
        if(current!=null && current!=target)return Outcome.CHANGED;
        if(!level.dimension().location().toString().equals(network.getDimension()))return Outcome.UNLOADED;
        var pos=element.getPos();var decoded=element.resolveState();
        if(decoded.status()==CompiledBlockStateCodec.Status.UNRESOLVED)return Outcome.INVALID;
        var id=ResourceLocation.tryParse(element.getBlockId());if(id==null || !BuiltInRegistries.BLOCK.containsKey(id))return Outcome.INVALID;
        Block expected=BuiltInRegistries.BLOCK.get(id);
        if(expected!=ModBlocks.BASIC_WIRE.get() && expected!=ModBlocks.INPUT_ENDPOINT.get() && expected!=ModBlocks.OUTPUT_ENDPOINT.get())return Outcome.UNSUPPORTED;
        if(level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos))return Outcome.INVALID;
        // Supported blocks inspect their immediate neighborhood and may issue neighbor-shape callbacks.
        // Keep the fixed radius-two neighborhood available before any placement helper can read it.
        for(long chunk:RuntimeSignalReader.dependencyChunks(pos)) if(!NetworkRuntime.isChunkAvailable(level,chunk))return Outcome.UNLOADED;
        if(!level.mayInteract(player,pos))return Outcome.PROTECTED;
        var actual=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4).getBlockState(pos);
        var match=CompiledBlockStateMatcher.match(element,actual,NetworkIntegrityManager.exactIntegrityEnabled());
        if(match==CompiledBlockStateMatcher.Match.MATCH){NetworkIntegrityManager.scheduleCheck(level,pos);return Outcome.CORRECT;}
        if(current==null)return Outcome.CHANGED;
        if(match==CompiledBlockStateMatcher.Match.STATE_MISMATCH || !(actual.isAir()||actual.canBeReplaced()) || actual.hasBlockEntity())return Outcome.OCCUPIED;
        var restored=decoded.state().orElseGet(expected::defaultBlockState);
        if(expected==ModBlocks.BASIC_WIRE.get())restored=Block.updateFromNeighbourShapes(restored,level,pos);
        if(!restored.is(expected) || !restored.canSurvive(level,pos)
                || !level.isUnobstructed(restored,pos,net.minecraft.world.phys.shapes.CollisionContext.empty()))return Outcome.FAILED;
        if(!ProtectedRepairPlacement.place(level,pos,restored,player,()->NetworkOperations.canModify(player)
                && NetworkSavedData.get(level.getServer()).getNetwork(network.getId())==network
                && network.getBrokenElement(target.getElementId())==target))return Outcome.PROTECTED;
        NetworkIntegrityManager.scheduleCheck(level,pos);
        for(var direction:net.minecraft.core.Direction.values())NetworkIntegrityManager.scheduleCheck(level,pos.relative(direction));
        return Outcome.PLACED;
    }
}
