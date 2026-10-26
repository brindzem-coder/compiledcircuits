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
    private long revision;
    long revision() { return revision; }
    /** Validate the prospective linked graph without publishing endpoints or changing runtime state. */
    long validateCompilation(CompiledNetwork candidate) {
        if (candidate.directPorts().isEmpty()) return revision;
        var overlay = new HashMap<Key, Endpoint>();
        for (var p : candidate.directPorts()) overlay.put(key(candidate, p), new Endpoint(candidate, p));
        java.util.function.BiFunction<CompiledNetwork, CompiledNetwork.Port, Endpoint> lookup = (network, port) -> {
            var key = key(network, port).opposite();
            var other = overlay.getOrDefault(key, ports.get(key));
            if (other == null || other.network == network || other.port.input() == port.input()) return null;
            // Explicit output/input ports cross network boundaries independently of local wire IDs.
            return other;
        };
        Set<CompiledNetwork> members = Collections.newSetFromMap(new IdentityHashMap<>());
        var queue = new ArrayDeque<CompiledNetwork>(); queue.add(candidate); members.add(candidate);
        int count = 0;
        while (!queue.isEmpty()) {
            var network = queue.remove(); count += network.getElements().size();
            if (count > LogicProgram.MAX_ELEMENTS) throw new IllegalArgumentException("Linked circuits exceed " + LogicProgram.MAX_ELEMENTS + " elements");
            for (var port : network.directPorts()) {
                var other = lookup.apply(network, port);
                if (other != null && members.add(other.network)) queue.add(other.network);
            }
        }
        // Preserve existing feedback semantics when the linked graph has no bundled channels.
        if (members.stream().noneMatch(n -> n.getElements().stream().anyMatch(e -> e.getBlockId().startsWith("compiledcircuits:bundle")))) return revision;
        Map<BlockPos, BlockState> states = new HashMap<>(); Map<BlockPos, String> ids = new HashMap<>();
        Crossings.Owners owners = new Crossings.Owners(); List<BundleFace.Link> faceLinks = new ArrayList<>();
        Map<BundleFace, com.example.compiledcircuits.block.BundleWireEntity.Port> faces = new HashMap<>(); int owner = 0;
        for (var network : members) {
            owner++; Crossings.mergeGraph(states,ids,network); faces.putAll(network.bundleFaces());
            for (var e : network.getElements()) { owners.add(e, owner); }
            for (var port : network.directPorts()) if (!port.input()) {
                var other = lookup.apply(network, port);
                if (other != null) faceLinks.add(new BundleFace.Link(new BundleFace(port.pos(), port.side()), new BundleFace(other.port.pos(), other.port.side())));
            }
        }
        LogicProgram.compile(states, Map.of(), owners, ids, faces, faceLinks);
        return revision;
    }
    DirectLinks(NetworkSavedData data,Consumer<CompiledNetwork> changed){this.data=data;this.changed=changed;}
    void clear(){ports.clear();samples.clear();groups.clear();dirty.clear();}
    boolean contains(CompiledNetwork n){return samples.containsKey(n);}
    void optimizationChanged(CompiledNetwork n) { if (contains(n)) topologyChanged(Set.of(n)); }
    private Key key(CompiledNetwork n,CompiledNetwork.Port p){return new Key(n.getDimension(),p.pos(),p.side());}
    private Endpoint peer(CompiledNetwork n,CompiledNetwork.Port p){
        var other=ports.get(key(n,p).opposite());
        if (other == null || other.network == n || other.port.input() == p.input()) return null;
        // Wire IDs isolate passive connections inside a network, not explicit inter-network ports.
        return other;
    }
    void add(CompiledNetwork n){
        if(n.directPorts().isEmpty())return;
        revision++;
        samples.put(n,new Sample(Map.of(),false));n.directAvailability(false);
        for(var p:n.directPorts())ports.put(key(n,p),new Endpoint(n,p));
        var affected=new HashSet<CompiledNetwork>();affected.add(n);
        for(var p:n.directPorts()){var other=peer(n,p);if(other!=null)affected.add(other.network);}
        topologyChanged(affected);
    }
    void remove(CompiledNetwork n){
        if(samples.remove(n)==null)return;
        revision++;
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
            Map<BlockPos,BlockState> states=new HashMap<>();Crossings.Owners owners=new Crossings.Owners();
            List<BundleFace.Link> faceLinks=new ArrayList<>();Map<String,Boolean> memory=new HashMap<>();
            Map<BundleFace, com.example.compiledcircuits.block.BundleWireEntity.Port> faces=new HashMap<>();
            for(var n:members){
                Crossings.mergeGraph(states,wireIds,n); faces.putAll(n.bundleFaces());
                for(var e:n.getElements()){
                    if(states.size()>=LogicProgram.MAX_ELEMENTS)throw new IllegalArgumentException("Linked circuits exceed "+LogicProgram.MAX_ELEMENTS+" elements");
                    owners.add(e,n.getId());
                }
                memory.putAll(n.nodeMemory());
                for(var p:n.directPorts())if(!p.input()){
                    var target=peer(n,p);if(target!=null)faceLinks.add(new BundleFace.Link(new BundleFace(p.pos(),p.side()),new BundleFace(target.port.pos(),target.port.side())));
                }
            }
            result.program=LogicProgram.compile(states,Map.of(),owners,wireIds,faces,faceLinks);result.program.importNodeMemory(memory);
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
        // A disconnected transport input is unavailable, rather than a valid LOW that a gate could invert.
        ready &= group.members.stream().allMatch(n -> n.directPorts().stream().noneMatch(p -> p.input()
                && (n.getElementAt(p.pos()).getBlockId().equals("compiledcircuits:bundle_network_input") || n.getElementAt(p.pos()).getBlockId().equals("compiledcircuits:bundled_wire")) && peer(n, p) == null));
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
