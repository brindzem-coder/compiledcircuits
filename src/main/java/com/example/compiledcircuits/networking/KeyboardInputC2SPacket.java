package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

public record KeyboardInputC2SPacket(UUID token, int sequence, boolean close, Set<Integer> pressed) {
    public static void encode(KeyboardInputC2SPacket p,FriendlyByteBuf b) {
        b.writeUUID(p.token);b.writeVarInt(p.sequence);b.writeBoolean(p.close);b.writeVarInt(p.pressed.size());p.pressed.forEach(b::writeVarInt);
    }
    public static KeyboardInputC2SPacket decode(FriendlyByteBuf b) {
        var token=b.readUUID();int sequence=b.readVarInt();boolean close=b.readBoolean();int count=b.readVarInt();
        if(count<0||count>32)throw new IllegalArgumentException("Too many keyboard inputs");
        Set<Integer> codes=new HashSet<>();for(int i=0;i<count;i++)codes.add(b.readVarInt());
        return new KeyboardInputC2SPacket(token,sequence,close,Set.copyOf(codes));
    }
    public static void handle(KeyboardInputC2SPacket p,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();context.enqueueWork(()->{if(context.getSender()!=null)com.example.compiledcircuits.network.KeyboardSessions.input(context.getSender(),p);});context.setPacketHandled(true);
    }
}
