package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;
import static com.example.compiledcircuits.networking.HighlightProtocol.*;

/** Bounded immutable part; no world reads in either codec. */
public record NetworkHighlightS2CPacket(Request request,long batch,long revision,State state,String message,
        int index,int parts,int positions,int bytes,byte[] payload) {
    public NetworkHighlightS2CPacket {
        if(request==null||!request.current()||batch<=0||revision<0||state==null||message==null||message.length()>MESSAGE
                ||parts<1||parts>PARTS||index<0||index>=parts||positions<0||positions>POSITIONS||bytes<0||bytes>BATCH_BYTES
                ||payload==null||payload.length>PART_POSITIONS*9||payload.length%9!=0
                ||state!=State.READY&&(parts!=1||positions!=0||bytes!=0||payload.length!=0))
            throw new IllegalArgumentException("Invalid highlight frame");
        payload=payload.clone();
    }
    @Override public byte[] payload(){return payload.clone();}
    public static void writeRequest(Request r,FriendlyByteBuf b){b.writeUUID(r.context());b.writeLong(r.id());b.writeUtf(r.dimension(),DIMENSION);}
    public static Request readRequest(FriendlyByteBuf b){return new Request(b.readUUID(),b.readLong(),b.readUtf(DIMENSION));}
    public static void encode(NetworkHighlightS2CPacket p,FriendlyByteBuf b){
        writeRequest(p.request,b);b.writeLong(p.batch);b.writeLong(p.revision);b.writeEnum(p.state);b.writeUtf(p.message,MESSAGE);
        b.writeVarInt(p.index);b.writeVarInt(p.parts);b.writeVarInt(p.positions);b.writeVarInt(p.bytes);b.writeByteArray(p.payload);
    }
    public static NetworkHighlightS2CPacket decode(FriendlyByteBuf b){
        if(b.readableBytes()>PART_BYTES)throw new IllegalArgumentException("Oversized highlight part");
        var p=new NetworkHighlightS2CPacket(readRequest(b),b.readLong(),b.readLong(),b.readEnum(State.class),b.readUtf(MESSAGE),
                b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readByteArray(PART_POSITIONS*9));
        if(b.isReadable())throw new IllegalArgumentException("Trailing highlight bytes");return p;
    }
    public static void handle(NetworkHighlightS2CPacket p,Supplier<NetworkEvent.Context> supplier){
        var c=supplier.get();var connection=c.getNetworkManager();
        c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPacketHandlers.handleHighlight(p,connection)));
        c.setPacketHandled(true);
    }
}
