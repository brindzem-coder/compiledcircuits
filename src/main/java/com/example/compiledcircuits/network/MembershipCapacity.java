package com.example.compiledcircuits.network;

import com.example.compiledcircuits.config.ServerConfig;
import com.example.compiledcircuits.networking.CompiledElementPositionsS2CPacket;
import java.util.*;

/** Derived counts, independent of physical state/chunk loading. Limits are captured for this data session. */
public final class MembershipCapacity {
    public static final int NETWORK_HARD=OperationLimits.ELEMENTS, DIMENSION_HARD=CompiledElementPositionsS2CPacket.MAX_ENTRIES;
    public static final long HEADER_BOUND=1024, RECORD_BOUND=13;
    public enum State { READY, OVER_CAPACITY, UNKNOWN }
    public record Usage(State state,long records,long limit,long encodedUpperBound,long parts,String reason) {}
    private final NetworkSavedData data;
    private final NavigableMap<Integer,CompiledNetwork> ordered=new TreeMap<>();
    public List<CompiledNetwork> page(int after,int limit){return ordered.tailMap(after,false).values().stream().limit(limit).toList();}
    private final Map<String,Long> active=new HashMap<>();
    private final Map<String,PersistentIntMap<CompiledNetwork>> roots=new HashMap<>();
    private final Map<String,Integer> oversized=new HashMap<>();
    private Integer networkLimit,dimensionLimit;
    private long revision,allRevision;
    private final Map<String,Long> revisions=new HashMap<>();
    public long revision(String dimension){return Math.max(allRevision,revisions.getOrDefault(dimension,0L));}
    void invalidateAll(){allRevision=++revision;}
    private void changed(String dimension){revisions.put(dimension,++revision);}
    public MembershipCapacity(NetworkSavedData data){this.data=data;}
    void configure(int network,int dimension){
        if(networkLimit!=null||!active.isEmpty()||network<1||network>NETWORK_HARD||dimension<1||dimension>DIMENSION_HARD)throw new IllegalArgumentException("Invalid initial capacity policy");
        networkLimit=network;dimensionLimit=dimension;
    }
    private void limits(){if(networkLimit==null){
        boolean loaded=ServerConfig.SPEC.isLoaded();
        networkLimit=loaded?ServerConfig.NETWORK_CAPACITY.get():NETWORK_HARD;
        dimensionLimit=loaded?ServerConfig.DIMENSION_CAPACITY.get():DIMENSION_HARD;
        if(networkLimit<1||networkLimit>NETWORK_HARD||dimensionLimit<1||dimensionLimit>DIMENSION_HARD)throw new IllegalStateException("Invalid membership capacity configuration");
    }}
    public int networkLimit(){limits();return networkLimit;}
    public int dimensionLimit(){limits();return dimensionLimit;}
    public static long parts(long count){if(count<0)throw new IllegalArgumentException("Negative capacity");return Math.max(1,Math.addExact(count,CompiledElementPositionsS2CPacket.ENTRIES_PER_PART-1)/CompiledElementPositionsS2CPacket.ENTRIES_PER_PART);}
    public static long encodedUpperBound(long count){return Math.addExact(Math.multiplyExact(count,RECORD_BOUND),Math.multiplyExact(parts(count),HEADER_BOUND));}
    public long activeCount(String dimension){return active.getOrDefault(dimension,0L);}
    public long count(String dimension){return Math.addExact(active.getOrDefault(dimension,0L),data.reservedCapacity(dimension));}
    public int oversizedNetworks(String dimension){return oversized.getOrDefault(dimension,0);}
    public boolean oversized(CompiledNetwork n){return n.getElements().size()>networkLimit();}
    public Usage usage(String dimension){
        long count=count(dimension),bytes=encodedUpperBound(count),parts=parts(count);
        State state=data.hasUnknownMembershipReservations()?State.UNKNOWN:count>dimensionLimit()||count>DIMENSION_HARD
                ||bytes>CompiledElementPositionsS2CPacket.MAX_BYTES||parts>CompiledElementPositionsS2CPacket.MAX_PARTS?State.OVER_CAPACITY:State.READY;
        return new Usage(state,count,dimensionLimit(),bytes,parts,state==State.UNKNOWN?"Unknown isolated membership accounting":state==State.OVER_CAPACITY?"Membership capacity exceeded":"");
    }
    public PersistentIntMap<CompiledNetwork> snapshot(String dimension){return roots.getOrDefault(dimension,new PersistentIntMap<>());}
    void add(CompiledNetwork n){
        limits();ordered.put(n.getId(),n);String d=n.getDimension();changed(d);active.put(d,Math.addExact(active.getOrDefault(d,0L),n.getElements().size()));
        roots.put(d,snapshot(d).put(n.getId(),n));if(oversized(n))oversized.merge(d,1,Integer::sum);
    }
    void remove(CompiledNetwork n){
        ordered.remove(n.getId());String d=n.getDimension();changed(d);active.put(d,Math.subtractExact(active.getOrDefault(d,0L),n.getElements().size()));roots.put(d,snapshot(d).remove(n.getId()));
        if(oversized(n))oversized.merge(d,-1,Integer::sum);
    }
    public void check(Collection<CompiledNetwork> added,CompiledNetwork removed){
        limits();Map<String,Long> delta=new HashMap<>();
        if(removed!=null)delta.put(removed.getDimension(),-(long)removed.getElements().size());
        for(var n:added){
            if(n.getDimension().length()>256)throw new NetworkSavedData.AdmissionException("Dimension identifier exceeds membership protocol limit of 256 characters.",NetworkOperations.Code.TOO_LARGE);
            long size=n.getElements().size();
            if(size>networkLimit()&&!(removed!=null&&removed.getId()==n.getId()&&size<removed.getElements().size()))
                throw failure("network",n.getDimension(),removed==null?0:removed.getElements().size(),size,networkLimit());
            delta.merge(n.getDimension(),size,Math::addExact);
        }
        for(var e:delta.entrySet()){
            long before=count(e.getKey()),after=Math.addExact(before,e.getValue());
            if(data.hasUnknownMembershipReservations()&&e.getValue()>0)throw new IllegalStateException("UNKNOWN membership accounting; resolve isolated records before increasing capacity.");
            boolean fits=after<=dimensionLimit()&&after<=DIMENSION_HARD&&parts(after)<=CompiledElementPositionsS2CPacket.MAX_PARTS
                    &&encodedUpperBound(after)<=CompiledElementPositionsS2CPacket.MAX_BYTES;
            if(!fits&&!(before>after&&usage(e.getKey()).state()!=State.READY)){
                long released=removed!=null&&removed.getDimension().equals(e.getKey())?removed.getElements().size():0;
                throw failure("dimension",e.getKey(),before,Math.addExact(e.getValue(),released),dimensionLimit(),released);
            }
        }
    }
    private static NetworkSavedData.AdmissionException failure(String scope,String dimension,long current,long added,long limit){
        return failure(scope,dimension,current,added,limit,0);
    }
    private static NetworkSavedData.AdmissionException failure(String scope,String dimension,long current,long added,long limit,long released){
        return new NetworkSavedData.AdmissionException("Membership "+scope+" limit in "+dimension+": current="+current+", removed="+released+", candidate="+added+", limit="+limit+", remaining="+Math.max(0,Math.subtractExact(limit,Math.subtractExact(current,released)))+".",NetworkOperations.Code.TOO_LARGE);
    }
}
