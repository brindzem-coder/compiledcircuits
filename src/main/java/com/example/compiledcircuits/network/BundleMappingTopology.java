package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Converts each declared mapping into OR nodes between otherwise separate cable components. */
final class BundleMappingTopology {
    record Key(BundleFace face,String id) {}
    record Link(Key from,Key to) {}
    record Plan(List<BundleTopology.Port> ports,Set<Key> nodes,List<Link> links,Set<BlockPos> boundaries) {}
    static Plan plan(Map<BlockPos,BlockState> states,Map<BundleFace,BundleWireEntity.Port> faces,
                     Map<BlockPos,Integer> owners,List<BundleFace.Link> directLinks){
        var ports=new ArrayList<BundleTopology.Port>();var nodes=new LinkedHashSet<Key>();var links=new ArrayList<Link>();var boundaries=new HashSet<BlockPos>();
        var ordered=faces.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(BundleFace::pos).thenComparing(BundleFace::side))).toList();
        for(var entry:ordered){
            var face=entry.getKey();var config=entry.getValue();if(!config.mapping())continue;
            var mapping=ChannelMapping.parse(config.id());
            if(config.external())boundaries.add(face.pos());
            for(var id:mapping.targets())nodes.add(new Key(face,id));
            if(config.kind()!=9)for(var edge:mapping.edges())ports.add(new BundleTopology.Port(face,new BundleWireEntity.Port(2,edge.from()),true,-1,false,new Key(face,edge.to())));
            BundleFace destination=null;
            if(config.kind()==7){
                destination=new BundleFace(face.pos().relative(face.side()),face.side().getOpposite());var state=states.get(destination.pos());
                if(state==null||!(state.getBlock() instanceof BundledWireBlock)||faces.containsKey(destination)||!Objects.equals(owners.get(face.pos()),owners.get(destination.pos())))
                    throw new IllegalArgumentException("Internal bundle mapping needs a bare bundled wire on its output at "+face.pos().toShortString());
            }else if(config.kind()==9)destination=face;
            if(destination!=null)for(var id:mapping.targets())ports.add(new BundleTopology.Port(destination,new BundleWireEntity.Port(1,id),true,-1,false,new Key(face,id)));
            if(ports.size()>32768||nodes.size()>32768)throw new IllegalArgumentException("Bundle mapping expansion exceeds 32768 nodes/connections");
        }
        for(var link:directLinks){
            var from=faces.get(link.from());var to=faces.get(link.to());
            if(from==null||!from.mapping())continue;
            if(from.kind()!=8||to==null||to.kind()!=9||!ChannelMapping.parse(from.id()).equivalent(ChannelMapping.parse(to.id())))
                throw new IllegalArgumentException("Inter-network bundle mapping rules must match");
            for(var id:ChannelMapping.parse(from.id()).targets())links.add(new Link(new Key(link.from(),id),new Key(link.to(),id)));
        }
        return new Plan(List.copyOf(ports),Collections.unmodifiableSet(nodes),List.copyOf(links),Set.copyOf(boundaries));
    }
}
