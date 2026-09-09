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

        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.SERVER,
                com.example.compiledcircuits.config.ServerConfig.SPEC, "compiledcircuits-server.toml");
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);

        ModNetworking.register();
    }
}