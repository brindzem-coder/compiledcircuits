package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Scope is always GLOBAL; the dimension is only a server-validated context witness. */
public record DamageResyncC2SPacket(UUID context,String dimension){
    public static void encode(DamageResyncC2SPacket p,FriendlyByteBuf b){b.writeUUID(p.context);b.writeUtf(p.dimension,DamageProtocol.IDENTIFIER);}
    public static DamageResyncC2SPacket decode(FriendlyByteBuf b){
        if(b.readableBytes()>1044)throw new IllegalArgumentException("Oversized resync");
        var p=new DamageResyncC2SPacket(b.readUUID(),b.readUtf(DamageProtocol.IDENTIFIER));
        if(b.isReadable())throw new IllegalArgumentException("Trailing resync bytes");return p;
    }
    public static void handle(DamageResyncC2SPacket p,Supplier<NetworkEvent.Context> supplier){
        var c=supplier.get();c.enqueueWork(()->{var player=c.getSender();if(player!=null)DamageSync.request(player,p.context,p.dimension);});c.setPacketHandled(true);
    }
}
