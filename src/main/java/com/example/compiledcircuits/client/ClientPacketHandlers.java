package com.example.compiledcircuits.client;

import com.example.compiledcircuits.networking.NetworkListS2CPacket;
import net.minecraft.client.Minecraft;
import com.example.compiledcircuits.networking.CompileNamedC2SPacket;
import com.example.compiledcircuits.networking.ModNetworking;

import com.example.compiledcircuits.networking.NetworkHighlightS2CPacket;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void openCompileNameScreen() {
        Minecraft.getInstance().setScreen(new NetworkTextEditScreen(
                null, "Compile Network", "Network name:", "",
                name -> ModNetworking.CHANNEL.sendToServer(new CompileNamedC2SPacket(name)), 64));
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