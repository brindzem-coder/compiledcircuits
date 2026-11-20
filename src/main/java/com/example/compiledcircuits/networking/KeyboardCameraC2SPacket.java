package com.example.compiledcircuits.networking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;
public record KeyboardCameraC2SPacket(UUID token,int action){
    public static void encode(KeyboardCameraC2SPacket p,FriendlyByteBuf b){b.writeUUID(p.token);b.writeByte(p.action);}
    public static KeyboardCameraC2SPacket decode(FriendlyByteBuf b){return new KeyboardCameraC2SPacket(b.readUUID(),b.readUnsignedByte());}
    public static void handle(KeyboardCameraC2SPacket p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{if(c.getSender()!=null)com.example.compiledcircuits.network.KeyboardCameraSessions.finish(c.getSender(),p.token,p.action);});c.setPacketHandled(true);}
}
