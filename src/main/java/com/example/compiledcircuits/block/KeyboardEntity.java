package com.example.compiledcircuits.block;

import com.example.compiledcircuits.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class KeyboardEntity extends BlockEntity {
    private Vec3 offset;
    private float yaw, pitch;
    public KeyboardEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.KEYBOARD.get(),pos,state); }
    public Vec3 camera() { return offset == null ? null : Vec3.atLowerCornerOf(worldPosition).add(offset); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public void configureCamera(ServerPlayer player) {
        if (offset == null) {
            offset = player.position().subtract(Vec3.atLowerCornerOf(worldPosition)); yaw=player.getYRot(); pitch=player.getXRot();
            player.sendSystemMessage(Component.translatable("keyboard.compiledcircuits.camera_set"));
        } else { offset=null; player.sendSystemMessage(Component.translatable("keyboard.compiledcircuits.camera_clear")); }
        setChanged();
        var data=com.example.compiledcircuits.network.NetworkSavedData.get(player.getServer());
        var network=data.findNetworkByInput(player.serverLevel(),worldPosition);
        if(network!=null){network.getElementAt(worldPosition).captureWire(level);data.setDirty();}
    }
    public CompoundTag cameraData() {
        var tag = new CompoundTag();
        if (offset != null) { tag.putDouble("x",offset.x);tag.putDouble("y",offset.y);tag.putDouble("z",offset.z);tag.putFloat("yaw",yaw);tag.putFloat("pitch",pitch); }
        return tag;
    }
    public void restoreCamera(CompoundTag tag) {
        offset=null;
        if (tag.contains("x")) {
            var value=new Vec3(tag.getDouble("x"),tag.getDouble("y"),tag.getDouble("z"));
            float y=tag.getFloat("yaw"),p=tag.getFloat("pitch");
            if (Double.isFinite(value.lengthSqr()) && value.lengthSqr() <= 256 && Float.isFinite(y) && Float.isFinite(p)) { offset=value; yaw=y;pitch=net.minecraft.util.Mth.clamp(p,-90,90); }
        }
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag);tag.put("Camera",cameraData()); }
    @Override public void load(CompoundTag tag) { super.load(tag);restoreCamera(tag.getCompound("Camera")); }
}
