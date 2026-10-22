package com.example.compiledcircuits.event;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=com.example.compiledcircuits.CompiledCircuits.MOD_ID)
public final class BlueprintInteractionEvents {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void click(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event){
        if(event.getEntity().getMainHandItem().is(com.example.compiledcircuits.registry.ModItems.CIRCUIT_COPIER.get())
                || event.getEntity().getOffhandItem().is(com.example.compiledcircuits.registry.ModItems.CIRCUIT_COPIER.get())){
            event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
            if(!event.getItemStack().is(com.example.compiledcircuits.registry.ModItems.CIRCUIT_COPIER.get()))
                event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }
}
