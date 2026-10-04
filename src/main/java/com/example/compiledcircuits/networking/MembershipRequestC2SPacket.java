package com.example.compiledcircuits.networking;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
/** A client world nonce is echoed on every reply; ack zero requests a full snapshot. */
public record MembershipRequestC2SPacket(UUID context,String dimension,long ack){
    public static void encode(MembershipRequestC2SPacket p,FriendlyByteBuf b){b.writeUUID(p.context);b.writeUtf(p.dimension,256);b.writeLong(p.ack);}
    public static MembershipRequestC2SPacket decode(FriendlyByteBuf b){
        if(b.readableBytes()>1050)throw new IllegalArgumentException("Oversized membership request");
        var p=new MembershipRequestC2SPacket(b.readUUID(),b.readUtf(256),b.readLong());
        if(p.ack<0||b.isReadable())throw new IllegalArgumentException("Invalid membership request");return p;
    }
    public static void handle(MembershipRequestC2SPacket p,Supplier<NetworkEvent.Context> supplier){
        var c=supplier.get();c.enqueueWork(()->{if(c.getSender()!=null)CompiledElementSync.request(c.getSender(),p);});c.setPacketHandled(true);
    }
}
