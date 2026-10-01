package com.example.compiledcircuits.networking;
import com.example.compiledcircuits.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import java.util.function.Supplier;
public class NetworkBulkActionC2SPacket {
    public enum BulkAction { HIGHLIGHT_NETWORKS, MOVE_NETWORKS, DECOMPILE_NETWORKS, MOVE_FOLDERS, DELETE_FOLDERS, REPAIR_NETWORKS }
    private final BulkAction action;
    private final List<Integer> ids;
    private final int targetFolderId;
    public NetworkBulkActionC2SPacket(BulkAction action, Collection<Integer> ids, int target) {
        if (action == null || ids == null || ids.isEmpty() || ids.size() > OperationLimits.IDS || target < 0)
            throw new IllegalArgumentException("Invalid bulk request");
        for (Integer id : ids) if (id == null || id <= 0) throw new IllegalArgumentException("Invalid target ID");
        this.action = action; this.ids = List.copyOf(new LinkedHashSet<>(ids)); targetFolderId = target;
    }
    public static void encode(NetworkBulkActionC2SPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action); buf.writeVarInt(p.ids.size()); for (int id:p.ids) buf.writeVarInt(id); buf.writeVarInt(p.targetFolderId);
    }
    public static NetworkBulkActionC2SPacket decode(FriendlyByteBuf buf) {
        var action = buf.readEnum(BulkAction.class); int size = buf.readVarInt();
        if (size <= 0 || size > OperationLimits.IDS || size >= buf.readableBytes()) throw new IllegalArgumentException("Invalid raw bulk size");
        List<Integer> ids = new ArrayList<>(size); for(int i=0;i<size;i++) ids.add(buf.readVarInt());
        return new NetworkBulkActionC2SPacket(action, ids, buf.readVarInt());
    }
    public static NetworkOperations.Result execute(NetworkBulkActionC2SPacket packet, ServerPlayer player) {
        var action = switch(packet.action) {
            case HIGHLIGHT_NETWORKS -> NetworkOperations.Action.HIGHLIGHT;
            case DECOMPILE_NETWORKS -> NetworkOperations.Action.DECOMPILE;
            case REPAIR_NETWORKS -> NetworkOperations.Action.REPAIR;
            default -> NetworkOperations.Action.valueOf(packet.action.name());
        };
        return NetworkOperations.execute(player == null ? null : player.createCommandSourceStack(), action, packet.ids, packet.targetFolderId, "");
    }
    public static void handle(NetworkBulkActionC2SPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        if (context.getDirection() != net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER) {
            context.setPacketHandled(true); return;
        }
        context.enqueueWork(() -> {
            var player = context.getSender(); if (player == null) return;
            var result = execute(packet,player); NetworkOperations.reply(player.createCommandSourceStack(),result);
            if (!result.success()) return;
            if (packet.action == BulkAction.HIGHLIGHT_NETWORKS) ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),buildHighlight(result.networks()));
            else if (packet.action != BulkAction.REPAIR_NETWORKS) NetworkGuiSync.sendList(player);
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

}
