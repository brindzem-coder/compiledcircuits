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
        record Cable(BlockPos pos,Direction.Axis axis){}
        Map<Cable,Integer> cables=new HashMap<>();int index=0;
        List<Cable> seeds=new ArrayList<>();
        for(var e:states.entrySet()){
            if(e.getValue().getBlock() instanceof BundledWireBlock)seeds.add(new Cable(e.getKey(),null));
            else if(e.getValue().getBlock() instanceof CrossingWireBlock)for(var axis:Direction.Axis.values())if(CrossingWireBlock.kind(e.getValue(),axis)==3)seeds.add(new Cable(e.getKey(),axis));
        }
        for(var seed:seeds)if(!cables.containsKey(seed)){
            var queue=new ArrayDeque<Cable>();queue.add(seed);cables.put(seed,++index);
            while(!queue.isEmpty()){
                var current=queue.remove();var pos=current.pos();
                for(var side:Direction.values()){
                    if(current.axis()!=null&&side.getAxis()!=current.axis()||faces.containsKey(new BundleFace(pos,side)))continue;
                    var next=pos.relative(side);var state=states.get(next);if(state==null||faces.containsKey(new BundleFace(next,side.getOpposite())))continue;
                    Cable key=state.getBlock() instanceof BundledWireBlock?new Cable(next,null):CrossingWireBlock.kind(state,side.getAxis())==3?new Cable(next,side.getAxis()):null;
                    if(key!=null&&!cables.containsKey(key)&&Objects.equals(Crossings.owner(owners,pos,side),Crossings.owner(owners,next,side.getOpposite()))){cables.put(key,index);queue.add(key);}
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
            var cableState=states.get(cable);
            var keyCable=new Cable(cable,cableState!=null&&cableState.getBlock() instanceof CrossingWireBlock?port.face.side().getAxis():null);
            Integer component = cables.get(keyCable);
            if (component == null || !Objects.equals(Crossings.owner(owners,port.pos(),port.face.side().getOpposite()), Crossings.owner(owners,cable,port.face.side()))
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
