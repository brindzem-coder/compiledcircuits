package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.PersistentIntMap;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;
import static com.example.compiledcircuits.networking.DamageProtocol.*;

/** Pure client state machine. Multipart validation/application is bounded and atomically published. */
public final class DamageReplica {
    public enum Status { UNKNOWN, STALE, READY, OVER_CAPACITY }
    public record Network(Meta meta,PersistentIntMap<Element> elements,int bytes) {}
    private PersistentIntMap<Network> root=new PersistentIntMap<>();
    private UUID context,epoch;private long revision=-1,lastBatch;
    private Status status=Status.UNKNOWN;private Assembly assembly;
    private long completedBatch;private int elements,stateBytes;
    private static final class Assembly {
        final DamagePartS2CPacket header;final byte[][] parts;final long started;
        long lastProgress;int bytes,received,next,records,elements,stateBytes;FriendlyByteBuf current;
        int inPart;PersistentIntMap<Network> root;
        final Set<Long> keys=new HashSet<>();final Set<Integer> metas=new HashSet<>(),removedNetworks=new HashSet<>(),elementNetworks=new HashSet<>();
        Assembly(DamagePartS2CPacket p,long now,PersistentIntMap<Network> root,int elements,int stateBytes){
            this.stateBytes=stateBytes;header=p;parts=new byte[p.parts()][];started=lastProgress=now;this.root=root;this.elements=elements;
        }
        void release(){if(current!=null){current.release();current=null;}}
    }
    public void reset(UUID context){discard();this.context=context;epoch=null;revision=-1;lastBatch=completedBatch=0;root=new PersistentIntMap<>();elements=stateBytes=0;status=Status.UNKNOWN;}
    public boolean assembling(){return assembly!=null;}
    public Status status(){return status;}
    public long revision(){return revision;}
    public long completedBatch(){return completedBatch;}
    public int stagingBytes(){return assembly==null?0:assembly.bytes;}
    public Collection<Network> networks(){return root.values();}
    private void discard(){if(assembly!=null)assembly.release();assembly=null;}
    public void stale(){discard();status=Status.STALE;}
    public void accept(DamagePartS2CPacket p,long now){
        if(!p.context().equals(context) || epoch!=null&&!epoch.equals(p.epoch()))return;
        if(epoch==null)epoch=p.epoch();
        if(p.batch()<=lastBatch)return;
        if(p.kind()==Kind.OVER_CAPACITY){discard();lastBatch=p.batch();status=Status.OVER_CAPACITY;return;}
        if(p.revision()<revision || p.kind()==Kind.DELTA&&p.revision()==revision)return;
        if(assembly==null || assembly.header.batch()!=p.batch()){
            if(assembly!=null && (p.batch()<assembly.header.batch() || p.kind()!=Kind.SNAPSHOT))return;
            if(p.kind()==Kind.DELTA && (status!=Status.READY || p.base()!=revision)){stale();return;}
            discard();assembly=new Assembly(p,now,p.kind()==Kind.SNAPSHOT?new PersistentIntMap<>():root,p.kind()==Kind.SNAPSHOT?0:elements,p.kind()==Kind.SNAPSHOT?0:stateBytes);
            status=Status.STALE;
        }
        var a=assembly;var h=a.header;
        if(p.kind()!=h.kind()||p.base()!=h.base()||p.revision()!=h.revision()||p.parts()!=h.parts()||p.records()!=h.records()||p.bytes()!=h.bytes()) {stale();return;}
        byte[] bytes=p.payload();
        if(a.parts[p.index()]!=null){if(!Arrays.equals(a.parts[p.index()],bytes))stale();return;}
        if(a.bytes+bytes.length+128>h.bytes() || a.bytes+bytes.length+128>BATCH_BYTES){stale();return;}
        a.parts[p.index()]=bytes;a.bytes+=bytes.length+128;a.received++;a.lastProgress=now;
    }
    public void tick(long now,int allowance){
        var a=assembly;if(a==null)return;
        if(now-a.lastProgress>IDLE_TIMEOUT || now-a.started>ASSEMBLY_TIMEOUT){stale();return;}
        try{
            while(allowance-->0){
                if(a.current==null){
                    if(a.next==a.parts.length){
                        if(a.records!=a.header.records()||a.bytes!=a.header.bytes())throw new IllegalArgumentException("Batch totals disagree");
                        root=a.root;elements=a.elements;stateBytes=a.stateBytes;revision=a.header.revision();lastBatch=completedBatch=a.header.batch();status=Status.READY;discard();return;
                    }
                    if(a.parts[a.next]==null)return;
                    a.current=new FriendlyByteBuf(Unpooled.wrappedBuffer(a.parts[a.next]));a.inPart=a.current.readVarInt();
                    if(a.inPart<0||a.inPart>512)throw new IllegalArgumentException("Part count");
                }
                if(a.inPart==0){if(a.current.isReadable())throw new IllegalArgumentException("Trailing records");a.release();a.next++;continue;}
                int start=a.current.readerIndex();var c=DamageProtocol.read(a.current);int encoded=a.current.readerIndex()-start;a.inPart--;if(++a.records>MAX_RECORDS)throw new IllegalArgumentException("Record capacity");
                apply(a,c,encoded);a.lastProgress=now;
            }
        }catch(RuntimeException invalid){stale();}
    }
    private static void apply(Assembly a,Change c,int encoded){
        var n=a.root.get(c.network());
        switch(c.op()){
            case META -> {
                if(!a.metas.add(c.network()) || a.elementNetworks.contains(c.network()))throw new IllegalArgumentException("Duplicate/out-of-order metadata");
                int difference=encoded-(n==null?0:DamageProtocol.encodedSize(Change.meta(n.meta)));
                a.stateBytes+=difference;
                a.root=a.root.put(c.network(),new Network(c.meta(),n==null?new PersistentIntMap<>():n.elements,(n==null?0:n.bytes)+difference));
            }
            case REMOVE_NETWORK -> {
                if(a.header.kind()==Kind.SNAPSHOT || !a.removedNetworks.add(c.network()) || a.metas.contains(c.network()) || a.elementNetworks.contains(c.network()))throw new IllegalArgumentException("Conflicting network removal");
                if(n!=null){a.elements-=n.elements.size();a.stateBytes-=n.bytes;a.root=a.root.remove(c.network());}
            }
            case UPSERT,REMOVE -> {
                if(!a.keys.add(c.key()) || n==null || a.header.kind()==Kind.SNAPSHOT&&c.op()==Op.REMOVE)throw new IllegalArgumentException("Duplicate/unknown element");
                a.elementNetworks.add(c.network());
                var previous=n.elements.get(c.element());
                int difference=(c.op()==Op.UPSERT?encoded:0)-(previous==null?0:DamageProtocol.encodedSize(Change.upsert(previous)));
                a.stateBytes+=difference;
                var updated=c.op()==Op.UPSERT?n.elements.put(c.element(),c.value()):n.elements.remove(c.element());
                a.elements+=updated.size()-n.elements.size();
                if(a.elements>MAX_RECORDS)throw new IllegalArgumentException("State capacity");
                a.root=a.root.put(c.network(),new Network(n.meta,updated,n.bytes+difference));
            }
        }
        if((long)a.root.size()+a.elements>MAX_RECORDS||a.stateBytes>STATE_BYTES)throw new IllegalArgumentException("Metadata capacity");
    }
}
