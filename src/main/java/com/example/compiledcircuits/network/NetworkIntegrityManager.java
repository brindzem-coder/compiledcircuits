package com.example.compiledcircuits.network;

import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
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
    private static Boolean exactMode;
    public static boolean exactIntegrityEnabled() {
        if (exactMode == null) exactMode = com.example.compiledcircuits.config.ServerConfig.EXACT_BLOCK_STATE_INTEGRITY.get();
        return exactMode;
    }
    public static void audit(MinecraftServer server) { NetworkSavedData.get(server).integrity().audit(server); }
    private NetworkIntegrityManager() {}
    public static void scheduleCheck(ServerLevel level, BlockPos pos) {
        if(level.captureBlockSnapshots || level.restoringBlockSnapshots)return;
        PerformanceDiagnostics.add("pending.scheduleCalls", 1);
        var data=NetworkSavedData.get(level.getServer());
        var location=data.findElementLocation(level.dimension().location().toString(),pos);
        if(location==null){PerformanceDiagnostics.add("pending.unownedSkipped",1);return;}
        data.integrity().schedule(level,location);
    }
    public static void invalidateDimension(ServerLevel level) { NetworkSavedData.get(level.getServer()).integrity().invalidateDimension(level); }
    public static void clearPending() {
        exactMode=null;
        // Test/session compatibility; server-owned schedulers are weakly tracked by saved data.
        NetworkSavedData.clearIntegritySchedulers();
    }
    public static void processPending(MinecraftServer server) {
        long start=PerformanceDiagnostics.begin();PerformanceDiagnostics.add("processPending.calls",1);
        try{NetworkSavedData.get(server).integrity().tick(server);}
        finally{PerformanceDiagnostics.elapsed("processPending",start);}
    }
    public static void checkPosition(ServerLevel level, BlockPos pos) {
        NetworkSavedData data = NetworkSavedData.get(level.getServer());
        NetworkSavedData.ElementLocation location =
                data.findElementLocation(level.dimension().location().toString(), pos);
        if (location == null || !NetworkRuntime.isChunkAvailable(level, RuntimeSignalReader.chunk(pos))) return;

        checkElement(level, data, location.network(), location.element());
    }

    static boolean checkElement(ServerLevel level, NetworkSavedData data, CompiledNetwork network, CompiledCircuitElement element) {
        if (level.captureBlockSnapshots || level.restoringBlockSnapshots) return false;
        BlockPos pos = element.getPos();
        var actual = level.getBlockState(pos);
        String actualBlockId = BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString();
        element.logUnresolved(network.getId());
        boolean repaired = CompiledBlockStateMatcher.match(element, actual, exactIntegrityEnabled())
                == CompiledBlockStateMatcher.Match.MATCH;
        boolean wasDamaged = network.isDamaged();
        boolean changed = repaired
                ? network.markRepaired(element.getId())
                : network.markBroken(new BrokenCircuitElement(element.getId(), actualBlockId, level.getGameTime()));
        if (!changed) {
            if(!repaired && network.updateBrokenActual(element.getId(),actualBlockId))data.setDirty();
            return true;
        }

        data.setDirty();
        DamageNotifications.changed(level, network.getId(), repaired);
        if (!wasDamaged && network.isDamaged()) {
            NetworkRuntime.networkBecameDamaged(level, network);
        } else if (wasDamaged && !network.isDamaged()) {
            NetworkRuntime.networkBecameHealthy(level, network);
        }
        return true;
    }
}
