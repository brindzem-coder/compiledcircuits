package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.CreativeHotbar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.*;
import java.util.function.Supplier;

public record CreativeHotbarS2CPacket(CompoundTag state,ItemStack ninth,int selected,long revision) {
    public static void send(ServerPlayer player){ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new CreativeHotbarS2CPacket(CreativeHotbar.state(player).save(),player.getInventory().getItem(8).copy(),player.getInventory().selected,CreativeHotbar.state(player).revision()));}
    public static void encode(CreativeHotbarS2CPacket p,FriendlyByteBuf b){b.writeNbt(p.state);b.writeItem(p.ninth);b.writeByte(p.selected);b.writeLong(p.revision);}
    public static CreativeHotbarS2CPacket decode(FriendlyByteBuf b){return new CreativeHotbarS2CPacket(b.readNbt(),b.readItem(),b.readUnsignedByte(),b.readLong());}
    public static void handle(CreativeHotbarS2CPacket p,Supplier<NetworkEvent.Context> supplier){var context=supplier.get();context.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.example.compiledcircuits.client.CreativeHotbarClient.accept(p)));context.setPacketHandled(true);}
}
