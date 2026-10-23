package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.client.ClientPacketHandlers;
import com.example.compiledcircuits.network.OperationLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record OpenRenameS2CPacket(int id, String name) {
    public static void encode(OpenRenameS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.id());
        buf.writeUtf(packet.name(), OperationLimits.NAME);
    }
    public static OpenRenameS2CPacket decode(FriendlyByteBuf buf) {
        return new OpenRenameS2CPacket(buf.readVarInt(), buf.readUtf(OperationLimits.NAME));
    }
    public static void handle(OpenRenameS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.openRenameScreen(packet, context.getNetworkManager())));
        context.setPacketHandled(true);
    }
}
