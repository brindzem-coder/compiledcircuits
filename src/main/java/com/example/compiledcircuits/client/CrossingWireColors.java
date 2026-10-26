package com.example.compiledcircuits.client;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=com.example.compiledcircuits.CompiledCircuits.MOD_ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class CrossingWireColors {
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void colors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event){
        event.register((state,level,pos,index)->{
            if(index<0||index>2)return 0xffffff;
            var axis=net.minecraft.core.Direction.Axis.values()[index];int kind=com.example.compiledcircuits.block.CrossingWireBlock.kind(state,axis);
            if(kind==3)return 0x555555;if(kind!=2)return com.example.compiledcircuits.network.WireIds.RED;
            return level!=null&&pos!=null&&level.getBlockEntity(pos) instanceof com.example.compiledcircuits.block.CrossingWireEntity wire?WireClient.color(wire.id(axis),wire.color(axis)):0xffffff;
        },com.example.compiledcircuits.registry.ModBlocks.CROSSING_WIRE.get());
    }
}
