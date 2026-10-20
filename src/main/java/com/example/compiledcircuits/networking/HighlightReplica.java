package com.example.compiledcircuits.networking;

import net.minecraft.core.BlockPos;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;
import static com.example.compiledcircuits.networking.HighlightProtocol.*;

/** Atomic selection staging; safe to exercise without loading Minecraft client classes. */
public final class HighlightReplica {
    public enum Status { EMPTY, WAITING, READY, ERROR }
    public record Selection(Set<BlockPos> wires,Set<BlockPos> inputs,Set<BlockPos> outputs) {
        public static final Selection EMPTY=new Selection(Set.of(),Set.of(),Set.of());
    }
    private Request expected;private long started,lastBatch;private boolean terminal;
    private Status status=Status.EMPTY;private String message="";private Selection selection=Selection.EMPTY;
    private Assembly assembly;
    private static final class Assembly {
        final NetworkHighlightS2CPacket h;final byte[][] parts;
        final Set<BlockPos> all=new HashSet<>(),wires=new HashSet<>(),inputs=new HashSet<>(),outputs=new HashSet<>();
        int receivedBytes,next,offset;long progress;
        Assembly(NetworkHighlightS2CPacket h,long tick){this.h=h;parts=new byte[h.parts()][];progress=tick;}
    }
    public void reset(){expected=null;assembly=null;selection=Selection.EMPTY;status=Status.EMPTY;message="";lastBatch=0;terminal=false;}
    public void expect(Request r,long now){reset();expected=r;started=now;status=Status.WAITING;}
    public Status status(){return status;}
    public String message(){return message;}
    public Selection selection(){return selection;}
    public int stagingBytes(){return assembly==null?0:assembly.receivedBytes;}
    public long batch(){return lastBatch;}
    private void fail(String reason){assembly=null;selection=Selection.EMPTY;status=Status.ERROR;terminal=true;message=reason;}
    public void accept(NetworkHighlightS2CPacket p,long now){
        if(expected==null||!expected.equals(p.request())||p.batch()<lastBatch)return;
        if(p.state()!=State.READY){lastBatch=p.batch();fail(p.message());return;}
        if(terminal || status==Status.READY || p.batch()==lastBatch&&assembly==null)return;
        if(assembly==null){assembly=new Assembly(p,now);lastBatch=p.batch();}
        var a=assembly;var h=a.h;
        if(p.batch()!=h.batch()||p.revision()!=h.revision()||p.parts()!=h.parts()||p.positions()!=h.positions()
                ||p.bytes()!=h.bytes()||!p.message().equals(h.message())){fail("Inconsistent highlight parts; select again.");return;}
        var bytes=p.payload();var old=a.parts[p.index()];
        if(old!=null){if(!Arrays.equals(old,bytes))fail("Conflicting highlight part; select again.");return;}
        if((long)a.receivedBytes+bytes.length>h.bytes()||a.receivedBytes+bytes.length>BATCH_BYTES){fail("Highlight exceeds byte limit.");return;}
        a.parts[p.index()]=bytes;a.receivedBytes+=bytes.length;a.progress=now;
    }
    public void tick(long now,int steps){
        if(status==Status.WAITING&&now-started>LIFETIME_TICKS){fail("Highlight timed out; select again.");return;}
        var a=assembly;if(a==null)return;
        if(now-a.progress>IDLE_TICKS){fail("Incomplete highlight timed out; select again.");return;}
        try{
            while(steps-->0){
                if(a.next==a.parts.length){
                    if(a.all.size()!=a.h.positions()||a.receivedBytes!=a.h.bytes())throw new IllegalArgumentException();
                    selection=new Selection(Collections.unmodifiableSet(a.wires),Collections.unmodifiableSet(a.inputs),Collections.unmodifiableSet(a.outputs));
                    status=Status.READY;message=a.h.message();assembly=null;return;
                }
                byte[] part=a.parts[a.next];if(part==null)return;
                if(a.offset==part.length){a.offset=0;a.next++;continue;}
                var b=new FriendlyByteBuf(Unpooled.wrappedBuffer(part,a.offset,9));
                try{
                    int role=b.readUnsignedByte();BlockPos pos=b.readBlockPos();
                    if(role>2||!a.all.add(pos)||a.all.size()>a.h.positions())throw new IllegalArgumentException();
                    switch(role){case 0->a.wires.add(pos);case 1->a.inputs.add(pos);case 2->a.outputs.add(pos);}
                }finally{b.release();}
                a.offset+=9;a.progress=now;
            }
        }catch(RuntimeException invalid){fail("Invalid highlight data; select again.");}
    }
}
