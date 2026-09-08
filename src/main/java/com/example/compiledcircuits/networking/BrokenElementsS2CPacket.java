package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.client.ClientPacketHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

public final class BrokenElementsS2CPacket {
    private final String dimension;
    private final Set<BlockPos> positions;

    public BrokenElementsS2CPacket(String dimension, Set<BlockPos> positions) {
        this.dimension = dimension;
        Set<BlockPos> copy = new LinkedHashSet<>();
        for (BlockPos pos : positions) copy.add(pos.immutable());
        this.positions = Collections.unmodifiableSet(copy);
    }

    public String getDimension() { return dimension; }
    public Set<BlockPos> getPositions() { return positions; }

    public static void encode(BrokenElementsS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.dimension);
        buf.writeVarInt(packet.positions.size());
        for (BlockPos pos : packet.positions) buf.writeBlockPos(pos);
    }

    public static BrokenElementsS2CPacket decode(FriendlyByteBuf buf) {
        String dimension = buf.readUtf();
        int size = buf.readVarInt();
        if (size < 0 || size > buf.readableBytes() / Long.BYTES) {
            throw new IllegalArgumentException("Invalid broken position count: " + size);
        }
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (int i = 0; i < size; i++) positions.add(buf.readBlockPos());
        return new BrokenElementsS2CPacket(dimension, positions);
    }

    public static void handle(BrokenElementsS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.handleBrokenElements(packet)));
        context.setPacketHandled(true);
    }
}
