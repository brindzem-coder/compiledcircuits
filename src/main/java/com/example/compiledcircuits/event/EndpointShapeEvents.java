package com.example.compiledcircuits.event;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.RedstoneEndpointBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Refresh old saved endpoints whose block states predate the visual connection properties. */
@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID)
public final class EndpointShapeEvents {
    private EndpointShapeEvents() {}

    @SubscribeEvent public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var chunk = event.getChunk();
        var sections = chunk.getSections();
        var pos = new BlockPos.MutableBlockPos();
        for (int index = 0; index < sections.length; index++) {
            var section = sections[index];
            if (!section.maybeHas(state -> state.getBlock() instanceof RedstoneEndpointBlock)) continue;
            int bottom = level.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                var block = section.getBlockState(x, y, z).getBlock();
                if (!(block instanceof RedstoneEndpointBlock)) continue;
                pos.set(chunk.getPos().getMinBlockX() + x, bottom + y, chunk.getPos().getMinBlockZ() + z);
                level.scheduleTick(pos.immutable(), block, 1);
            }
        }
    }
}
