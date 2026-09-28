package com.example.compiledcircuits.event;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.network.NetworkSavedData;
import com.example.compiledcircuits.networking.CompiledElementSync;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Notify loaded neighbors after saved-data isolation, never force chunks to load. */
@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID)
public final class MembershipQuarantineEvents {
    private record LoadedChunk(ServerLevel level, long pos) {}
    private static final java.util.Queue<LoadedChunk> loaded = new java.util.concurrent.ConcurrentLinkedQueue<>();
    @SubscribeEvent public static void chunk(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) loaded.add(new LoadedChunk(level, event.getChunk().getPos().toLong()));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var data = NetworkSavedData.get(event.getServer());
        Map<ServerLevel, Set<BlockPos>> pending = new HashMap<>();
        if (data.consumeReservationsChanged()) for (var level : event.getServer().getAllLevels()) {
            String dimension = level.dimension().location().toString();
            CompiledElementSync.markDimensionDirty(dimension);
            pending.computeIfAbsent(level, key -> new HashSet<>()).addAll(data.getBlockedPositions(dimension));
        }
        LoadedChunk chunk;
        while ((chunk = loaded.poll()) != null) {
            if (chunk.level.getServer() != event.getServer()) continue;
            pending.computeIfAbsent(chunk.level, key -> new HashSet<>()).addAll(data.getBlockedPositions(
                    chunk.level.dimension().location().toString(), chunk.pos));
        }
        pending.forEach((level, positions) -> positions.forEach(pos -> {
            if (!level.hasChunkAt(pos)) return;
            for (var direction : net.minecraft.core.Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (level.hasChunkAt(neighbor)) level.neighborChanged(neighbor, ModBlocks.OUTPUT_ENDPOINT.get(), pos);
            }
        }));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { loaded.clear(); }
}
