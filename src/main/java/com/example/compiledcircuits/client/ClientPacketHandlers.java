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
        Minecraft.getInstance().setScreen(new NetworkManagerScreen(packet.getListPacket().entries,
                packet.getListPacket().folders, packet.getNetworkId()));
    }

    public static void openNetworkManager(NetworkListS2CPacket packet) {
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

    public static void handleHighlight(
            NetworkHighlightS2CPacket packet
    ) {

        ClientNetworkSelection.setSelection(
                packet.getWires(),
                packet.getInputs(),
                packet.getOutputs()
        );

        Minecraft.getInstance().setScreen(null);
    }
}