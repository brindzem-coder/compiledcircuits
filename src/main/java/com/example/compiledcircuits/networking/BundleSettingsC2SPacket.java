package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record BundleSettingsC2SPacket(UUID token, String id, boolean cancel) {
    public static void encode(BundleSettingsC2SPacket p, FriendlyByteBuf b) { b.writeUUID(p.token); b.writeUtf(p.id, 64); b.writeBoolean(p.cancel); }
    public static BundleSettingsC2SPacket decode(FriendlyByteBuf b) { return new BundleSettingsC2SPacket(b.readUUID(), b.readUtf(64), b.readBoolean()); }
    public static void handle(BundleSettingsC2SPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) com.example.compiledcircuits.network.BundleToolService.finish(player, p.token, p.id, p.cancel);
        }); context.setPacketHandled(true);
    }
}
