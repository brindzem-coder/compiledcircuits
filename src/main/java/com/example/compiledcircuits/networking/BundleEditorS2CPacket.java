package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import java.util.UUID;
import java.util.function.Supplier;

public record BundleEditorS2CPacket(UUID token, String id) {
    public static void encode(BundleEditorS2CPacket p, FriendlyByteBuf b) { b.writeUUID(p.token); b.writeUtf(p.id, 64); }
    public static BundleEditorS2CPacket decode(FriendlyByteBuf b) { return new BundleEditorS2CPacket(b.readUUID(), b.readUtf(64)); }
    public static void handle(BundleEditorS2CPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.compiledcircuits.client.BundleChannelScreen.open(p)));
        context.setPacketHandled(true);
    }
}
