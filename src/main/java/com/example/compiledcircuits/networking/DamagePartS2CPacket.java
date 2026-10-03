package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;
import static com.example.compiledcircuits.networking.DamageProtocol.*;

public record DamagePartS2CPacket(UUID context,UUID epoch,Kind kind,long batch,long base,long revision,
                                  int index,int parts,int records,int bytes,byte[] payload){
    public DamagePartS2CPacket {
        if(context==null||epoch==null||kind==null||batch<=0||revision<0||base< -1||index<0||parts<1||parts>MAX_PARTS||index>=parts
                ||records<0||records>MAX_RECORDS||bytes<0||bytes>BATCH_BYTES||payload==null||payload.length>PART_BYTES-128
                ||kind==Kind.DELTA && (base<0||revision<=base)||kind==Kind.SNAPSHOT&&base!= -1
                ||kind==Kind.OVER_CAPACITY&&(parts!=1||records!=0||payload.length!=0))throw new IllegalArgumentException("Invalid damage frame");
        payload=payload.clone();
    }
    @Override public byte[] payload(){return payload.clone();}
    public static void encode(DamagePartS2CPacket p,FriendlyByteBuf b){
        b.writeUUID(p.context);b.writeUUID(p.epoch);b.writeEnum(p.kind);b.writeLong(p.batch);b.writeLong(p.base);b.writeLong(p.revision);
        b.writeVarInt(p.index);b.writeVarInt(p.parts);b.writeVarInt(p.records);b.writeVarInt(p.bytes);b.writeByteArray(p.payload);
    }
    public static DamagePartS2CPacket decode(FriendlyByteBuf b){
        if(b.readableBytes()>PART_BYTES)throw new IllegalArgumentException("Oversized damage packet");
        var result=new DamagePartS2CPacket(b.readUUID(),b.readUUID(),b.readEnum(Kind.class),b.readLong(),b.readLong(),b.readLong(),
                b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readByteArray(PART_BYTES-128));
        if(b.isReadable())throw new IllegalArgumentException("Trailing damage bytes");return result;
    }
    public static void handle(DamagePartS2CPacket p,Supplier<NetworkEvent.Context> supplier){
        var c=supplier.get();var connection=c.getNetworkManager();
        c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPacketHandlers.handleDamage(p,connection)));c.setPacketHandled(true);
    }
}
