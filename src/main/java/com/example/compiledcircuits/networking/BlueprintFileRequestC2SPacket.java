package com.example.compiledcircuits.networking;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.example.compiledcircuits.network.BlueprintFileService;

public record BlueprintFileRequestC2SPacket(int action, int networkId, UUID token) {
    public static void encode(BlueprintFileRequestC2SPacket p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeVarInt(p.networkId); b.writeUUID(p.token); }
    public static BlueprintFileRequestC2SPacket decode(FriendlyByteBuf b) { return new BlueprintFileRequestC2SPacket(b.readVarInt(), b.readVarInt(), b.readUUID()); }
    public static void handle(BlueprintFileRequestC2SPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); context.enqueueWork(() -> BlueprintFileService.request(context.getSender(), p)); context.setPacketHandled(true);
    }
}
