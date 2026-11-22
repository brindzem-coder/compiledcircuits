package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.CreativeHotbar;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record CreativeHotbarC2SPacket(int action,int value,ItemStack stack,long revision) {
    public static final int RESIZE=0, SELECT=1, PUT=2, RESTORE=3, CLEAR=4;
    public static void encode(CreativeHotbarC2SPacket p,FriendlyByteBuf b){b.writeByte(p.action);b.writeInt(p.value);b.writeItem(p.stack);b.writeLong(p.revision);}
    public static CreativeHotbarC2SPacket decode(FriendlyByteBuf b){return new CreativeHotbarC2SPacket(b.readUnsignedByte(),b.readInt(),b.readItem(),b.readLong());}
    public static void handle(CreativeHotbarC2SPacket packet,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->{
            var player=context.getSender();if(player==null||!player.isCreative())return;
            var state=CreativeHotbar.state(player);
            switch(packet.action){
                case RESIZE -> {if(packet.value!=1&&packet.value!=-1)return;state.resize(player,packet.value);}
                case SELECT -> {if(player.containerMenu!=player.inventoryMenu||packet.value<0||packet.value>=state.size())return;state.select(player,packet.value);}
                case PUT -> {
                    if(player.containerMenu!=player.inventoryMenu||packet.value<0||packet.value>=state.size())return;
                    if(!packet.stack.isEmpty()&&(!packet.stack.isItemEnabled(player.level().enabledFeatures())||packet.stack.getCount()>packet.stack.getMaxStackSize()))return;
                    state.restore(player);
                    if(packet.value<9)player.getInventory().setItem(packet.value,packet.stack.copy());
                    else state.put(player,packet.value,packet.stack);
                }
                case RESTORE -> state.restore(player);
                case CLEAR -> state.clearExtra(player);
                default -> {return;}
            }
            state.revision(packet.revision);
            CreativeHotbar.persist(player);player.inventoryMenu.broadcastChanges();CreativeHotbarS2CPacket.send(player);
        });context.setPacketHandled(true);
    }
}
