package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record BenchmarkRequestC2SPacket(int id) {
    public static void encode(BenchmarkRequestC2SPacket p, FriendlyByteBuf b) { b.writeInt(p.id); }
    public static BenchmarkRequestC2SPacket decode(FriendlyByteBuf b) {
        int id = b.readInt(); if (id < 1) throw new IllegalArgumentException("Invalid network ID");
        return new BenchmarkRequestC2SPacket(id);
    }
    public static void handle(BenchmarkRequestC2SPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> { var player = context.getSender(); if (player != null) com.example.compiledcircuits.network.CircuitBenchmark.send(player, p.id); });
        context.setPacketHandled(true);
    }
}
