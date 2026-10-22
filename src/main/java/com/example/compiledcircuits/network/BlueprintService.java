package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.ModItems;
import com.example.compiledcircuits.networking.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

public final class BlueprintService {
    private static final Map<ServerPlayer,Long> lastAction=new WeakHashMap<>();
    public static ItemStack tool(ServerPlayer player){
        for(var hand:net.minecraft.world.InteractionHand.values())if(player.getItemInHand(hand).is(ModItems.CIRCUIT_COPIER.get()))return player.getItemInHand(hand);
        return ItemStack.EMPTY;
    }
    public static UUID id(ItemStack stack){return stack.hasTag()&&stack.getTag().hasUUID("Blueprint")?stack.getTag().getUUID("Blueprint"):null;}
    public static void request(ServerPlayer player,int networkId){
        if(!NetworkOperations.canModify(player)||tool(player).isEmpty())return;
        long now=player.serverLevel().getGameTime();Long previous=lastAction.get(player);if(previous!=null&&now-previous<5)return;lastAction.put(player,now);
        try{
            var storage=BlueprintStorage.get(player.getServer());var stack=tool(player);UUID id=id(stack);
            if(networkId>0){
                var source=NetworkSavedData.get(player.getServer()).getNetwork(networkId);
                if(source==null)throw new IllegalArgumentException("Compilation no longer exists");
                var sourceLevel=player.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(source.getDimension())));
                if(sourceLevel==null)throw new IllegalArgumentException("Source dimension is unavailable");
                var blueprint=CircuitBlueprint.capture(source,sourceLevel);id=storage.put(blueprint);
                stack.getOrCreateTag().putUUID("Blueprint",id);stack.getOrCreateTag().putString("BlueprintName",blueprint.name());
                player.sendSystemMessage(Component.translatable("blueprint.compiledcircuits.copied",blueprint.name()));
            }
            var blueprint=id==null?null:storage.get(id);if(blueprint==null)return;
            var elements=blueprint.elements(BlockPos.ZERO,0);
            for(int start=0;start<elements.size();start+=128){
                var list=new net.minecraft.nbt.ListTag();for(var e:elements.subList(start,Math.min(start+128,elements.size())))list.add(e.save());
                var payload=new net.minecraft.nbt.CompoundTag();payload.put("elements",list);
                ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new BlueprintPreviewS2CPacket(id,blueprint.name(),start, elements.size(),payload));
            }
        }catch(IllegalArgumentException failure){player.sendSystemMessage(Component.literal(failure.getMessage()));}
    }
    public static void place(ServerPlayer player,UUID id,BlockPos anchor,int turns){
        if(!NetworkOperations.canModify(player)||!Objects.equals(id,id(tool(player))))return;
        long now=player.serverLevel().getGameTime();Long previous=lastAction.get(player);if(previous!=null&&now-previous<5)return;lastAction.put(player,now);
        try{
            if(anchor.distSqr(player.blockPosition())>128*128||turns<0||turns>3)throw new IllegalArgumentException("Placement is too far away");
            var blueprint=BlueprintStorage.get(player.getServer()).get(id);if(blueprint==null)throw new IllegalArgumentException("Blueprint no longer exists");
            var network=paste(player,blueprint,anchor,turns);
            player.sendSystemMessage(Component.translatable("blueprint.compiledcircuits.placed",network.getId()));
        }catch(IllegalArgumentException failure){player.sendSystemMessage(Component.literal(failure.getMessage()));}
    }
    static CompiledNetwork paste(ServerPlayer player,CircuitBlueprint blueprint,BlockPos anchor,int turns){
        var level=player.serverLevel();var data=NetworkSavedData.get(player.getServer());
        if(level.captureBlockSnapshots||level.restoringBlockSnapshots||!level.capturedBlockSnapshots.isEmpty())throw new IllegalArgumentException("Placement transaction is busy");
        var network=blueprint.network(data.getNextNetworkId(),level.dimension().location().toString(),anchor,turns);
        for(var e:network.getElements()){
            var pos=e.getPos();
            if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||!NetworkRuntime.isChunkAvailable(level,RuntimeSignalReader.chunk(pos)))throw new IllegalArgumentException("Placement crosses an unavailable area");
            if(!level.getBlockState(pos).isAir()||!data.getMembershipOwners(network.getDimension(),pos).isEmpty())throw new IllegalArgumentException("Placement is occupied at "+pos.toShortString());
            if(!level.isUnobstructed(e.resolveState().state().orElseThrow(),pos,net.minecraft.world.phys.shapes.CollisionContext.empty()))throw new IllegalArgumentException("An entity obstructs placement at "+pos.toShortString());
            if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,Direction.UP,tool(player)))throw new IllegalArgumentException("Placement is not allowed here");
        }
        data.capacity().check(List.of(network),null);
        data.runtime().validateBundleCompilation(network);
        List<BlockSnapshot> snapshots=new ArrayList<>();boolean committed=false;
        try{
            level.captureBlockSnapshots=true;
            try{
                for(var e:network.getElements()){
                    var pos=e.getPos();if(!level.setBlock(pos,e.resolveState().state().orElseThrow(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))throw new IllegalArgumentException("Could not place "+pos.toShortString());
                    var entity=level.getBlockEntity(pos);
                    if(entity instanceof IdWireEntity wire)wire.configure(e.wireId(),e.wireColor());
                    if(entity instanceof BundlePortEntity port)port.configure(e.channelId());
                    if(entity instanceof BundleWireEntity wire)wire.restorePorts(e.bundleFaces());
                }
            }finally{level.captureBlockSnapshots=false;snapshots.addAll(level.capturedBlockSnapshots);level.capturedBlockSnapshots.clear();}
            boolean denied=snapshots.size()==1?ForgeEventFactory.onBlockPlace(player,snapshots.get(0),Direction.UP):ForgeEventFactory.onMultiBlockPlace(player,snapshots,Direction.UP);
            if(denied||!NetworkOperations.canModify(player))throw new IllegalArgumentException("Placement was denied");
            for(var e:network.getElements())if(CompiledBlockStateMatcher.match(e,level,level.getBlockState(e.getPos()),true)!=CompiledBlockStateMatcher.Match.MATCH)throw new IllegalArgumentException("Placement changed before completion");
            data.runtime().validateBundleCompilation(network);
            data.addNetwork(network);committed=true;
            for(var e:network.getElements()){
                var pos=e.getPos();if(level.getBlockEntity(pos) instanceof IdWireEntity wire)wire.reconcile();
                var state=level.getBlockState(pos);level.sendBlockUpdated(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),state,3);
                CircuitBoundary.refreshWire(level,pos);
                for(var direction:Direction.values())if(level.hasChunkAt(pos.relative(direction)))CircuitBoundary.refreshWire(level,pos.relative(direction));
            }
            return network;
        }finally{
            if(!committed){level.restoringBlockSnapshots=true;try{for(int i=snapshots.size()-1;i>=0;i--)snapshots.get(i).restore(true,false);}finally{level.restoringBlockSnapshots=false;}}
        }
    }
}
