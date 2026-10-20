package com.example.compiledcircuits.networking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;
public record DamageAckC2SPacket(UUID context,long batch,long revision){
    public static void encode(DamageAckC2SPacket p,FriendlyByteBuf b){b.writeUUID(p.context);b.writeLong(p.batch);b.writeLong(p.revision);}
    public static DamageAckC2SPacket decode(FriendlyByteBuf b){if(b.readableBytes()!=32)throw new IllegalArgumentException("Invalid damage acknowledgement");return new DamageAckC2SPacket(b.readUUID(),b.readLong(),b.readLong());}
    public static void handle(DamageAckC2SPacket p,Supplier<NetworkEvent.Context> s){var c=s.get();c.enqueueWork(()->{if(c.getSender()!=null)DamageSync.ack(c.getSender(),p);});c.setPacketHandled(true);}
}
