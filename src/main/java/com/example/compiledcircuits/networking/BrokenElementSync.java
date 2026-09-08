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

    public static void sendToPlayer(ServerPlayer player) {
        String dimension = player.serverLevel().dimension().location().toString();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new BrokenElementsS2CPacket(dimension,
                        collectBrokenPositions(NetworkSavedData.get(player.getServer()), dimension)));
    }

    public static void broadcastDimension(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        BrokenElementsS2CPacket packet = new BrokenElementsS2CPacket(dimension,
                collectBrokenPositions(NetworkSavedData.get(level.getServer()), dimension));
        ModNetworking.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    public static void syncRemovedNetworks(MinecraftServer server, Collection<CompiledNetwork> removed) {
        if (!removed.isEmpty()) NetworkGuiSync.broadcastBrokenList(server);
        Set<String> dimensions = new HashSet<>();
        for (CompiledNetwork network : removed) dimensions.add(network.getDimension());
        for (ServerLevel level : server.getAllLevels()) {
            if (dimensions.contains(level.dimension().location().toString())) broadcastDimension(level);
        }
    }
}
