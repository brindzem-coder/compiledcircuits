package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import java.util.UUID;
import java.util.function.Supplier;

public record WireEditorS2CPacket(UUID token, String id, int color) {
    public static void encode(WireEditorS2CPacket p, FriendlyByteBuf b) { b.writeUUID(p.token); b.writeUtf(p.id, 64); b.writeInt(p.color); }
    public static WireEditorS2CPacket decode(FriendlyByteBuf b) { return new WireEditorS2CPacket(b.readUUID(), b.readUtf(64), b.readInt()); }
    public static void handle(WireEditorS2CPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.compiledcircuits.client.WireClient.open(p))); context.setPacketHandled(true);
    }
}
