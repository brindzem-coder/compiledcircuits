package com.example.compiledcircuits.event;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.networking.CompiledElementSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID)
public final class CircuitVisualSyncEvents {
    private CircuitVisualSyncEvents() {}
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CompiledElementSync.sendSnapshot(player);
    }
    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CompiledElementSync.sendSnapshot(player);
    }
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CompiledElementSync.sendSnapshot(player);
    }
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) CompiledElementSync.flushDirty(event.getServer());
    }
    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) { CompiledElementSync.clear(); }
}
