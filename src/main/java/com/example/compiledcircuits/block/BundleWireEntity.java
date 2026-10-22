package com.example.compiledcircuits.block;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.registry.ModBlockEntities;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Six independently configured faces; cable geometry remains the original 64 block states. */
public final class BundleWireEntity extends BlockEntity {
    public record Port(int kind, String id) {
        public Port {
            if (kind < 1 || kind > 4) throw new IllegalArgumentException("Invalid bundle port kind");
            id = WireIds.normalize(id);
        }
        public boolean input() { return kind == 1 || kind == 3; }
        public boolean external() { return kind >= 3; }
    }
    private final EnumMap<Direction, Port> ports = new EnumMap<>(Direction.class);
    public BundleWireEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.BUNDLE_WIRE.get(), pos, state); }
    public Map<Direction, Port> ports() { return Collections.unmodifiableMap(ports); }
    public Port port(Direction side) { return ports.get(side); }
    public void setPort(Direction side, Port port) {
        if (port == null) ports.remove(side); else ports.put(side, port);
        changed();
    }
    public void restorePorts(CompoundTag tag) { ports.clear(); ports.putAll(decode(tag)); changed(); }
    public void refreshConnections() {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            server.setBlock(worldPosition, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(getBlockState(), server, worldPosition), 3);
            server.getBlockState(worldPosition).updateNeighbourShapes(server, worldPosition, 3);
        }
    }
    public CompoundTag portData() { return encode(ports); }
    public static CompoundTag encode(Map<Direction, Port> ports) {
        var tag = new CompoundTag();
        ports.forEach((side, port) -> { var item = new CompoundTag(); item.putInt("kind", port.kind()); item.putString("id", port.id()); tag.put(side.getName(), item); });
        return tag;
    }
    public static Map<Direction, Port> decode(CompoundTag tag) {
        var result = new EnumMap<Direction, Port>(Direction.class);
        for (String key : tag.getAllKeys()) {
            Direction side = Direction.byName(key);
            if (side == null || !tag.contains(key, 10)) throw new IllegalArgumentException("Invalid bundle face");
            var item = tag.getCompound(key); String id = item.getString("id");
            var port = new Port(item.getInt("kind"), id);
            if (!id.equals(port.id())) throw new IllegalArgumentException("Noncanonical channel ID");
            result.put(side, port);
        }
        return result;
    }
    private void changed() {
        setChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            CompilationJobs.blockChanged(server, worldPosition);
            NetworkIntegrityManager.scheduleCheck(server, worldPosition);
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.put("BundleFaces", portData()); }
    @Override public void load(CompoundTag tag) {
        super.load(tag); ports.clear(); ports.putAll(decode(tag.getCompound("BundleFaces")));
        if (level != null && level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.example.compiledcircuits.client.ClientHoveredCircuit.clear());
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
