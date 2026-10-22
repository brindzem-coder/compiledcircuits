package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Compile-time routing, scoped by cable component and compilation ownership. */
final class BundleTopology {
    record Port(BundleFace face, BundleWireEntity.Port config, boolean inline) {
        BlockPos pos() { return face.pos(); }
    }
    record Route(Port input, Port output, String channel) {}
    static boolean element(BlockState state) {
        return state.getBlock() instanceof BundledWireBlock || state.getBlock() instanceof BundlePortBlock;
    }
    static List<Route> resolve(Map<BlockPos, BlockState> states, Map<BlockPos, String> channels, Map<BlockPos, Integer> owners, Map<BundleFace, BundleWireEntity.Port> faces) {
        Map<BlockPos, Integer> cables = new HashMap<>(); int index = 0;
        for (var entry : states.entrySet()) if (entry.getValue().getBlock() instanceof BundledWireBlock && !cables.containsKey(entry.getKey())) {
            var queue = new ArrayDeque<BlockPos>(); queue.add(entry.getKey()); cables.put(entry.getKey(), ++index);
            while (!queue.isEmpty()) {
                var pos = queue.remove();
                for (var side : Direction.values()) {
                    var next = pos.relative(side); var state = states.get(next);
                    if (state != null && state.getBlock() instanceof BundledWireBlock && !cables.containsKey(next)
                            && !faces.containsKey(new BundleFace(pos, side)) && !faces.containsKey(new BundleFace(next, side.getOpposite()))
                            && Objects.equals(owners.get(pos), owners.get(next))) { cables.put(next, index); queue.add(next); }
                }
            }
        }
        record Key(int cable, String channel) {}
        Map<Key, Port> inputs = new HashMap<>(); Map<Key, List<Port>> outputs = new LinkedHashMap<>();
        List<Port> ports = new ArrayList<>();
        for (var entry : states.entrySet()) {
            var pos = entry.getKey();
            if (entry.getValue().getBlock() instanceof BundledWireBlock) {
                for (var side : Direction.values()) {
                    var face = new BundleFace(pos, side); var config = faces.get(face);
                    if (config != null) ports.add(new Port(face, config, true));
                }
            } else if (entry.getValue().getBlock() instanceof BundlePortBlock port) {
                String channel = channels.get(pos);
                if (channel == null || !WireIds.normalize(channel).equals(channel)) throw new IllegalArgumentException("Invalid bundle channel at " + pos.toShortString());
                ports.add(new Port(new BundleFace(pos, entry.getValue().getValue(EndpointBlock.FACING)),
                        new BundleWireEntity.Port((port.isExternal() ? 3 : 1) + (port.isInput() ? 0 : 1), channel), false));
            }
        }
        for (var port : ports) {
            var cable = port.inline ? port.pos() : port.pos().relative(port.face.side().getOpposite());
            Integer component = cables.get(cable);
            if (component == null || !Objects.equals(owners.get(port.pos()), owners.get(cable))
                    || !port.inline && faces.containsKey(new BundleFace(cable, port.face.side())))
                throw new IllegalArgumentException("Bundle port needs a cable connection at " + port.pos().toShortString());
            var key = new Key(component, port.config.id());
            if (port.config.input()) {
                var previous = inputs.putIfAbsent(key, port);
                if (previous != null) throw new IllegalArgumentException("Bundle channel " + key.channel + " has multiple inputs at "
                        + previous.pos().toShortString() + " " + previous.face.side() + " and " + port.pos().toShortString() + " " + port.face.side());
            } else outputs.computeIfAbsent(key, k -> new ArrayList<>()).add(port);
        }
        var routes = new ArrayList<Route>();
        for (var entry : outputs.entrySet()) {
            var input = inputs.get(entry.getKey());
            if (input == null) throw new IllegalArgumentException("Bundle channel " + entry.getKey().channel + " has no input at " + entry.getValue().get(0).pos().toShortString());
            for (var output : entry.getValue()) routes.add(new Route(input, output, entry.getKey().channel));
        }
        return routes;
    }
}
