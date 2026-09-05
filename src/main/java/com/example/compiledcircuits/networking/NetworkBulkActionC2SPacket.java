package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.CompiledNetwork;
import com.example.compiledcircuits.network.NetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.function.Supplier;

public class NetworkBulkActionC2SPacket {
    public enum BulkAction {
        HIGHLIGHT_NETWORKS, MOVE_NETWORKS, DECOMPILE_NETWORKS,
        MOVE_FOLDERS, DELETE_FOLDERS
    }

    private static final int MAX_BULK_IDS = 100_000;
    private final BulkAction action;
    private final List<Integer> ids;
    private final int targetFolderId;

    public NetworkBulkActionC2SPacket(BulkAction action, Collection<Integer> ids, int targetFolderId) {
        if (ids.size() > MAX_BULK_IDS) throw new IllegalArgumentException("Too many IDs");
        this.action = action;
        this.ids = List.copyOf(new LinkedHashSet<>(ids));
        this.targetFolderId = targetFolderId;
    }

    public static void encode(NetworkBulkActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeVarInt(packet.ids.size());
        for (int id : packet.ids) buf.writeVarInt(id);
        buf.writeVarInt(packet.targetFolderId);
    }

    public static NetworkBulkActionC2SPacket decode(FriendlyByteBuf buf) {
        BulkAction action = buf.readEnum(BulkAction.class);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_BULK_IDS || size >= buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid bulk ID count");
        }
        List<Integer> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) ids.add(buf.readVarInt());
        return new NetworkBulkActionC2SPacket(action, ids, buf.readVarInt());
    }

    public static void handle(NetworkBulkActionC2SPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            NetworkSavedData data = NetworkSavedData.get(player.getServer());
            if (packet.ids.isEmpty() || packet.ids.size() > MAX_BULK_IDS) {
                fail(player);
                return;
            }
            boolean success;
            switch (packet.action) {
                case HIGHLIGHT_NETWORKS -> {
                    List<CompiledNetwork> networks = new ArrayList<>();
                    for (int id : packet.ids) {
                        CompiledNetwork network = data.getNetwork(id);
                        if (network == null) {
                            fail(player);
                            return;
                        }
                        networks.add(network);
                    }
                    ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            buildHighlight(networks));
                    return;
                }
                case MOVE_NETWORKS -> success = data.moveNetworks(packet.ids, packet.targetFolderId);
                case MOVE_FOLDERS -> success = data.moveFolders(packet.ids, packet.targetFolderId);
                case DELETE_FOLDERS -> success = data.deleteFolders(packet.ids);
                case DECOMPILE_NETWORKS -> {
                    List<CompiledNetwork> removed = data.removeNetworks(packet.ids);
                    success = !removed.isEmpty();
                    // Remove the entire selection before notifying any neighbors.
                    for (CompiledNetwork network : removed) {
                        NetworkActionC2SPacket.updateRemovedNetworkOutputs(player, network);
                    }
                }
                default -> throw new IllegalStateException("Unknown bulk action");
            }
            if (!success) {
                fail(player);
                return;
            }
            NetworkGuiSync.sendList(player);
        });
        context.setPacketHandled(true);
    }

    static NetworkHighlightS2CPacket buildHighlight(Collection<CompiledNetwork> networks) {
        Set<BlockPos> wires = new HashSet<>();
        Set<BlockPos> inputs = new HashSet<>();
        Set<BlockPos> outputs = new HashSet<>();
        for (CompiledNetwork network : networks) {
            wires.addAll(network.getWires());
            inputs.addAll(network.getInputs());
            outputs.addAll(network.getOutputs());
        }
        return new NetworkHighlightS2CPacket(wires, inputs, outputs);
    }

    private static void fail(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(
                "Could not apply bulk action. Nothing changed. Check that items exist, folders are empty for deletion, "
                        + "and the destination has no name conflicts or folder cycles."));
        NetworkGuiSync.sendList(player);
    }
}
