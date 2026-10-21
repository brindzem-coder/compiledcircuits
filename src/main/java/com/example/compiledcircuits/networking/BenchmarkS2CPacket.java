package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.*;
import java.util.function.Supplier;

public record BenchmarkS2CPacket(int id, List<String> lines) {
    public BenchmarkS2CPacket { lines = List.copyOf(lines); }
    public static void encode(BenchmarkS2CPacket p, FriendlyByteBuf b) {
        b.writeInt(p.id); b.writeVarInt(p.lines.size());
        p.lines.forEach(s -> b.writeUtf(s, 256));
    }
    public static BenchmarkS2CPacket decode(FriendlyByteBuf b) {
        int id = b.readInt(), size = b.readVarInt();
        if (id < 1 || size < 0 || size > 20) throw new IllegalArgumentException("Invalid benchmark report");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < size; i++) lines.add(b.readUtf(256));
        return new BenchmarkS2CPacket(id, lines);
    }
    public static void handle(BenchmarkS2CPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.compiledcircuits.client.ClientBenchmarks.accept(p)));
        context.setPacketHandled(true);
    }
}
