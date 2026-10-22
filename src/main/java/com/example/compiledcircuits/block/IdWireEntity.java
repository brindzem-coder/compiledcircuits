package com.example.compiledcircuits.block;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Identity has no ticking cost and is independent of the render color. */
public final class IdWireEntity extends BlockEntity {
    private String wireId = "";
    private int color = WireIds.RED;
    public IdWireEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ID_WIRE.get(), pos, state); }
    public String wireId() { return wireId; }
    public int color() { return level instanceof ServerLevel server ? WireCatalog.get(server.getServer()).color(wireId, color) : color; }
    public void configure(String id, int rgb) {
        id = WireIds.normalize(id);
        if (id.equals("0")) throw new IllegalArgumentException("ID 0 requires basic wire");
        wireId = id; color = rgb & 0xffffff; setChanged();
        if (level instanceof ServerLevel server) {
            if (!server.captureBlockSnapshots && !server.restoringBlockSnapshots) reconcile();
            CompilationJobs.blockChanged(server, worldPosition);
            NetworkIntegrityManager.scheduleCheck(server, worldPosition);
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }
    public void reconcile() {
        if (level instanceof ServerLevel server && !server.captureBlockSnapshots && !server.restoringBlockSnapshots && !wireId.isEmpty()) {
            var data = WireCatalog.get(server.getServer());
            color = data.color(wireId, color);
            data.placed(server, worldPosition, wireId, color);
        }
    }
    @Override public void onLoad() { super.onLoad(); reconcile(); if (level != null && level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.example.compiledcircuits.client.WireClient.track(this)); }
    @Override public void load(CompoundTag tag) {
        String previous = wireId;
        super.load(tag);
        try { wireId = WireIds.normalize(tag.getString("WireId")); if (wireId.equals("0")) wireId = ""; }
        catch (IllegalArgumentException invalid) { wireId = ""; }
        color = tag.getInt("WireColor") & 0xffffff;
        if (level instanceof ServerLevel server && !wireId.equals(previous)) {
            if (wireId.isEmpty() && !server.captureBlockSnapshots && !server.restoringBlockSnapshots) WireCatalog.get(server.getServer()).remove(server, worldPosition);
            else reconcile();
            CompilationJobs.blockChanged(server, worldPosition); NetworkIntegrityManager.scheduleCheck(server, worldPosition);
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            if (!wireId.equals(previous)) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.compiledcircuits.client.WireClient.identityChanged(this));
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putString("WireId", wireId);
        int savedColor = level instanceof ServerLevel server ? WireCatalog.get(server.getServer()).color(wireId, color) : color;
        tag.putInt("WireColor", savedColor);
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
