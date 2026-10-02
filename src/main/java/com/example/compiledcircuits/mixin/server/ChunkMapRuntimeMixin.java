package com.example.compiledcircuits.mixin.server;

import com.example.compiledcircuits.network.NetworkRuntime;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashSet;
import java.util.Set;

/** Forge Unload is later than FULL demotion: gate input dependencies at the actual status transition. */
@Mixin(ChunkMap.class)
abstract class ChunkMapRuntimeMixin {
    @Shadow @Final private ServerLevel level;
    @Unique private final Set<Long> compiledcircuits$fullChunks = new HashSet<>();
    @Inject(method="onFullChunkStatusChange", at=@At("HEAD"))
    private void compiledcircuits$availability(ChunkPos pos, FullChunkStatus status, CallbackInfo ci) {
        boolean loaded = status.isOrAfter(FullChunkStatus.FULL);
        boolean changed = loaded ? compiledcircuits$fullChunks.add(pos.toLong()) : true;
        if (!loaded) compiledcircuits$fullChunks.remove(pos.toLong());
        if (changed && level.getServer().overworld() != null)
            NetworkRuntime.chunkChanged(level, pos.toLong(), loaded);
    }
}
