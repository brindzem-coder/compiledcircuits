package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, value = Dist.CLIENT)
public final class CircuitVisualClientEvents {
    private CircuitVisualClientEvents() {}
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var level = Minecraft.getInstance().level;
        ClientCompiledElements.onLevelChanged(level, level == null ? "" : level.dimension().location().toString());
        ClientCompiledElements.tick(System.nanoTime());
        ClientCircuitBlockIndex.onLevelChanged(level);
        ClientCircuitBlockIndex.tick(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        ClientHoveredCircuit.tick();
    }
    @SubscribeEvent
    public static void chunkLoad(net.minecraftforge.event.level.ChunkEvent.Load event) {
        if (event.getLevel() == Minecraft.getInstance().level && event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level
                && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
            ClientCircuitBlockIndex.onLevelChanged(level);
            ClientCircuitBlockIndex.onChunkLoaded(level, chunk);
        }
    }
    @SubscribeEvent
    public static void chunkUnload(net.minecraftforge.event.level.ChunkEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
            ClientCircuitBlockIndex.onChunkUnloaded(level, event.getChunk().getPos());
        }
    }
    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == Minecraft.getInstance().level) {
            ClientCompiledElements.onLevelChanged(null, "");
            ClientCircuitBlockIndex.clear();
            ClientHoveredCircuit.clear();
        }
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientCompiledElements.clear(); ClientCircuitBlockIndex.clear(); ClientHoveredCircuit.reset(); }
}
