package com.example.compiledcircuits.networking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;
public record KeyboardCameraS2CPacket(UUID token,boolean open){
    public static void encode(KeyboardCameraS2CPacket p,FriendlyByteBuf b){b.writeUUID(p.token);b.writeBoolean(p.open);}
    public static KeyboardCameraS2CPacket decode(FriendlyByteBuf b){return new KeyboardCameraS2CPacket(b.readUUID(),b.readBoolean());}
    public static void handle(KeyboardCameraS2CPacket p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.example.compiledcircuits.client.KeyboardCameraClient.session(p)));c.setPacketHandled(true);}
}
