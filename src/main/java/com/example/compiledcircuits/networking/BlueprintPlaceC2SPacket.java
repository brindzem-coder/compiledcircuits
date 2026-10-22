package com.example.compiledcircuits.networking;
import com.example.compiledcircuits.network.BlueprintService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record BlueprintPlaceC2SPacket(java.util.UUID blueprint, net.minecraft.core.BlockPos anchor, int turns) {
    public static void encode(BlueprintPlaceC2SPacket p,FriendlyByteBuf b){b.writeUUID(p.blueprint);b.writeBlockPos(p.anchor);b.writeVarInt(p.turns);}
    public static BlueprintPlaceC2SPacket decode(FriendlyByteBuf b){return new BlueprintPlaceC2SPacket(b.readUUID(),b.readBlockPos(),b.readVarInt());}
    public static void handle(BlueprintPlaceC2SPacket p,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->{var player=context.getSender();if(player!=null){BlueprintService.place(player,p.blueprint,p.anchor,p.turns);}});context.setPacketHandled(true);
    }
}
