package com.example.compiledcircuits.networking;
import com.example.compiledcircuits.network.BlueprintService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record BlueprintRequestC2SPacket(int networkId, boolean append, net.minecraft.core.BlockPos clicked) {
    public BlueprintRequestC2SPacket(int networkId){this(networkId,false,null);}
    public static void encode(BlueprintRequestC2SPacket p,FriendlyByteBuf b){b.writeVarInt(p.networkId);b.writeBoolean(p.append);b.writeBoolean(p.clicked!=null);if(p.clicked!=null)b.writeBlockPos(p.clicked);}
    public static BlueprintRequestC2SPacket decode(FriendlyByteBuf b){int id=b.readVarInt();boolean append=b.readBoolean();return new BlueprintRequestC2SPacket(id,append,b.readBoolean()?b.readBlockPos():null);}
    public static void handle(BlueprintRequestC2SPacket p,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->{var player=context.getSender();if(player!=null){if(p.clicked!=null)BlueprintService.captureAt(player,p.clicked,p.append);else BlueprintService.request(player,p.networkId);}});context.setPacketHandled(true);
    }
}
