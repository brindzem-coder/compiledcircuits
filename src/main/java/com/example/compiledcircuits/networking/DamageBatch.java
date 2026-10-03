package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.DamageLedger;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static com.example.compiledcircuits.networking.DamageProtocol.*;

/** Encoded once, shared by every receiver. Builder consumes one cursor step per budget unit. */
public final class DamageBatch {
    public final Kind kind;public final long base,revision,id;
    private final List<byte[]> parts;
    public final int bytes,records;
    private DamageBatch(Kind kind,long base,long revision,long id,List<byte[]> parts,int bytes,int records){
        this.kind=kind;this.base=base;this.revision=revision;this.id=id;this.parts=List.copyOf(parts);this.bytes=bytes;this.records=records;
    }
    public int partCount(){return parts.size();}
    public DamagePartS2CPacket packet(UUID context,UUID epoch,int part){return new DamagePartS2CPacket(context,epoch,kind,id,base,revision,part,parts.size(),records,bytes,parts.get(part));}
    public static final class Builder {
        private final DamageLedger.Cursor cursor;private final Kind kind;private final long base,revision,id;
        private final List<byte[]> parts=new ArrayList<>();private final ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        private int count,totalRecords,totalBytes,rawBytes;private boolean done;private DamageBatch result;
        public Builder(DamageLedger.Cursor cursor,Kind kind,long base,long revision,long id){this.cursor=cursor;this.kind=kind;this.base=base;this.revision=revision;this.id=id;}
        public boolean done(){return done;}
        public long revision(){return revision;}
        public int retainedBytes(){return totalBytes+buffer.size()+133;}
        public DamageBatch result(){if(!done)throw new IllegalStateException("Incomplete batch");return result;}
        public void step(){
            if(done)return;
            if(cursor.done()){
                if(count>0 || parts.isEmpty())finishPart();
                result=new DamageBatch(kind,base,revision,id,parts,totalBytes,totalRecords);done=true;return;
            }
            var change=cursor.step();if(change==null)return;
            var encoded=new FriendlyByteBuf(Unpooled.buffer());
            try{
                DamageProtocol.write(change,encoded);int size=encoded.readableBytes();
                if((rawBytes+=size)>STATE_BYTES)throw new IllegalArgumentException("Damage state byte capacity");
                if(size>PART_BYTES-132)throw new IllegalArgumentException("Oversized damage record");
                if(count>=512 || buffer.size()+size>PART_BYTES-132)finishPart();
                byte[] bytes=new byte[size];encoded.readBytes(bytes);buffer.writeBytes(bytes);count++;
                if(++totalRecords>MAX_RECORDS)throw new IllegalArgumentException("Damage record capacity");
            }finally{encoded.release();}
        }
        private void finishPart(){
            var encoded=new FriendlyByteBuf(Unpooled.buffer());
            try{
                encoded.writeVarInt(count);encoded.writeBytes(buffer.toByteArray());byte[] bytes=new byte[encoded.readableBytes()];encoded.readBytes(bytes);
                totalBytes+=bytes.length+128;
                if(totalBytes>BATCH_BYTES || parts.size()>=MAX_PARTS)throw new IllegalArgumentException("Damage batch capacity");
                parts.add(bytes);buffer.reset();count=0;
            }finally{encoded.release();}
        }
    }
}
