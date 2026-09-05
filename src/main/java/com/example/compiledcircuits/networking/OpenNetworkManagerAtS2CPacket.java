package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenNetworkManagerAtS2CPacket {
    private final NetworkListS2CPacket listPacket;
    private final int networkId;

    public OpenNetworkManagerAtS2CPacket(NetworkListS2CPacket listPacket, int networkId) {
        this.listPacket = listPacket;
        this.networkId = networkId;
    }

    public NetworkListS2CPacket getListPacket() {
        return listPacket;
    }

    public int getNetworkId() {
        return networkId;
    }

    public static void encode(OpenNetworkManagerAtS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.networkId);
        NetworkListS2CPacket.encode(packet.listPacket, buf);
    }

    public static OpenNetworkManagerAtS2CPacket decode(FriendlyByteBuf buf) {
        int networkId = buf.readInt();
        return new OpenNetworkManagerAtS2CPacket(NetworkListS2CPacket.decode(buf), networkId);
    }

    public static void handle(OpenNetworkManagerAtS2CPacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.openNetworkManagerAt(packet)));
        context.setPacketHandled(true);
    }
}
