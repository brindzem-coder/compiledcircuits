package com.example.compiledcircuits.mixin.client;

import com.example.compiledcircuits.client.ClientCircuitBlockIndex;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class LevelChunkVisualIndexMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
    private void compiledcircuits$afterBlockChange(BlockPos pos, BlockState requested, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (!(chunk.getLevel() instanceof ClientLevel level) || cir.getReturnValue() == null) return;
        ClientCircuitBlockIndex.onBlockChanged(level, pos, cir.getReturnValue(), chunk.getBlockState(pos));
    }
}
