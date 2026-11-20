package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Compile-only bus expansion. Channel domains are scoped to connected gate/cable islands. */
public final class BundledGates {
    public static boolean gate(BlockState state){return state.getBlock() instanceof GateBodyBlock || state.getBlock() instanceof NotGateBlock || state.getBlock() instanceof DLatchBlock;}
    public static int role(BlockState state,Direction side){
        if(state.getBlock() instanceof GateBodyBlock)return GateBodyBlock.port(state,side)==1?1:GateBodyBlock.port(state,side)==2?2:0;
        if(state.getBlock() instanceof NotGateBlock || state.getBlock() instanceof DLatchBlock){
            if(side==state.getValue(EndpointBlock.FACING))return 2;
            if(side==state.getValue(EndpointBlock.FACING).getOpposite())return 1;
            if(state.getBlock() instanceof DLatchBlock&&side==DLatchBlock.enableSide(state))return 3;
        }
        return 0;
    }
    record Attachment(BundleFace gateFace,BundleFace cableFace,int cable) {}
    record Group(BlockPos root,BlockState state,List<BundleFace> pins,List<Attachment> attachments,SortedSet<String> channels) {}
    record Key(BundleFace face,String channel) {}
    record Plan(List<Group> groups,List<BundleTopology.Port> ports) {}
    private static int root(Map<Integer,Integer> parents,int value){int r=value;while(parents.getOrDefault(r,r)!=r)r=parents.get(r);while(value!=r){int next=parents.getOrDefault(value,value);parents.put(value,r);value=next;}return r;}
    static Plan plan(Map<BlockPos,BlockState> states,Map<BundleFace,BundleWireEntity.Port> faces,
            Map<BundleFace,Integer> cables,List<BundleTopology.Port> ports,Map<BlockPos,Integer> owners){
        Set<BlockPos> visited=new HashSet<>();List<Group> groups=new ArrayList<>();Map<Integer,Integer> parents=new HashMap<>();
        for(var start:states.keySet().stream().sorted().toList()){
            var state=states.get(start);if(!gate(state)||!visited.add(start))continue;
            var queue=new ArrayDeque<BlockPos>();queue.add(start);var pins=new ArrayList<BundleFace>();var legacyPorts=new ArrayList<BlockPos>();var attachments=new ArrayList<Attachment>();
            while(!queue.isEmpty()){
                var pos=queue.remove();var current=states.get(pos);
                for(var side:Direction.values()){
                    var next=pos.relative(side);var other=states.get(next);
                    if(current.getBlock() instanceof GateBodyBlock body&&other!=null&&other.getBlock() instanceof GateBodyBlock peer
                            &&body.kind()==peer.kind()&&GateBodyBlock.port(current,side)!=3&&GateBodyBlock.port(other,side.getOpposite())!=3
                            &&Objects.equals(owners.get(pos),owners.get(next))){if(visited.add(next))queue.add(next);continue;}
                    if(other!=null&&other.getBlock() instanceof GatePortBlock&&Objects.equals(owners.get(pos),owners.get(next)))legacyPorts.add(next);
                    if(role(current,side)==0)continue;
                    var pin=new BundleFace(pos,side);pins.add(pin);var cableFace=new BundleFace(next,side.getOpposite());var cable=cables.get(cableFace);
                    if(cable!=null&&!faces.containsKey(cableFace)&&Objects.equals(Crossings.owner(owners,pos,side),Crossings.owner(owners,next,side.getOpposite())))attachments.add(new Attachment(pin,cableFace,cable));
                }
            }
            if(attachments.isEmpty())continue;
            if(!legacyPorts.isEmpty())throw new IllegalArgumentException("Bundled gates require surface gate ports at "+start.toShortString());
            Set<BundleFace> bundled=new HashSet<>();attachments.forEach(a->bundled.add(a.gateFace));
            for(var pin:pins)if(!bundled.contains(pin)&&role(states.get(pin.pos()),pin.side())!=3){
                var other=states.get(pin.pos().relative(pin.side()));
                if(other!=null&&Objects.equals(Crossings.owner(owners,pin.pos(),pin.side()),Crossings.owner(owners,pin.pos().relative(pin.side()),pin.side().getOpposite()))&&other.getBlock() instanceof IWireConnectable connect&&connect.canWireConnect(other,pin.side().getOpposite()))
                    throw new IllegalArgumentException("Do not mix ordinary and bundled inputs on one gate at "+start.toShortString()+"; use bundle channel ports to convert");
            }
            int first=attachments.get(0).cable();for(var a:attachments){int x=root(parents,first),y=root(parents,a.cable());if(x!=y)parents.put(Math.max(x,y),Math.min(x,y));}
            pins.sort(Comparator.comparing(BundleFace::pos).thenComparing(BundleFace::side));
            groups.add(new Group(start,state,List.copyOf(pins),List.copyOf(attachments),new TreeSet<>()));
        }
        if(groups.isEmpty())return new Plan(List.of(),List.of());
        Map<Integer,SortedSet<String>> domains=new HashMap<>();
        for(var port:ports){var cablePos=port.inline()?port.pos():port.pos().relative(port.face().side().getOpposite());
            var cable=cables.get(new BundleFace(cablePos,port.face().side()));if(cable!=null)domains.computeIfAbsent(root(parents,cable),k->new TreeSet<>()).add(port.config().id());}
        List<BundleTopology.Port> virtual=new ArrayList<>();long expanded=0;
        for(var group:groups){
            group.channels.addAll(domains.getOrDefault(root(parents,group.attachments.get(0).cable()),Collections.emptySortedSet()));
            if(group.channels.isEmpty())throw new IllegalArgumentException("Bundled gate needs at least one configured channel ID at "+group.root.toShortString());
            expanded+=(long)group.channels.size()*(group.pins.size()+3);
            if(expanded>32768)throw new IllegalArgumentException("Bundled gate expansion exceeds 32768 logic nodes");
            for(var channel:group.channels)for(var a:group.attachments){boolean output=role(states.get(a.gateFace.pos()),a.gateFace.side())==2;
                virtual.add(new BundleTopology.Port(a.cableFace,new BundleWireEntity.Port(output?1:2,channel),true,-1,true));}
        }
        return new Plan(List.copyOf(groups),List.copyOf(virtual));
    }
}
