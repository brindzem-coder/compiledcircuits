package com.example.compiledcircuits.event;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.command.CircuitCommands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = CompiledCircuits.MOD_ID
)
public class ModCommandEvents {

    @SubscribeEvent
    public static void onRegisterCommands(
            RegisterCommandsEvent event
    ) {

        CircuitCommands.register(
                event.getDispatcher()
        );
    }
}