package com.example.compiledcircuits.client;
import com.example.compiledcircuits.registry.ModItems;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=com.example.compiledcircuits.CompiledCircuits.MOD_ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class CrossingPlacementInput {
    @SubscribeEvent public static void click(PlayerInteractEvent.RightClickBlock event){
        if(!event.getLevel().isClientSide||!net.minecraft.client.gui.screens.Screen.hasControlDown()||event.getEntity().isShiftKeyDown())return;
        var stack=event.getItemStack();if(!stack.is(ModItems.BASIC_WIRE.get())&&!stack.is(ModItems.ID_WIRE.get())&&!stack.is(ModItems.BUNDLED_WIRE.get()))return;
        var state=event.getLevel().getBlockState(event.getPos());if(!(state.getBlock() instanceof com.example.compiledcircuits.block.BasicWireBlock||state.getBlock() instanceof com.example.compiledcircuits.block.CrossingWireBlock))return;
        event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        com.example.compiledcircuits.networking.ModNetworking.CHANNEL.sendToServer(new com.example.compiledcircuits.networking.CrossingPlaceC2SPacket(event.getPos(),event.getHand()));
    }
}
