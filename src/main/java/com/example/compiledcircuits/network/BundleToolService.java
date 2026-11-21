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
        if (!(stack.getItem() instanceof BundlePortItem) && !(stack.getItem() instanceof com.example.compiledcircuits.item.KeyboardPortItem) && !(stack.getItem() instanceof com.example.compiledcircuits.item.ScreenPortItem) && !(stack.getItem() instanceof com.example.compiledcircuits.item.BundleMappingItem)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("bundle.compiledcircuits.hold")); return 0;
        }
        if(stack.getItem() instanceof com.example.compiledcircuits.item.BundleMappingItem item && item.kind()==9){
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("mapping.compiledcircuits.receiver_hint"));return 0;
        }
        var session = new Session(hand, stack, UUID.randomUUID()); sessions.put(player.getUUID(), session);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BundleEditorS2CPacket(session.token, stack.getItem() instanceof com.example.compiledcircuits.item.BundleMappingItem ? com.example.compiledcircuits.item.BundleMappingItem.rules(stack) : stack.getItem() instanceof com.example.compiledcircuits.item.KeyboardPortItem ? com.example.compiledcircuits.item.KeyboardPortItem.binding(stack).encode() : BundlePortItem.channel(stack), stack.getItem() instanceof com.example.compiledcircuits.item.KeyboardPortItem,stack.getItem() instanceof com.example.compiledcircuits.item.BundleMappingItem));
        return 1;
    }
    public static void finish(ServerPlayer player, UUID token, String id, boolean cancel) {
        var session = sessions.get(player.getUUID());
        if (session == null || !session.token.equals(token)) return;
        sessions.remove(player.getUUID());
        if (cancel || !NetworkOperations.canModify(player) || player.getItemInHand(session.hand) != session.stack) return;
        boolean keyboard = session.stack.getItem() instanceof com.example.compiledcircuits.item.KeyboardPortItem;
        boolean mapping=session.stack.getItem() instanceof com.example.compiledcircuits.item.BundleMappingItem;
        try { id = mapping?ChannelMapping.parse(id).text():keyboard ? KeyboardBinding.parse(id).encode() : session.stack.getItem() instanceof com.example.compiledcircuits.item.ScreenPortItem ? ScreenColors.base(id) : WireIds.normalize(id); } catch (IllegalArgumentException invalid) { return; }
        session.stack.getOrCreateTag().putString(mapping ? "ChannelMapping" : keyboard ? "KeyboardBinding" : "ChannelId", id); player.containerMenu.broadcastChanges();
    }
    public static void forget(ServerPlayer player) { sessions.remove(player.getUUID()); }
    public static void clear() { sessions.clear(); }
}
