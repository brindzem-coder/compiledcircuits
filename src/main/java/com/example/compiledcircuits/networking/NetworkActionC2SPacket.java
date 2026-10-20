package com.example.compiledcircuits.networking;
import com.example.compiledcircuits.network.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import java.util.function.Supplier;
public class NetworkActionC2SPacket {
    public enum Action {

        HIGHLIGHT,

        RENAME_NETWORK,
        MOVE_NETWORK,
        DECOMPILE,

        CREATE_FOLDER,
        RENAME_FOLDER,
        DELETE_FOLDER,
        MOVE_FOLDER
    }

    private final Action action;
    private final int networkId, targetId;
    private final String value;
    private HighlightProtocol.Request highlight = HighlightProtocol.LEGACY;
    public NetworkActionC2SPacket(Action action, int id, String value, HighlightProtocol.Request request) { this(action,id,0,value); highlight=request; }
    public NetworkActionC2SPacket(Action action, int id, int target, String value) {
        if (action == null || id < 0 || (id == 0 && action != Action.CREATE_FOLDER) || target < 0
                || value == null || value.length() > OperationLimits.NAME) throw new IllegalArgumentException("Invalid action fields");
        this.action = action; networkId = id; targetId = target; this.value = value;
    }
    public NetworkActionC2SPacket(Action action, int id, String value) { this(action,id,0,value); }
    public static void encode(NetworkActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action); buf.writeInt(packet.networkId); buf.writeInt(packet.targetId); buf.writeUtf(packet.value, OperationLimits.NAME);
        if(packet.action==Action.HIGHLIGHT)NetworkHighlightS2CPacket.writeRequest(packet.highlight,buf);
    }
    public static NetworkActionC2SPacket decode(FriendlyByteBuf buf) {
        var p=new NetworkActionC2SPacket(buf.readEnum(Action.class),buf.readInt(),buf.readInt(),buf.readUtf(OperationLimits.NAME));
        if(p.action==Action.HIGHLIGHT)p.highlight=NetworkHighlightS2CPacket.readRequest(buf);
        return p;
    }
    /** Called inside enqueueWork, so current permissions are evaluated at execution. */
    public static NetworkOperations.Result execute(NetworkActionC2SPacket packet, ServerPlayer player) {
        var action = switch(packet.action) {
            case MOVE_NETWORK -> NetworkOperations.Action.MOVE_NETWORKS;
            case MOVE_FOLDER -> NetworkOperations.Action.MOVE_FOLDERS;
            case DELETE_FOLDER -> NetworkOperations.Action.DELETE_FOLDERS;
            default -> NetworkOperations.Action.valueOf(packet.action.name());
        };
        return NetworkOperations.execute(player == null ? null : player.createCommandSourceStack(), action,
                List.of(packet.networkId), packet.targetId, packet.value);
    }
    public static void handle(NetworkActionC2SPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        if (context.getDirection() != net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER) {
            context.setPacketHandled(true); return;
        }
        context.enqueueWork(() -> {
            var player = context.getSender(); if (player == null) return;
            if(packet.action==Action.HIGHLIGHT){HighlightSync.request(player,packet.highlight,List.of(packet.networkId));return;}
            var result = execute(packet, player);
            NetworkOperations.reply(player.createCommandSourceStack(), result);
            if (!result.success()) return;
            NetworkGuiSync.sendList(player);
        });
        context.setPacketHandled(true);
    }
}
