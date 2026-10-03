package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BrokenElementSync {
    private BrokenElementSync() {}

    public static Set<BlockPos> collectBrokenPositions(NetworkSavedData data, String dimensionId) {
        Set<BlockPos> result = new LinkedHashSet<>();
        for (CompiledNetwork network : data.getNetworks()) {
            if (!network.getDimension().equals(dimensionId)) continue;
            for (BrokenCircuitElement broken : network.getBrokenElements()) {
                CompiledCircuitElement element = network.getElement(broken.getElementId());
                if (element != null) result.add(element.getPos());
            }
        }
        return result;
    }

    public static void sendToPlayer(ServerPlayer player) { DamageSync.ensure(player); }
    public static void broadcastDimension(ServerLevel level) { /* The global ledger owns delivery. */ }
    public static void syncRemovedNetworks(MinecraftServer server, Collection<CompiledNetwork> removed) {
        // NetworkSavedData/NetworkRuntime removal already records tombstones in the ledger.
    }
}
