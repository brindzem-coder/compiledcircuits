package com.example.compiledcircuits.networking;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.example.compiledcircuits.network.PortableBlueprint;

public record BlueprintFilePartS2CPacket(UUID token, int offset, int total, byte[] bytes) {
    public static void encode(BlueprintFilePartS2CPacket p, FriendlyByteBuf b) { b.writeUUID(p.token); b.writeVarInt(p.offset); b.writeVarInt(p.total); b.writeByteArray(p.bytes); }
    public static BlueprintFilePartS2CPacket decode(FriendlyByteBuf b) { return new BlueprintFilePartS2CPacket(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readByteArray(PortableBlueprint.PART_BYTES)); }
    public static void handle(BlueprintFilePartS2CPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); var connection = context.getNetworkManager();
        context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.example.compiledcircuits.client.BlueprintFilesClient.accept(p, connection)));
        context.setPacketHandled(true);
    }
}
