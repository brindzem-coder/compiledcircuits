package com.example.compiledcircuits;

import com.example.compiledcircuits.registry.ModBlocks;
import com.example.compiledcircuits.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.example.compiledcircuits.networking.ModNetworking;

@Mod(CompiledCircuits.MOD_ID)
public class CompiledCircuits {

    public static final String MOD_ID = "compiledcircuits";

    public CompiledCircuits() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);

        ModNetworking.register();
    }
}