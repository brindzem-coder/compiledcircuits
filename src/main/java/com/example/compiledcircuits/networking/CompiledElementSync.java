package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.NetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Server-thread full-dimension snapshots. Membership mutation hooks are integrated in Part F. */
public final class CompiledElementSync {
    private static long nextSnapshot;
    private static long snapshotBytes, snapshotBuildNanos;
    public static long getSnapshotBytes() { return snapshotBytes; }
    public static long getSnapshotBuildNanos() { return snapshotBuildNanos; }
    private static final Set<String> dirty = new HashSet<>();
    private static final Map<UUID, ServerPlayer> requested = new LinkedHashMap<>();
    private static final Map<UUID, Delivery> deliveries = new LinkedHashMap<>();
    private static final class Delivery {
        final ServerPlayer player;
        final List<CompiledElementPositionsS2CPacket> parts;
        int next;
        Delivery(ServerPlayer player, List<CompiledElementPositionsS2CPacket> parts) { this.player = player; this.parts = parts; }
    }
    private CompiledElementSync() {}
    public static void markDimensionDirty(String dimension) { dirty.add(dimension); }
    public static void sendSnapshot(ServerPlayer player) { requested.put(player.getUUID(), player); }
    public static void clear() { dirty.clear(); requested.clear(); deliveries.clear(); nextSnapshot = 0; snapshotBytes = 0; snapshotBuildNanos = 0; }

    public static List<CompiledElementPositionsS2CPacket> buildSnapshot(NetworkSavedData data, String dimension, long id) {
        long started = System.nanoTime();
        var entries = new ArrayList<CompiledElementPositionsS2CPacket.Entry>();
        var seen = new HashSet<BlockPos>();
        for (var network : data.getNetworks()) {
            if (!network.getDimension().equals(dimension)) continue;
            for (var element : network.getElements()) {
                if (entries.size() >= CompiledElementPositionsS2CPacket.MAX_ENTRIES) throw new IllegalArgumentException("Membership exceeds one million entries");
                if (!seen.add(element.getPos())) throw new IllegalArgumentException("Duplicate membership at " + element.getPos());
                entries.add(new CompiledElementPositionsS2CPacket.Entry(network.getId(), element.getPos()));
            }
        }
        int count = Math.max(1, (entries.size() + CompiledElementPositionsS2CPacket.ENTRIES_PER_PART - 1)
                / CompiledElementPositionsS2CPacket.ENTRIES_PER_PART);
        var parts = new ArrayList<CompiledElementPositionsS2CPacket>(count);
        var dim = new ResourceLocation(dimension);
        for (int index = 0; index < count; index++) {
            int start = index * CompiledElementPositionsS2CPacket.ENTRIES_PER_PART;
            parts.add(new CompiledElementPositionsS2CPacket(dim, id, index, count,
                    entries.subList(start, Math.min(entries.size(), start + CompiledElementPositionsS2CPacket.ENTRIES_PER_PART))));
        }
        snapshotBytes = parts.stream().mapToLong(CompiledElementPositionsS2CPacket::encodedBytes).sum();
        snapshotBuildNanos = System.nanoTime() - started;
        return List.copyOf(parts);
    }
    public static void flushDirty(MinecraftServer server) {
        Map<String, List<ServerPlayer>> viewers = new LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String dim = player.serverLevel().dimension().location().toString();
            if (dirty.contains(dim) || requested.containsKey(player.getUUID())) viewers.computeIfAbsent(dim, key -> new ArrayList<>()).add(player);
        }
        requested.clear(); dirty.clear();
        for (var entry : viewers.entrySet()) {
            try {
                var parts = buildSnapshot(NetworkSavedData.get(server), entry.getKey(), ++nextSnapshot);
                for (var player : entry.getValue()) deliveries.put(player.getUUID(), new Delivery(player, parts));
            } catch (IllegalArgumentException invalid) {
                for (var player : entry.getValue()) deliveries.remove(player.getUUID());
                org.slf4j.LoggerFactory.getLogger(CompiledElementSync.class).warn("Cannot sync compiled membership for {}: {}", entry.getKey(), invalid.getMessage());
            }
        }
        // Up to four <=64 KiB parts per player per tick; no block mutation or GUI dependency.
        var iterator = deliveries.entrySet().iterator();
        while (iterator.hasNext()) {
            var delivery = iterator.next().getValue();
            if (server.getPlayerList().getPlayer(delivery.player.getUUID()) != delivery.player
                    || !delivery.player.serverLevel().dimension().location().equals(delivery.parts.get(0).dimension())) {
                iterator.remove(); continue;
            }
            for (int i = 0; i < 4 && delivery.next < delivery.parts.size(); i++) {
                ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> delivery.player), delivery.parts.get(delivery.next++));
            }
            if (delivery.next == delivery.parts.size()) iterator.remove();
        }
    }
}
