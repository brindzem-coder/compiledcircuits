package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class NetworkIntegrityManager {
    // Accessed only from server events; cleared when the server stops.
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PENDING_CHECKS = new HashMap<>();

    private NetworkIntegrityManager() {}

    public static void scheduleCheck(ServerLevel level, BlockPos pos) {
        PENDING_CHECKS.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    public static void clearPending() {
        PENDING_CHECKS.clear();
    }

    public static void processPending(MinecraftServer server) {
        if (PENDING_CHECKS.isEmpty()) return;
        Map<ResourceKey<Level>, Set<BlockPos>> pending = new HashMap<>(PENDING_CHECKS);
        PENDING_CHECKS.clear();
        for (var entry : pending.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) continue;
            for (BlockPos pos : entry.getValue()) checkPosition(level, pos);
        }
    }

    public static void checkPosition(ServerLevel level, BlockPos pos) {
        NetworkSavedData data = NetworkSavedData.get(level.getServer());
        NetworkSavedData.ElementLocation location =
                data.findElementLocation(level.dimension().location().toString(), pos);
        if (location == null || !level.hasChunkAt(pos)) return;

        CompiledNetwork network = location.network();
        CompiledCircuitElement element = location.element();
        String actualBlockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        boolean repaired = element.getBlockId().equals(actualBlockId);
        boolean wasDamaged = network.isDamaged();
        boolean changed = repaired
                ? network.markRepaired(element.getId())
                : network.markBroken(new BrokenCircuitElement(element.getId(), actualBlockId, level.getGameTime()));
        if (!changed) return;

        data.setDirty();
        com.example.compiledcircuits.networking.NetworkGuiSync.broadcastBrokenList(level.getServer());
        com.example.compiledcircuits.networking.BrokenElementSync.broadcastDimension(level);
        if (!wasDamaged && network.isDamaged()) {
            NetworkRuntime.networkBecameDamaged(level, network);
        } else if (wasDamaged && !network.isDamaged()) {
            NetworkRuntime.networkBecameHealthy(level, network);
        }
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal(formatMessage(network, element, repaired)), false);
    }

    private static String formatMessage(CompiledNetwork network, CompiledCircuitElement element, boolean repaired) {
        return "[CompiledCircuits] Circuit " + (repaired ? "repaired: " : "damaged: ")
                + network.getName() + " (#" + network.getId() + ") | "
                + getElementDisplayName(element) + " @ " + formatPos(element.getPos());
    }

    private static String getElementDisplayName(CompiledCircuitElement element) {
        String blockId = element.getBlockId();
        String path = blockId.substring(blockId.indexOf(':') + 1);
        StringBuilder result = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
