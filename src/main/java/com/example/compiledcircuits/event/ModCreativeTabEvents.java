package com.example.compiledcircuits.event;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = CompiledCircuits.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class ModCreativeTabEvents {

    @SubscribeEvent
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {

        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModItems.BASIC_WIRE);
            event.accept(ModItems.INPUT_ENDPOINT);
            event.accept(ModItems.OUTPUT_ENDPOINT);
            event.accept(ModItems.NETWORK_SELECTOR);
        }
    }

}