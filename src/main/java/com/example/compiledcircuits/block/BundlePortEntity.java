package com.example.compiledcircuits.block;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BundlePortEntity extends BlockEntity {
    private String channel = "0";
    public BundlePortEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.BUNDLE_PORT.get(), pos, state); }
    public String channel() { return channel; }
    public void configure(String id) {
        channel = WireIds.normalize(id); setChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            CompilationJobs.blockChanged(server, worldPosition);
            NetworkIntegrityManager.scheduleCheck(server, worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putString("ChannelId", channel); }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        try { channel = WireIds.normalize(tag.getString("ChannelId")); }
        catch (IllegalArgumentException invalid) { channel = ""; }
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
