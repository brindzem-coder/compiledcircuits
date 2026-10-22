package com.example.compiledcircuits.network;

import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Consumer;

/** Server-owned derived port table. No block reads, redstone polling, or persisted object references. */
final class DirectLinks {
    private record Key(String dimension, BlockPos pos, Direction side) {
        Key opposite(){return new Key(dimension,pos.relative(side),side.getOpposite());}
    }
    private record Endpoint(CompiledNetwork network, CompiledNetwork.Port port) {}
    private record Sample(Map<BlockPos,Boolean> values,boolean ready) {}
    private static final class Group {
        final Set<CompiledNetwork> members;
        LogicProgram program;
        String error="";
        Group(Set<CompiledNetwork> members){this.members=members;}
    }
    private final Map<Key,Endpoint> ports=new HashMap<>();
    private final Map<CompiledNetwork,Sample> samples=new IdentityHashMap<>();
    private final Map<CompiledNetwork,Group> groups=new IdentityHashMap<>();
    private final LinkedHashSet<CompiledNetwork> dirty=new LinkedHashSet<>();
    private final Consumer<CompiledNetwork> changed;
    private final NetworkSavedData data;
    DirectLinks(NetworkSavedData data,Consumer<CompiledNetwork> changed){this.data=data;this.changed=changed;}
    void clear(){ports.clear();samples.clear();groups.clear();dirty.clear();}
    boolean contains(CompiledNetwork n){return samples.containsKey(n);}
    void optimizationChanged(CompiledNetwork n) { if (contains(n)) topologyChanged(Set.of(n)); }
    private Key key(CompiledNetwork n,CompiledNetwork.Port p){return new Key(n.getDimension(),p.pos(),p.side());}
    private Endpoint peer(CompiledNetwork n,CompiledNetwork.Port p){
        var other=ports.get(key(n,p).opposite());
        if (other == null || other.network == n || other.port.input() == p.input()) return null;
        var left = n.getElementAt(p.pos()); var right = other.network.getElementAt(other.port.pos());
        if (left != null && right != null && left.getBlockId().equals("compiledcircuits:id_wire") && right.getBlockId().equals("compiledcircuits:id_wire") && !left.wireId().equals(right.wireId())) return null;
        return other;
    }
    void add(CompiledNetwork n){
        if(n.directPorts().isEmpty())return;
        samples.put(n,new Sample(Map.of(),false));n.directAvailability(false);
        for(var p:n.directPorts())ports.put(key(n,p),new Endpoint(n,p));
        var affected=new HashSet<CompiledNetwork>();affected.add(n);
        for(var p:n.directPorts()){var other=peer(n,p);if(other!=null)affected.add(other.network);}
        topologyChanged(affected);
    }
    void remove(CompiledNetwork n){
        if(samples.remove(n)==null)return;
        var affected=new HashSet<CompiledNetwork>();affected.add(n);
        for(var p:n.directPorts()){var other=peer(n,p);if(other!=null)affected.add(other.network);}
        var oldGroup=groups.get(n);
        if(oldGroup!=null)affected.addAll(oldGroup.members);
        if(oldGroup!=null)for(var member:oldGroup.members)if(member!=n){
            member.directAvailability(false);
            if(member.applyDirect(Map.of(),null,""))changed.accept(member);
        }
        // Remove every endpoint synchronously, before deferred membership retirement.
        for(var p:n.directPorts())ports.remove(key(n,p),new Endpoint(n,p));
        dirty.remove(n);groups.remove(n);topologyChanged(affected);
    }
    private void topologyChanged(Set<CompiledNetwork> affected){
        affected = new HashSet<>(affected);
        for(var n:new ArrayList<>(affected)){var old=groups.get(n);if(old!=null)affected.addAll(old.members);}
        for(var n:affected){groups.remove(n);if(samples.containsKey(n))dirty.add(n);}
    }
    void invalidate(CompiledNetwork n){
        var sample=samples.get(n);if(sample==null)return;
        samples.put(n,new Sample(sample.values,false));dirty.add(n);
        var group=groups.get(n);
        if(group!=null)for(var member:group.members){member.directAvailability(false);if(member.applyDirect(Map.of(),null,""))changed.accept(member);}
    }
    void update(CompiledNetwork n,Map<BlockPos,Boolean> values){
        var old=samples.get(n);if(old==null)return;
        var next=new Sample(Map.copyOf(values),n.directReady());samples.put(n,next);
        if(!next.equals(old))dirty.add(n);
    }
    boolean hasWork(){return !dirty.isEmpty();}
    int linkCount(){int count=0;for(var entry:ports.values())if(!entry.port.input()&&peer(entry.network,entry.port)!=null)count++;return count;}
    private Group group(CompiledNetwork root){
        var cached=groups.get(root);if(cached!=null)return cached;
        Set<CompiledNetwork> members=new TreeSet<>(Comparator.comparingInt(CompiledNetwork::getId));
        ArrayDeque<CompiledNetwork> pending=new ArrayDeque<>();members.add(root);pending.add(root);
        while(!pending.isEmpty()){var current=pending.remove();for(var p:current.directPorts()){
            var other=peer(current,p);
            if(other!=null&&members.add(other.network))pending.add(other.network);
        }}
        var result=new Group(members);
        try {
            Map<BlockPos,String> wireIds=new HashMap<>();
            Map<BlockPos,BlockState> states=new HashMap<>();Map<BlockPos,Integer> owners=new HashMap<>();
            Map<BlockPos,Set<BlockPos>> links=new HashMap<>();Map<String,Boolean> memory=new HashMap<>();
            for(var n:members){
                wireIds.putAll(n.wireIds());
                for(var e:n.getElements()){
                    if(states.size()>=LogicProgram.MAX_ELEMENTS)throw new IllegalArgumentException("Linked circuits exceed "+LogicProgram.MAX_ELEMENTS+" elements");
                    states.put(e.getPos(),e.resolveState().state().orElseThrow());owners.put(e.getPos(),n.getId());
                }
                memory.putAll(n.nodeMemory());
                for(var p:n.directPorts())if(!p.input()){
                    var target=peer(n,p);if(target!=null)links.computeIfAbsent(p.pos(),k->new HashSet<>()).add(target.port.pos());
                }
            }
            result.program=LogicProgram.compile(states,links,owners,wireIds);result.program.importNodeMemory(memory);
            // A network-level cycle is deliberately conservative: table contraction must not alter settling order.
            Map<CompiledNetwork, Set<CompiledNetwork>> successors = new IdentityHashMap<>();
            for (var n : members) {
                var next = new HashSet<CompiledNetwork>();
                for (var p : n.directPorts()) if (!p.input()) { var target = peer(n, p); if (target != null) next.add(target.network); }
                successors.put(n, next);
            }
            for (var n : members) if (n.networkTable() != null) {
                var seen = new HashSet<CompiledNetwork>(); var queue = new ArrayDeque<>(successors.get(n)); boolean cycle = false;
                while (!queue.isEmpty()) { var next = queue.remove(); if (next == n) { cycle = true; break; } if (seen.add(next)) queue.addAll(successors.get(next)); }
                if (cycle) { n.tableStatus("Saved table suspended: inter-network cycle; using graph"); continue; }
                try {
                    var region = new HashSet<BlockPos>(); for (var e : n.getElements()) region.add(e.getPos());
                    result.program.installTable(n.networkTable(), region); n.tableStatus("Saved table active in linked graph");
                } catch (IllegalArgumentException unavailable) { n.tableStatus(unavailable.getMessage() + "; using graph"); }
            }
            Set<BlockPos> optimized = new HashSet<>();
            for (var n : members) if (n.isOptimized()) for (var e : n.getElements()) optimized.add(e.getPos());
            if (!optimized.isEmpty()) result.program.optimize(optimized);
        }catch(RuntimeException failure){result.error="Direct links: "+failure.getMessage();}
        for(var n:members)groups.put(n,result);
        return result;
    }
    void step(){
        if(dirty.isEmpty())return;var first=dirty.iterator().next();
        if(!samples.containsKey(first)){dirty.remove(first);return;}
        var group=group(first);dirty.removeAll(group.members);
        boolean ready=group.members.stream().allMatch(n->samples.get(n).ready&&n.directReady());
        Map<BlockPos,Boolean> result=Map.of();Map<String,Boolean> memory=null;String error=group.error;
        if(ready&&error.isEmpty())try{
            Map<BlockPos,Boolean> inputs=new HashMap<>();for(var n:group.members)inputs.putAll(samples.get(n).values);
            result=group.program.evaluate(inputs);
            if(group.program.hasStatefulMemory())memory=group.program.nodeMemory();
        }catch(LogicProgram.UnstableFeedback failure){error="Direct links: "+failure.getMessage();}
        List<CompiledNetwork> notifications=new ArrayList<>();
        for(var n:group.members){
            n.directAvailability(ready);
            Map<BlockPos,Boolean> values=new HashMap<>();
            for(var p:n.getOutputs())if(result.containsKey(p))values.put(p,result.get(p));
            for(var p:n.directPorts())if(!p.input()&&result.containsKey(p.pos()))values.put(p.pos(),result.get(p.pos()));
            var before=memory==null?Map.<String,Boolean>of():n.nodeMemory();
            if(n.applyDirect(values,memory,error))notifications.add(n);
            if(memory!=null&&!before.equals(n.nodeMemory()))data.setDirty();
        }
        // Publish the entire stable group before notifying any vanilla observer.
        notifications.forEach(changed);
    }
}
