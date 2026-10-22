package com.example.compiledcircuits.network;

import com.example.compiledcircuits.item.BundlePortItem;
import com.example.compiledcircuits.networking.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

public final class BundleToolService {
    private record Session(InteractionHand hand, net.minecraft.world.item.ItemStack stack, UUID token) {}
    private static final Map<UUID, Session> sessions = new HashMap<>();
    public static int open(ServerPlayer player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BundlePortItem)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("bundle.compiledcircuits.hold")); return 0;
        }
        var session = new Session(hand, stack, UUID.randomUUID()); sessions.put(player.getUUID(), session);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BundleEditorS2CPacket(session.token, BundlePortItem.channel(stack)));
        return 1;
    }
    public static void finish(ServerPlayer player, UUID token, String id, boolean cancel) {
        var session = sessions.get(player.getUUID());
        if (session == null || !session.token.equals(token)) return;
        sessions.remove(player.getUUID());
        if (cancel || !NetworkOperations.canModify(player) || player.getItemInHand(session.hand) != session.stack) return;
        try { id = WireIds.normalize(id); } catch (IllegalArgumentException invalid) { return; }
        session.stack.getOrCreateTag().putString("ChannelId", id); player.containerMenu.broadcastChanges();
    }
    public static void forget(ServerPlayer player) { sessions.remove(player.getUUID()); }
    public static void clear() { sessions.clear(); }
}
