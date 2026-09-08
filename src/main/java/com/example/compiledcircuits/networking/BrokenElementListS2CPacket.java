package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.client.ClientPacketHandlers;
import com.example.compiledcircuits.network.CircuitElementType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class BrokenElementListS2CPacket {
    public record Entry(int networkId, String networkName, int folderId, int elementId,
                        String dimension, BlockPos pos, CircuitElementType type,
                        String blockId, String actualBlockId, long detectedAt) {
        public Entry { pos = pos.immutable(); }
    }
    private final List<Entry> entries;
    public BrokenElementListS2CPacket(List<Entry> entries) { this.entries = List.copyOf(entries); }
    public List<Entry> getEntries() { return entries; }

    public static void encode(BrokenElementListS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entries.size());
        for (Entry e : packet.entries) {
            buf.writeVarInt(e.networkId()); buf.writeUtf(e.networkName());
            buf.writeVarInt(e.folderId()); buf.writeVarInt(e.elementId());
            buf.writeUtf(e.dimension()); buf.writeBlockPos(e.pos()); buf.writeEnum(e.type());
            buf.writeUtf(e.blockId()); buf.writeUtf(e.actualBlockId()); buf.writeLong(e.detectedAt());
        }
    }
    public static BrokenElementListS2CPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        // Every entry requires at least 24 bytes, including empty strings.
        if (size < 0 || size > buf.readableBytes() / 24) throw new IllegalArgumentException("Invalid broken entry count");
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(), buf.readBlockPos(), buf.readEnum(CircuitElementType.class),
                    buf.readUtf(), buf.readUtf(), buf.readLong()));
        }
        return new BrokenElementListS2CPacket(entries);
    }
    public static void handle(BrokenElementListS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.handleBrokenElementList(packet)));
        context.setPacketHandled(true);
    }
}
