package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.client.ClientPacketHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

public record CompiledElementPositionsS2CPacket(ResourceLocation dimension, long snapshotId,
                                               int partIndex, int partCount, List<Entry> entries, List<BlockPos> blocked, boolean unknownReservations, java.util.UUID context, State state, String reason) {
    public CompiledElementPositionsS2CPacket(ResourceLocation dimension,long snapshotId,int partIndex,int partCount,List<Entry> entries,List<BlockPos> blocked,boolean unknown,java.util.UUID context,State state){
        this(dimension,snapshotId,partIndex,partCount,entries,blocked,unknown,context,state,"");
    }
    public enum State { READY, OVER_CAPACITY, UNKNOWN }
    public static final java.util.UUID TEST_CONTEXT = new java.util.UUID(0,0);
    public CompiledElementPositionsS2CPacket(ResourceLocation dimension,long snapshotId,int partIndex,int partCount,List<Entry> entries,List<BlockPos> blocked,boolean unknownReservations) {
        this(dimension,snapshotId,partIndex,partCount,entries,blocked,unknownReservations,TEST_CONTEXT,State.READY);
    }
    public CompiledElementPositionsS2CPacket withContext(java.util.UUID token) {
        return new CompiledElementPositionsS2CPacket(dimension,snapshotId,partIndex,partCount,entries,blocked,unknownReservations,token,state,reason);
    }
    // Protocol limits; raising these requires matching server/client changes.
    public static final int MAX_ENTRIES = 1_000_000;
    public static final int ENTRIES_PER_PART = 4096;
    public static final int MAX_PARTS = (MAX_ENTRIES + ENTRIES_PER_PART - 1) / ENTRIES_PER_PART;
    public static final long MAX_BYTES = 64L * 1024 * 1024;
    public static final long STAGING_TIMEOUT_NANOS = 30_000_000_000L;
    public static final long STAGING_LIFETIME_NANOS=180_000_000_000L;
    public record Entry(int networkId, BlockPos pos) {
        public Entry {
            if (networkId <= 0) throw new IllegalArgumentException("Invalid network ID");
            pos = pos.immutable();
        }
    }
    public CompiledElementPositionsS2CPacket(ResourceLocation dimension, long snapshotId, int partIndex, int partCount, List<Entry> entries) {
        this(dimension, snapshotId, partIndex, partCount, entries, List.of(), false);
    }
    public CompiledElementPositionsS2CPacket {
        if (entries == null || blocked == null || reason == null || reason.length()>128 || context == null || state == null || (state != State.READY && (partCount != 1 || !entries.isEmpty() || !blocked.isEmpty())) || dimension == null || dimension.toString().length() > 256 || snapshotId <= 0
                || partCount < 1 || partCount > MAX_PARTS || partIndex < 0 || partIndex >= partCount
                || (long)entries.size() + blocked.size() > ENTRIES_PER_PART || (entries.isEmpty() && blocked.isEmpty() && partCount != 1)) {
            throw new IllegalArgumentException("Invalid compiled snapshot part");
        }
        entries = List.copyOf(entries);
        blocked = blocked.stream().map(BlockPos::immutable).toList();
        var seen = new HashSet<BlockPos>();
        for (Entry entry : entries) if (!seen.add(entry.pos())) throw new IllegalArgumentException("Duplicate compiled position");
        for (BlockPos pos : blocked) if (!seen.add(pos)) throw new IllegalArgumentException("Duplicate blocked position");
    }
    /** Encoded payload bytes, excluding the channel discriminator and transport framing. */
    public long encodedBytes() {
        int dimensionBytes = dimension.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        long bytes = FriendlyByteBuf.getVarIntSize(dimensionBytes) + dimensionBytes + 8
                + FriendlyByteBuf.getVarIntSize(partIndex) + FriendlyByteBuf.getVarIntSize(partCount)
                + FriendlyByteBuf.getVarIntSize(entries.size());
        for (Entry entry : entries) bytes += FriendlyByteBuf.getVarIntSize(entry.networkId()) + 8;
        int reasonBytes=reason.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        return bytes + 18 + FriendlyByteBuf.getVarIntSize(reasonBytes)+reasonBytes + FriendlyByteBuf.getVarIntSize(blocked.size()) + 8L * blocked.size();
    }
    public long estimatedBytes() { return 1024L + 13L * (entries.size() + blocked.size()); }
    public static void encode(CompiledElementPositionsS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.context); buf.writeByte(packet.state.ordinal());buf.writeUtf(packet.reason,128);
        buf.writeResourceLocation(packet.dimension); buf.writeLong(packet.snapshotId);
        buf.writeVarInt(packet.partIndex); buf.writeVarInt(packet.partCount); buf.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) { buf.writeVarInt(entry.networkId()); buf.writeBlockPos(entry.pos()); }
        buf.writeBoolean(packet.unknownReservations); buf.writeVarInt(packet.blocked.size());
        for (BlockPos pos : packet.blocked) buf.writeBlockPos(pos);
    }
    public static CompiledElementPositionsS2CPacket decode(FriendlyByteBuf buf) {
        if (buf.readableBytes() > 64 * 1024) throw new IllegalArgumentException("Compiled part exceeds byte budget");
        var token = buf.readUUID(); int state = buf.readUnsignedByte();
        if(state >= State.values().length) throw new IllegalArgumentException("Invalid membership status");
        String reason=buf.readUtf(128);
        ResourceLocation dimension = ResourceLocation.tryParse(buf.readUtf(256));
        long id = buf.readLong(); int index = buf.readVarInt(), count = buf.readVarInt(), size = buf.readVarInt();
        if (dimension == null || id <= 0 || count < 1 || count > MAX_PARTS || index < 0 || index >= count || size < 0 || size > ENTRIES_PER_PART || size > buf.readableBytes() / 9) throw new IllegalArgumentException("Invalid entry count");
        var entries = new java.util.ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(buf.readVarInt(), buf.readBlockPos()));
        boolean unknown = buf.readBoolean();
        int blockedSize = buf.readVarInt();
        if (blockedSize < 0 || blockedSize > ENTRIES_PER_PART - size || blockedSize > buf.readableBytes() / 8)
            throw new IllegalArgumentException("Invalid blocked entry count");
        var blocked = new java.util.ArrayList<BlockPos>(blockedSize);
        for (int i = 0; i < blockedSize; i++) blocked.add(buf.readBlockPos());
        if(buf.isReadable())throw new IllegalArgumentException("Trailing membership bytes");
        return new CompiledElementPositionsS2CPacket(dimension, id, index, count, entries, blocked, unknown,token,State.values()[state],reason);
    }
    public static void handle(CompiledElementPositionsS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        var connection = context.getNetworkManager();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.handleCompiledElements(packet, connection)));
        context.setPacketHandled(true);
    }
}
