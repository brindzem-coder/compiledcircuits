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
    /** Explicitly supported placements; arbitrary saved block IDs must not bypass repair policy. */
    private static boolean supports(Block block) {
        return block instanceof com.example.compiledcircuits.block.CrossingWireBlock || block instanceof com.example.compiledcircuits.block.BundledWireBlock || block instanceof com.example.compiledcircuits.block.BundlePortBlock || block == ModBlocks.ID_WIRE.get() || block == ModBlocks.NETWORK_WIRE.get() || block == ModBlocks.BASIC_WIRE.get()
                || block == ModBlocks.INPUT_ENDPOINT.get() || block == ModBlocks.OUTPUT_ENDPOINT.get()
                || block == ModBlocks.SIGNAL_SWITCH.get() || block == ModBlocks.SIGNAL_LAMP.get()
                || block == ModBlocks.AND_GATE.get() || block == ModBlocks.OR_GATE.get()
                || block == ModBlocks.NAND_GATE.get() || block == ModBlocks.NOR_GATE.get()
                || block == ModBlocks.XOR_GATE.get() || block == ModBlocks.XNOR_GATE.get()
                || block == ModBlocks.NOT_GATE.get()
                || block == ModBlocks.D_LATCH.get()
                || block == ModBlocks.GATE_INPUT.get() || block == ModBlocks.GATE_OUTPUT.get();
    }
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
        if(current!=null && !current.sameOccurrence(target))return Outcome.CHANGED;
        if(!level.dimension().location().toString().equals(network.getDimension()))return Outcome.UNLOADED;
        var pos=element.getPos();var decoded=element.resolveState();
        if(decoded.status()==CompiledBlockStateCodec.Status.UNRESOLVED)return Outcome.INVALID;
        var id=ResourceLocation.tryParse(element.getBlockId());if(id==null || !BuiltInRegistries.BLOCK.containsKey(id))return Outcome.INVALID;
        Block expected=BuiltInRegistries.BLOCK.get(id);
        if(!supports(expected))return Outcome.UNSUPPORTED;
        if(level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos))return Outcome.INVALID;
        // Supported blocks inspect their immediate neighborhood and may issue neighbor-shape callbacks.
        // Keep the fixed radius-two neighborhood available before any placement helper can read it.
        for(long chunk:RuntimeSignalReader.dependencyChunks(pos)) if(!NetworkRuntime.isChunkAvailable(level,chunk))return Outcome.UNLOADED;
        if(!level.mayInteract(player,pos))return Outcome.PROTECTED;
        var actual=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4).getBlockState(pos);
        if(expected instanceof com.example.compiledcircuits.block.CrossingWireBlock){
            if(Crossings.matches(element,level,actual))return Outcome.CORRECT;
            if(!actual.isAir()&&!(actual.getBlock() instanceof com.example.compiledcircuits.block.CrossingWireBlock))return Outcome.OCCUPIED;
            var desired=decoded.state().orElseThrow();var lanes=element.crossingLanes();
            if(actual.getBlock() instanceof com.example.compiledcircuits.block.CrossingWireBlock){
                var keep=com.example.compiledcircuits.block.CrossingWireBlock.only(actual,7&~Crossings.mask(element));
                desired=com.example.compiledcircuits.block.CrossingWireBlock.merge(keep,desired);
                if(level.getBlockEntity(pos) instanceof com.example.compiledcircuits.block.CrossingWireEntity wire){var other=wire.snapshot(7&~Crossings.mask(element));other.merge(lanes);lanes=other;}
            }
            var saved=lanes;
            boolean restored=ProtectedRepairPlacement.place(level,pos,desired,player,()->NetworkOperations.canModify(player)&&NetworkSavedData.get(level.getServer()).getNetwork(network.getId())==network,
                    ()->((com.example.compiledcircuits.block.CrossingWireEntity)level.getBlockEntity(pos)).restore(saved));
            if(restored)NetworkIntegrityManager.scheduleCheck(level,pos);
            return restored?Outcome.PLACED:Outcome.PROTECTED;
        }

        var match=CompiledBlockStateMatcher.match(element,level,actual,NetworkIntegrityManager.exactIntegrityEnabled());
        if(match==CompiledBlockStateMatcher.Match.MATCH){NetworkIntegrityManager.scheduleCheck(level,pos);return Outcome.CORRECT;}
        if(current==null)return Outcome.CHANGED;
        // Wire variants represent the same editable wire, with or without face ports.
        boolean repairFaces = match == CompiledBlockStateMatcher.Match.STATE_MISMATCH
                && (((expected instanceof com.example.compiledcircuits.block.SignalSwitchBlock || expected instanceof com.example.compiledcircuits.block.SignalLampBlock || expected instanceof com.example.compiledcircuits.block.GateBodyBlock || expected instanceof com.example.compiledcircuits.block.BundlePortBlock || expected instanceof com.example.compiledcircuits.block.BundledWireBlock) && actual.is(expected))
                || (expected instanceof com.example.compiledcircuits.block.BasicWireBlock
                    && actual.getBlock() instanceof com.example.compiledcircuits.block.BasicWireBlock));
        if(!repairFaces && (match==CompiledBlockStateMatcher.Match.STATE_MISMATCH || !(actual.isAir()||actual.canBeReplaced()))
                || actual.hasBlockEntity() && !((expected instanceof com.example.compiledcircuits.block.SignalLampBlock || expected instanceof com.example.compiledcircuits.block.IdWireBlock || expected instanceof com.example.compiledcircuits.block.BundlePortBlock || expected instanceof com.example.compiledcircuits.block.BundledWireBlock) && actual.is(expected)))return Outcome.OCCUPIED;
        var restored=decoded.state().orElseGet(expected::defaultBlockState);
        // Repair topology without resetting the user's live switch value or copying stale lamp light.
        if(expected instanceof com.example.compiledcircuits.block.SignalSwitchBlock && actual.is(expected))
            restored=restored.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED,
                    actual.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED));
        if(expected instanceof com.example.compiledcircuits.block.SignalLampBlock)
            restored=restored.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,false);
        if(expected instanceof com.example.compiledcircuits.block.BasicWireBlock)restored=Block.updateFromNeighbourShapes(restored,level,pos);
        if(!restored.is(expected) || !restored.canSurvive(level,pos)
                || !level.isUnobstructed(restored,pos,net.minecraft.world.phys.shapes.CollisionContext.empty()))return Outcome.FAILED;
        if(!ProtectedRepairPlacement.place(level,pos,restored,player,()->NetworkOperations.canModify(player)
                && NetworkSavedData.get(level.getServer()).getNetwork(network.getId())==network
                && target.sameOccurrence(network.getBrokenElement(target.getElementId())), () -> {
                    if (level.getBlockEntity(pos) instanceof com.example.compiledcircuits.block.BundleWireEntity wire) wire.restorePorts(element.bundleFaces());
                    if (level.getBlockEntity(pos) instanceof com.example.compiledcircuits.block.BundlePortEntity port) port.configure(element.channelId());
                    if (level.getBlockEntity(pos) instanceof com.example.compiledcircuits.block.IdWireEntity wire)
                        wire.configure(element.wireId(), WireCatalog.get(level.getServer()).color(element.wireId(), element.wireColor()));
                }))return Outcome.PROTECTED;
        if (level.getBlockEntity(pos) instanceof com.example.compiledcircuits.block.BundleWireEntity wire) wire.refreshConnections();
        if(expected instanceof com.example.compiledcircuits.block.SignalLampBlock)level.scheduleTick(pos,expected,1);
        NetworkIntegrityManager.scheduleCheck(level,pos);
        for(var direction:net.minecraft.core.Direction.values())NetworkIntegrityManager.scheduleCheck(level,pos.relative(direction));
        return Outcome.PLACED;
    }
}
