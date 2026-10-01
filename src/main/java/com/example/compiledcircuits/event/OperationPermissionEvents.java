package com.example.compiledcircuits.event;
import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.network.NetworkOperations;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=CompiledCircuits.MOD_ID)
public final class OperationPermissionEvents {
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { NetworkOperations.forget(e.getEntity()); }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone e) { NetworkOperations.forget(e.getOriginal()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { NetworkOperations.clear(); }
}
