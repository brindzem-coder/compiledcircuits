package com.example.compiledcircuits.event;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=com.example.compiledcircuits.CompiledCircuits.MOD_ID)
public final class CrossingBreakEvents {
    private static boolean checkingProtection;
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void broken(BlockEvent.BreakEvent event){
        if(checkingProtection)return;
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getState().getBlock() instanceof CrossingWireBlock)||DeletionJobs.isDeletionEvent(event))return;
        var player=event.getPlayer();var hit=player.pick(8,1,false);
        if(!(hit instanceof net.minecraft.world.phys.BlockHitResult target)||!target.getBlockPos().equals(event.getPos()))return;
        var axis=CrossingWireBlock.hit(event.getState(),event.getPos(),hit.getLocation());if(axis==null)return;
        if(!(player instanceof net.minecraft.server.level.ServerPlayer server)||!NetworkOperations.canModify(server)||!level.mayInteract(player,event.getPos()))return;
        var state=event.getState();int kind=CrossingWireBlock.kind(state,axis);var stack=new ItemStack((kind==3?ModItems.BUNDLED_WIRE:kind==2?ModItems.ID_WIRE:ModItems.BASIC_WIRE).get());
        if(kind==2&&level.getBlockEntity(event.getPos()) instanceof CrossingWireEntity wire)com.example.compiledcircuits.item.IdWireItem.configure(stack,wire.id(axis),wire.color(axis));
        event.setCanceled(true);
        boolean denied;
        checkingProtection=true;
        try{denied=net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,event.getPos(),state,player));}
        finally{checkingProtection=false;}
        if(denied||level.getBlockState(event.getPos())!=state||!NetworkOperations.canModify(server))return;
        if(Crossings.remove(level,event.getPos(),1<<axis.ordinal())){
            NetworkIntegrityManager.scheduleCheck(level,event.getPos());
            if(!player.getAbilities().instabuild)Block.popResource(level,event.getPos(),stack);
        }
    }
}
