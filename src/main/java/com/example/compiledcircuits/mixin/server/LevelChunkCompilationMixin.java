package com.example.compiledcircuits.mixin.server;

import com.example.compiledcircuits.network.CompilationJobs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Central normal block write path, including commands, pistons and mod setBlock calls. */
@Mixin(LevelChunk.class)
abstract class LevelChunkCompilationMixin {
    @Inject(method="setBlockState", at=@At("HEAD"))
    private void compiledcircuits$invalidate(BlockPos pos, BlockState state, boolean moving,
                                             CallbackInfoReturnable<BlockState> cir) {
        LevelChunk chunk = (LevelChunk)(Object)this;
        if (chunk.getLevel() instanceof ServerLevel level && chunk.getBlockState(pos) != state) {
            CompilationJobs.blockChanged(level, pos);
            if(level.getServer().isSameThread() && level.getServer().overworld()!=null)
                com.example.compiledcircuits.network.NetworkIntegrityManager.scheduleCheck(level,pos);
        }
    }
}
