package com.example.compiledcircuits.block;

import com.example.compiledcircuits.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** No ticker: reconcile a saved light once when its chunk loads. */
public final class SignalLampEntity extends BlockEntity {
    public SignalLampEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.SIGNAL_LAMP.get(), pos, state); }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) server.getServer().execute(() -> {
            if (!isRemoved() && server.hasChunkAt(worldPosition))
                server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        });
    }
}
