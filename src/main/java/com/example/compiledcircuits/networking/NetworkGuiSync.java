package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.CompiledNetwork;
import com.example.compiledcircuits.network.CircuitFolder;
import com.example.compiledcircuits.network.NetworkSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NetworkGuiSync {

    private NetworkGuiSync() {
    }

    public static NetworkListS2CPacket buildListPacket(
            ServerPlayer player
    ) {

        NetworkSavedData savedData =
                NetworkSavedData.get(
                        player.getServer()
                );

        if((long)savedData.getNetworks().size()+savedData.getFolders().size()>NetworkListS2CPacket.MAX_RECORDS)return NetworkListS2CPacket.unavailable();
        List<NetworkListS2CPacket.Entry> entries =
                new ArrayList<>();

        for (CompiledNetwork network
                : savedData.getNetworks()) {

            entries.add(
                    new NetworkListS2CPacket.Entry(
                            network.getId(),
                            network.getName(),
                            network.getFolderId(),
                            network.getDimension(),
                            network.getEffectiveSignal() > 0,
                            network.getWires().size(),
                            network.getInputs().size(),
                            network.getOutputs().size()
                    )
            );
        }

        List<NetworkListS2CPacket.FolderEntry> folders =
                new ArrayList<>();

        for (CircuitFolder folder
                : savedData.getFolders()) {

            folders.add(
                    new NetworkListS2CPacket.FolderEntry(
                            folder.getId(),
                            folder.getName(),
                            folder.getParentId()
                    )
            );
        }

        entries.sort(
                Comparator.comparingInt(
                        NetworkListS2CPacket.Entry::id
                )
        );

        return NetworkListS2CPacket.fits(entries,folders)?new NetworkListS2CPacket(entries, folders):NetworkListS2CPacket.unavailable();
    }

    public static List<BrokenElementListS2CPacket.Entry> buildBrokenEntries(ServerPlayer player) {
        return buildBrokenEntries(NetworkSavedData.get(player.getServer()));
    }

    public static List<BrokenElementListS2CPacket.Entry> buildBrokenEntries(NetworkSavedData data) {
        List<BrokenElementListS2CPacket.Entry> result = new ArrayList<>();
        for (CompiledNetwork network : data.getNetworks()) {
            for (var broken : network.getBrokenElements()) {
                var element = network.getElement(broken.getElementId());
                if (element == null) continue;
                result.add(new BrokenElementListS2CPacket.Entry(network.getId(), network.getName(),
                        network.getFolderId(), element.getId(), network.getDimension(), element.getPos(),
                        element.getType(), element.getBlockId(), broken.getActualBlockId(), broken.getDetectedAt()));
            }
        }
        result.sort(Comparator.comparing(BrokenElementListS2CPacket.Entry::networkName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(BrokenElementListS2CPacket.Entry::elementId)
                .thenComparingInt(BrokenElementListS2CPacket.Entry::networkId));
        return result;
    }

    public static void sendBrokenList(ServerPlayer player) { DamageSync.ensure(player); }

    /** Compatibility entry point: the ledger already observes every supported mutation. */
    public static void broadcastBrokenList(net.minecraft.server.MinecraftServer server) { }

    public static void sendList(ServerPlayer player) {
        sendBrokenList(player);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), buildListPacket(player));
    }

    public static void sendListAndNavigate(ServerPlayer player, int networkId) {
        sendBrokenList(player);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenNetworkManagerAtS2CPacket(buildListPacket(player), networkId));
    }
}
