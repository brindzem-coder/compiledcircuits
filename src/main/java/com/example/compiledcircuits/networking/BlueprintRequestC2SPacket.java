package com.example.compiledcircuits.networking;
import com.example.compiledcircuits.network.BlueprintService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record BlueprintRequestC2SPacket(int networkId) {
    public static void encode(BlueprintRequestC2SPacket p,FriendlyByteBuf b){b.writeVarInt(p.networkId);}
    public static BlueprintRequestC2SPacket decode(FriendlyByteBuf b){return new BlueprintRequestC2SPacket(b.readVarInt());}
    public static void handle(BlueprintRequestC2SPacket p,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->{var player=context.getSender();if(player!=null){BlueprintService.request(player,p.networkId);}});context.setPacketHandled(true);
    }
}
