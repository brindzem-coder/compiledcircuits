package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record KeyboardSessionS2CPacket(UUID token, boolean open) {
    public static void encode(KeyboardSessionS2CPacket p,FriendlyByteBuf b){b.writeUUID(p.token);b.writeBoolean(p.open);}
    public static KeyboardSessionS2CPacket decode(FriendlyByteBuf b){return new KeyboardSessionS2CPacket(b.readUUID(),b.readBoolean());}
    public static void handle(KeyboardSessionS2CPacket p,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                ()->()->com.example.compiledcircuits.client.KeyboardControlScreen.session(p)));context.setPacketHandled(true);
    }
}
