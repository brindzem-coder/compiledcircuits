package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Compile-only bus expansion. Channel presence flows from producers through gate inputs to outputs. */
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
    record Group(BlockPos root,BlockState state,List<BundleFace> pins,List<Attachment> attachments,SortedSet<String> channels,Map<BundleFace,Set<String>> inputChannels) {}
    record Key(BundleFace face,String channel) {}
    record Plan(List<Group> groups,List<BundleTopology.Port> ports) {}
    static Plan plan(Map<BlockPos,BlockState> states,Map<BundleFace,BundleWireEntity.Port> faces,
            Map<BundleFace,Integer> cables,List<BundleTopology.Port> ports,Map<BlockPos,Integer> owners){
        Set<BlockPos> visited=new HashSet<>();List<Group> groups=new ArrayList<>();
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
            for(var pin:pins)if(!bundled.contains(pin)&&role(states.get(pin.pos()),pin.side())==2){
                var other=states.get(pin.pos().relative(pin.side()));
                if(other!=null&&Objects.equals(Crossings.owner(owners,pin.pos(),pin.side()),Crossings.owner(owners,pin.pos().relative(pin.side()),pin.side().getOpposite()))&&other.getBlock() instanceof IWireConnectable connect&&connect.canWireConnect(other,pin.side().getOpposite()))
                    throw new IllegalArgumentException("Bundled gate outputs require bundled wire at "+start.toShortString()+"; extract an ID with a bundle output port");
            }
            pins.sort(Comparator.comparing(BundleFace::pos).thenComparing(BundleFace::side));
            groups.add(new Group(start,state,List.copyOf(pins),List.copyOf(attachments),new TreeSet<>(),new HashMap<>()));
        }
        if(groups.isEmpty())return new Plan(List.of(),List.of());
        // Only producers introduce IDs. Consumers never create a channel on an input cable.
        Map<Integer,Set<String>> domains=new HashMap<>();
        Map<Integer,List<Group>> readers=new HashMap<>();
        for(var group:groups)for(var a:group.attachments())
            if(role(states.get(a.gateFace().pos()),a.gateFace().side())!=2)
                readers.computeIfAbsent(a.cable(),k->new ArrayList<>()).add(group);
        record Channel(int cable,String id) {}
        ArrayDeque<Channel> pending=new ArrayDeque<>();
        for(var port:ports)if(port.config().input()){
            var cablePos=port.inline()?port.pos():port.pos().relative(port.face().side().getOpposite());
            var cable=cables.get(new BundleFace(cablePos,port.face().side()));
            if(cable!=null&&domains.computeIfAbsent(cable,k->new HashSet<>()).add(port.config().id()))pending.add(new Channel(cable,port.config().id()));
        }
        long expansion=0;
        while(!pending.isEmpty()){
            var channel=pending.remove();
            for(var group:readers.getOrDefault(channel.cable(),List.of()))if(group.channels().add(channel.id())){
                expansion+=group.pins().size()+3;
                if(expansion>32768)throw new IllegalArgumentException("Bundled gate expansion exceeds 32768 logic nodes");
                for(var a:group.attachments())if(role(states.get(a.gateFace().pos()),a.gateFace().side())==2
                        &&domains.computeIfAbsent(a.cable(),k->new HashSet<>()).add(channel.id()))pending.add(new Channel(a.cable(),channel.id()));
            }
        }
        for(var group:groups)for(var a:group.attachments())if(role(states.get(a.gateFace().pos()),a.gateFace().side())!=2)
            group.inputChannels().put(a.gateFace(),Set.copyOf(domains.getOrDefault(a.cable(),Set.of())));
        List<BundleTopology.Port> virtual=new ArrayList<>();
        for(var group:groups){
            if(group.channels.isEmpty())throw new IllegalArgumentException("Bundled gate needs a channel source on an input at "+group.root.toShortString());
            for(var channel:group.channels)for(var a:group.attachments){boolean output=role(states.get(a.gateFace.pos()),a.gateFace.side())==2;
                if(!output&&!group.inputChannels().get(a.gateFace()).contains(channel))continue;
                virtual.add(new BundleTopology.Port(a.cableFace,new BundleWireEntity.Port(output?1:2,channel),true,-1,true));}
        }
        return new Plan(List.copyOf(groups),List.copyOf(virtual));
    }
}
