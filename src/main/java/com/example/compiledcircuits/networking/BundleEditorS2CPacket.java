package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import java.util.UUID;
import java.util.function.Supplier;

public record BundleEditorS2CPacket(UUID token, String id, boolean keyboard,boolean mapping) {
    public BundleEditorS2CPacket(UUID token,String id,boolean keyboard){this(token,id,keyboard,false);}
    public static void encode(BundleEditorS2CPacket p, FriendlyByteBuf b) { b.writeUUID(p.token); b.writeUtf(p.id, 2048); b.writeBoolean(p.keyboard); b.writeBoolean(p.mapping); }
    public static BundleEditorS2CPacket decode(FriendlyByteBuf b) { return new BundleEditorS2CPacket(b.readUUID(), b.readUtf(2048), b.readBoolean(), b.readBoolean()); }
    public static void handle(BundleEditorS2CPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.compiledcircuits.client.BundleChannelScreen.open(p)));
        context.setPacketHandled(true);
    }
}
