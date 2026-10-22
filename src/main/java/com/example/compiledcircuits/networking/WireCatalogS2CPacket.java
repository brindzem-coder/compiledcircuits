package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import java.util.*;
import java.util.function.Supplier;

/** Bounded parts; client publishes only a completed snapshot. */
public record WireCatalogS2CPacket(long revision, int part, boolean last, Map<String, Integer> colors, Set<String> locked) {
    public static void encode(WireCatalogS2CPacket p, FriendlyByteBuf b) {
        b.writeLong(p.revision); b.writeVarInt(p.part); b.writeBoolean(p.last); b.writeVarInt(p.colors.size());
        p.colors.forEach((id, color) -> { b.writeUtf(id, 64); b.writeInt(color); b.writeBoolean(p.locked.contains(id)); });
    }
    public static WireCatalogS2CPacket decode(FriendlyByteBuf b) {
        long revision = b.readLong(); int part = b.readVarInt(); boolean last = b.readBoolean(); int size = b.readVarInt();
        if (part < 0 || size < 0 || size > 256) throw new IllegalArgumentException("Invalid wire palette part");
        var colors = new LinkedHashMap<String, Integer>();
        var locked = new HashSet<String>();
        for (int i = 0; i < size; i++) {
            String id = b.readUtf(64); colors.put(id, b.readInt() & 0xffffff);
            if (b.readBoolean()) locked.add(id);
        }
        return new WireCatalogS2CPacket(revision, part, last, colors, locked);
    }
    public static void handle(WireCatalogS2CPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.compiledcircuits.client.WireClient.accept(p))); context.setPacketHandled(true);
    }
}
