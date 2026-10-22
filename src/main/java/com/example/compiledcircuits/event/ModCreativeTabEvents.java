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
            event.accept(ModItems.SIGNAL_SWITCH);
            event.accept(ModItems.SIGNAL_LAMP);
            event.accept(ModItems.BUNDLE_INPUT);
            event.accept(ModItems.BUNDLE_OUTPUT);
            event.accept(ModItems.BUNDLE_NETWORK_INPUT);
            event.accept(ModItems.BUNDLE_NETWORK_OUTPUT);
            event.accept(ModItems.BUNDLED_WIRE);
            event.accept(ModItems.ID_WIRE);
            event.accept(ModItems.INPUT_ENDPOINT);
            event.accept(ModItems.OUTPUT_ENDPOINT);
            event.accept(ModItems.NETWORK_SELECTOR);
            event.accept(ModItems.CIRCUIT_COPIER);
            event.accept(ModItems.AND_GATE);
            event.accept(ModItems.OR_GATE);
            event.accept(ModItems.NOT_GATE);
            event.accept(ModItems.D_LATCH);
            event.accept(ModItems.WDE_PORT);
            event.accept(ModItems.GATE_SEPARATOR);
            event.accept(ModItems.NETWORK_INPUT);
            event.accept(ModItems.NETWORK_OUTPUT);
            event.accept(ModItems.NAND_GATE);
            event.accept(ModItems.NOR_GATE);
            event.accept(ModItems.XOR_GATE);
            event.accept(ModItems.XNOR_GATE);
            event.accept(ModItems.GATE_INPUT);
            event.accept(ModItems.GATE_OUTPUT);
        }
    }

}
