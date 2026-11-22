package com.example.compiledcircuits.event;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.network.CreativeHotbar;
import com.example.compiledcircuits.networking.CreativeHotbarS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=CompiledCircuits.MOD_ID)
public final class CreativeHotbarEvents {
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){CreativeHotbar.clearServer();}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){CreativeHotbar.state(p).reconcile(p);CreativeHotbar.persist(p);CreativeHotbarS2CPacket.send(p);}}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p&&CreativeHotbar.state(p).reconcile(p)){CreativeHotbar.persist(p);p.inventoryMenu.broadcastChanges();CreativeHotbarS2CPacket.send(p);}}
    @SubscribeEvent public static void mode(PlayerEvent.PlayerChangeGameModeEvent e){if(e.getNewGameMode()!=GameType.CREATIVE&&e.getEntity() instanceof ServerPlayer p){CreativeHotbar.state(p).restore(p);CreativeHotbar.persist(p);CreativeHotbarS2CPacket.send(p);}}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p){CreativeHotbar.state(p).restore(p);CreativeHotbar.persist(p);}}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){CreativeHotbar.copy(e.getOriginal(),e.getEntity());}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)CreativeHotbarS2CPacket.send(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)CreativeHotbarS2CPacket.send(p);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){CreativeHotbar.persist(e.getEntity());CreativeHotbar.forget(e.getEntity());}
}
