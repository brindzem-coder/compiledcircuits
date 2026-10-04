package com.example.compiledcircuits.client;

import com.example.compiledcircuits.networking.NetworkListS2CPacket;
import net.minecraft.client.Minecraft;
import com.example.compiledcircuits.networking.OpenNetworkManagerAtS2CPacket;
import com.example.compiledcircuits.networking.CompileNamedC2SPacket;
import com.example.compiledcircuits.networking.ModNetworking;

import com.example.compiledcircuits.networking.NetworkHighlightS2CPacket;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void handleCompiledElements(com.example.compiledcircuits.networking.CompiledElementPositionsS2CPacket packet,
                                              net.minecraft.network.Connection connection) {
        ClientMembershipSync.accept(packet,connection);
    }

    public static void handleDamage(com.example.compiledcircuits.networking.DamagePartS2CPacket packet,net.minecraft.network.Connection connection){ClientDamageSync.accept(packet,connection);}

    public static void handleBrokenElementList(com.example.compiledcircuits.networking.BrokenElementListS2CPacket packet) {
        ClientBrokenElementList.setEntries(packet.getEntries());
        java.util.Set<ClientBrokenElements.FocusedBrokenPos> valid = new java.util.HashSet<>();
        for (var entry : packet.getEntries()) {
            valid.add(new ClientBrokenElements.FocusedBrokenPos(entry.dimension(), entry.pos()));
        }
        ClientBrokenElements.retainFocused(valid);
        if (Minecraft.getInstance().screen instanceof NetworkManagerScreen screen) screen.onBrokenListUpdated();
    }

    public static void handleBrokenElements(com.example.compiledcircuits.networking.BrokenElementsS2CPacket packet) {
        ClientBrokenElements.setBroken(packet.getDimension(), packet.getPositions());
    }

    public static void openCompileNameScreen() {
        Minecraft.getInstance().setScreen(new NetworkTextEditScreen(
                null, "Compile Network", "Network name:", "",
                name -> ModNetworking.CHANNEL.sendToServer(new CompileNamedC2SPacket(name)), 64));
    }

    public static void openNetworkManagerAt(OpenNetworkManagerAtS2CPacket packet) {
        if(!listAvailable(packet.getListPacket()))return;
        Minecraft.getInstance().setScreen(new NetworkManagerScreen(packet.getListPacket().entries,
                packet.getListPacket().folders, packet.getNetworkId()));
    }

    private static boolean listAvailable(NetworkListS2CPacket packet){
        if(packet.available)return true;var mc=Minecraft.getInstance();
        if(mc.screen instanceof NetworkManagerScreen)mc.setScreen(null);
        if(mc.player!=null)mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Network list exceeds the GUI budget. Use /circuit list, /circuit capacity, /circuit rename <id> <name>, or /circuit decompile <id>."),false);
        return false;
    }
    public static void openNetworkManager(NetworkListS2CPacket packet) {
        if(!listAvailable(packet))return;
        if (Minecraft.getInstance().screen instanceof NetworkManagerScreen screen) {
            screen.updateData(packet.entries, packet.folders);
            return;
        }
        Minecraft.getInstance().setScreen(
                new NetworkManagerScreen(
                        packet.entries,
                        packet.folders
                )
        );
    }

    public static void handleHighlight(NetworkHighlightS2CPacket packet,net.minecraft.network.Connection connection) {
        ClientHighlightSync.accept(packet,connection);
    }
}
