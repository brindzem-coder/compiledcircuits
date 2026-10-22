package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record WireSettingsC2SPacket(UUID token, String id, int color, boolean cancel) {
    public static void encode(WireSettingsC2SPacket p, FriendlyByteBuf b) { b.writeUUID(p.token); b.writeUtf(p.id, 64); b.writeInt(p.color); b.writeBoolean(p.cancel); }
    public static WireSettingsC2SPacket decode(FriendlyByteBuf b) { return new WireSettingsC2SPacket(b.readUUID(), b.readUtf(64), b.readInt(), b.readBoolean()); }
    public static void handle(WireSettingsC2SPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); context.enqueueWork(() -> {
            if (context.getSender() != null) com.example.compiledcircuits.network.WireToolService.finish(context.getSender(), p.token, p.id, p.color, p.cancel);
        }); context.setPacketHandled(true);
    }
}
