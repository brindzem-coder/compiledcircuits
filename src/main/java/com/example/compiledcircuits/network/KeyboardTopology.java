package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Resolves physical keyboard buses once, without merging ordinary bundle channels. */
final class KeyboardTopology {
    private record Cable(BlockPos pos, Direction.Axis axis) {}
    static Map<BundleFace, BlockPos> resolve(Map<BlockPos, BlockState> states,
            Map<BundleFace, BundleWireEntity.Port> faces, Map<BlockPos, Integer> owners) {
        if (faces.values().stream().noneMatch(BundleWireEntity.Port::keyboard)) return Map.of();
        Set<Cable> visited = new HashSet<>();
        Map<BundleFace, BlockPos> result = new HashMap<>();
        for (var entry : states.entrySet()) {
            List<Cable> seeds = new ArrayList<>();
            if (entry.getValue().getBlock() instanceof BundledWireBlock) seeds.add(new Cable(entry.getKey(), null));
            else if (entry.getValue().getBlock() instanceof CrossingWireBlock)
                for (var axis : Direction.Axis.values()) if (CrossingWireBlock.kind(entry.getValue(), axis) == 3) seeds.add(new Cable(entry.getKey(), axis));
            for (var seed : seeds) {
                if (!visited.add(seed)) continue;
                Set<BlockPos> keyboards = new HashSet<>(); List<BundleFace> ports = new ArrayList<>();
                var queue = new ArrayDeque<Cable>(); queue.add(seed);
                while (!queue.isEmpty()) {
                    var cable = queue.remove();
                    for (var side : Direction.values()) {
                        if (cable.axis != null && side.getAxis() != cable.axis) continue;
                        var face = new BundleFace(cable.pos, side); var port = faces.get(face);
                        if (port != null) { if (port.keyboard()) ports.add(face); continue; }
                        var next = cable.pos.relative(side); var state = states.get(next);
                        if (state == null || !Objects.equals(Crossings.owner(owners,cable.pos,side), Crossings.owner(owners,next,side.getOpposite()))) continue;
                        if (state.getBlock() instanceof KeyboardBlock) { keyboards.add(next); continue; }
                        if (faces.containsKey(new BundleFace(next,side.getOpposite()))) continue;
                        Cable neighbor = state.getBlock() instanceof BundledWireBlock ? new Cable(next,null)
                                : CrossingWireBlock.kind(state,side.getAxis()) == 3 ? new Cable(next,side.getAxis()) : null;
                        if (neighbor != null && visited.add(neighbor)) queue.add(neighbor);
                    }
                }
                if (!ports.isEmpty() && keyboards.size() != 1)
                    throw new IllegalArgumentException("Keyboard ports need exactly one keyboard on their cable (found " + keyboards.size() + ")");
                for (var port : ports) result.put(port,keyboards.iterator().next());
            }
        }
        return result;
    }
}
