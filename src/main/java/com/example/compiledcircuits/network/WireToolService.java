package com.example.compiledcircuits.network;

import com.example.compiledcircuits.item.IdWireItem;
import com.example.compiledcircuits.networking.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** A session binds Done to the exact stack and hand used to open the editor. */
public final class WireToolService {
    private record Session(InteractionHand hand, net.minecraft.world.item.ItemStack stack, UUID token) {}
    private static final Map<UUID, Session> sessions = new HashMap<>();
    public static int open(ServerPlayer player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof IdWireItem)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("wire.compiledcircuits.hold")); return 0;
        }
        var session = new Session(hand, stack, UUID.randomUUID()); sessions.put(player.getUUID(), session);
        var data = WireCatalog.get(player.getServer());
        WireCatalogSync.send(player);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WireEditorS2CPacket(session.token, IdWireItem.id(stack), data.color(IdWireItem.id(stack), IdWireItem.color(stack))));
        return 1;
    }
    public static void finish(ServerPlayer player, UUID token, String id, int color, boolean cancel) {
        var session = sessions.get(player.getUUID());
        if (session == null || !session.token.equals(token)) return;
        sessions.remove(player.getUUID());
        if (cancel || !NetworkOperations.canModify(player) || player.getItemInHand(session.hand) != session.stack) return;
        try { id = WireIds.normalize(id); } catch (IllegalArgumentException invalid) { return; }
        var data = WireCatalog.get(player.getServer());
        if (data.isColorLocked(id) && data.color(id, color) != (color & 0xffffff))
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("wire.compiledcircuits.locked"));
        data.recolor(id, color); // Server rejects recoloring compiled IDs, even from a stale editor.
        IdWireItem.configure(session.stack, id, data.color(id, color));
        player.containerMenu.broadcastChanges();
    }
    public static void forget(ServerPlayer player) { sessions.remove(player.getUUID()); BundleToolService.forget(player); }
    public static void clear() { sessions.clear(); BundleToolService.clear(); }
}
