package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.CircuitElementType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Global damage stream; dimension filtering is a derived client view, never a second stream. */
public final class DamageProtocol {
    public static final int PART_BYTES=32*1024, MAX_PARTS=2048, BATCH_BYTES=64*1024*1024;
    public static final int STATE_BYTES=BATCH_BYTES-MAX_PARTS*133;
    public static final int MAX_RECORDS=1_000_000, MAX_PENDING=8192, MAX_PENDING_BATCHES=8;
    public static final int JOURNAL_BYTES=8*1024*1024, ENCODED_BYTES=128*1024*1024, MAX_CLIENTS=64;
    public static final int BUILD_STEPS=1024, PARTS_PER_PLAYER=2, PARTS_PER_TICK=8;
    public static final int RESYNC_TICKS=40, IDLE_TIMEOUT=200, ASSEMBLY_TIMEOUT=20000;
    public static final int NAME=64, IDENTIFIER=256, PATH=1024, CHAT_TICKS=20;
    public enum Kind { SNAPSHOT, DELTA, OVER_CAPACITY }
    public enum Op { META, UPSERT, REMOVE, REMOVE_NETWORK }
    public record Meta(int id, String name, int folder, String dimension, String path) {
        public Meta {
            if(id<=0 || folder<0 || name==null || name.length()>NAME || path==null || path.length()>PATH
                    || dimension==null || dimension.length()>IDENTIFIER || ResourceLocation.tryParse(dimension)==null)
                throw new IllegalArgumentException("Invalid damage metadata");
        }
    }
    public record Element(int network, int id, BlockPos pos, CircuitElementType type,
                          String expected, String actual, long detectedAt) {
        public Element {
            if(network<=0 || id<=0 || pos==null || type==null || !validId(expected) || !validId(actual))
                throw new IllegalArgumentException("Invalid damage element");
            pos=pos.immutable();
        }
    }
    public record Change(Op op,int network,int element,Meta meta,Element value) {
        public Change {
            if(op==null || network<=0 || (op==Op.UPSERT || op==Op.REMOVE) && element<=0
                    || op==Op.META && (meta==null || meta.id()!=network)
                    || op==Op.UPSERT && (value==null || value.network()!=network || value.id()!=element))
                throw new IllegalArgumentException("Invalid damage change");
        }
        public static Change meta(Meta m){return new Change(Op.META,m.id(),0,m,null);}
        public static Change upsert(Element e){return new Change(Op.UPSERT,e.network(),e.id(),null,e);}
        public static Change remove(int n,int e){return new Change(Op.REMOVE,n,e,null,null);}
        public static Change removeNetwork(int n){return new Change(Op.REMOVE_NETWORK,n,0,null,null);}
        public long key(){return ((long)network<<32)|(element&0xffffffffL);}
    }
    private DamageProtocol() {}
    private static boolean validId(String id){return id!=null && id.length()<=IDENTIFIER && ResourceLocation.tryParse(id)!=null;}
    public static void write(Change c,FriendlyByteBuf b){
        b.writeEnum(c.op);b.writeVarInt(c.network);
        switch(c.op){
            case META -> {var m=c.meta;b.writeUtf(m.name,NAME);b.writeVarInt(m.folder);b.writeUtf(m.dimension,IDENTIFIER);b.writeUtf(m.path,PATH);}
            case REMOVE -> b.writeVarInt(c.element);
            case UPSERT -> {var e=c.value;b.writeVarInt(e.id);b.writeBlockPos(e.pos);b.writeEnum(e.type);b.writeUtf(e.expected,IDENTIFIER);b.writeUtf(e.actual,IDENTIFIER);b.writeLong(e.detectedAt);}
            case REMOVE_NETWORK -> { }
        }
    }
    public static int encodedSize(Change change){
        var b=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{write(change,b);return b.readableBytes();}finally{b.release();}
    }
    public static Change read(FriendlyByteBuf b){
        var op=b.readEnum(Op.class);int n=b.readVarInt();
        return switch(op){
            case META -> Change.meta(new Meta(n,b.readUtf(NAME),b.readVarInt(),b.readUtf(IDENTIFIER),b.readUtf(PATH)));
            case REMOVE -> Change.remove(n,b.readVarInt());
            case UPSERT -> Change.upsert(new Element(n,b.readVarInt(),b.readBlockPos(),b.readEnum(CircuitElementType.class),b.readUtf(IDENTIFIER),b.readUtf(IDENTIFIER),b.readLong()));
            case REMOVE_NETWORK -> Change.removeNetwork(n);
        };
    }
}
