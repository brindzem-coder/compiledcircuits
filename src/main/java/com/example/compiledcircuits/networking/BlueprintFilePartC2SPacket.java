package com.example.compiledcircuits.networking;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.example.compiledcircuits.network.*;

public record BlueprintFilePartC2SPacket(UUID token, int offset, int total, byte[] bytes) {
    public static void encode(BlueprintFilePartC2SPacket p, FriendlyByteBuf b) { b.writeUUID(p.token); b.writeVarInt(p.offset); b.writeVarInt(p.total); b.writeByteArray(p.bytes); }
    public static BlueprintFilePartC2SPacket decode(FriendlyByteBuf b) { return new BlueprintFilePartC2SPacket(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readByteArray(PortableBlueprint.PART_BYTES)); }
    public static void handle(BlueprintFilePartC2SPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); context.enqueueWork(() -> BlueprintFileService.accept(context.getSender(), p)); context.setPacketHandled(true);
    }
}
