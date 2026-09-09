package com.example.compiledcircuits.client;

import com.example.compiledcircuits.networking.CompiledElementPositionsS2CPacket;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Main-thread logical membership. UNKNOWN is never equivalent to uncompiled. */
public final class ClientCompiledElements {
    private static String dimension = "";
    private static Object levelIdentity;
    private static boolean ready;
    private static long revision, latestSnapshot;
    private static Map<BlockPos, Integer> byPos = Map.of();
    private static Map<Integer, Set<BlockPos>> byNetwork = Map.of();
    private static Assembly staging;
    private static Complete complete;
    private record Complete(String dimension, Map<BlockPos, Integer> positions, Map<Integer, Set<BlockPos>> networks) {}
    private static final class Assembly {
        final String dimension;
        final long id, started;
        final List<CompiledElementPositionsS2CPacket.Entry>[] parts;
        int count, entries;
        long bytes;
        @SuppressWarnings("unchecked")
        Assembly(CompiledElementPositionsS2CPacket part, long now) {
            dimension = part.dimension().toString(); id = part.snapshotId(); started = now;
            parts = new List[part.partCount()];
        }
    }
    private ClientCompiledElements() {}
    public static boolean isReadyFor(String id) { return ready && dimension.equals(id); }
    public static long getRevision() { return revision; }
    public static Integer networkIdAt(BlockPos pos) { return byPos.get(pos); }
    public static Set<BlockPos> positionsForNetwork(int id) { return byNetwork.getOrDefault(id, Set.of()); }

    public static void onLevelChanged(Object level, String id) {
        if (levelIdentity == level && dimension.equals(id)) return;
        levelIdentity = level; dimension = id;
        ready = false; byPos = Map.of(); byNetwork = Map.of(); revision++;
        // A complete current-connection snapshot may arrive before level replacement.
        commitIfActive();
    }
    public static void clear() {
        levelIdentity = null; dimension = ""; ready = false; byPos = Map.of(); byNetwork = Map.of();
        staging = null; complete = null; latestSnapshot = 0; revision++;
    }
    public static void tick(long now) {
        if (staging != null && now - staging.started >= CompiledElementPositionsS2CPacket.STAGING_TIMEOUT_NANOS) reject("Timed out");
    }
    private static void reject(String reason) {
        staging = null;
        org.slf4j.LoggerFactory.getLogger(ClientCompiledElements.class).warn("Compiled membership snapshot rejected: {}", reason);
    }
    public static void accept(CompiledElementPositionsS2CPacket part, long now) {
        tick(now);
        if (part.snapshotId() < latestSnapshot) return;
        if (part.snapshotId() > latestSnapshot) {
            latestSnapshot = part.snapshotId(); staging = new Assembly(part, now);
        }
        if (staging == null) return; // already completed/rejected/expired ID
        if (!staging.dimension.equals(part.dimension().toString()) || staging.parts.length != part.partCount()) {
            reject("Inconsistent parts"); return;
        }
        if (staging.parts[part.partIndex()] != null) return;
        staging.entries += part.entries().size(); staging.bytes += part.estimatedBytes();
        if (staging.entries > CompiledElementPositionsS2CPacket.MAX_ENTRIES || staging.bytes > CompiledElementPositionsS2CPacket.MAX_BYTES) {
            reject("Assembly budget exceeded"); return;
        }
        staging.parts[part.partIndex()] = part.entries(); staging.count++;
        if (staging.count != staging.parts.length) return;
        Map<BlockPos, Integer> positions = new HashMap<>();
        Map<Integer, Set<BlockPos>> networks = new HashMap<>();
        for (var entries : staging.parts) for (var entry : entries) {
            if (positions.putIfAbsent(entry.pos(), entry.networkId()) != null) { reject("Duplicate position across parts"); return; }
            networks.computeIfAbsent(entry.networkId(), key -> new HashSet<>()).add(entry.pos());
        }
        networks.replaceAll((key, value) -> Collections.unmodifiableSet(value));
        complete = new Complete(staging.dimension, Collections.unmodifiableMap(positions), Collections.unmodifiableMap(networks));
        staging = null;
        commitIfActive();
    }
    private static void commitIfActive() {
        if (levelIdentity == null || complete == null || !complete.dimension.equals(dimension)) return;
        byPos = complete.positions; byNetwork = complete.networks;
        ready = true; revision++;
    }
}
