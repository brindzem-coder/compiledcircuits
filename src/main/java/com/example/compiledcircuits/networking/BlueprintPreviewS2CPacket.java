package com.example.compiledcircuits.networking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;
public record BlueprintPreviewS2CPacket(UUID id,String name,int offset,int total,CompoundTag payload){
    public static void encode(BlueprintPreviewS2CPacket p,FriendlyByteBuf b){b.writeUUID(p.id);b.writeUtf(p.name,256);b.writeVarInt(p.offset);b.writeVarInt(p.total);b.writeNbt(p.payload);}
    public static BlueprintPreviewS2CPacket decode(FriendlyByteBuf b){return new BlueprintPreviewS2CPacket(b.readUUID(),b.readUtf(256),b.readVarInt(),b.readVarInt(),b.readNbt());}
    public static void handle(BlueprintPreviewS2CPacket p,Supplier<NetworkEvent.Context> supplier){var context=supplier.get();context.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.example.compiledcircuits.client.BlueprintClient.accept(p)));context.setPacketHandled(true);}
}
