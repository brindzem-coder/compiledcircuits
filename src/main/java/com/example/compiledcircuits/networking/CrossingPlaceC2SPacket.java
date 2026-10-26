package com.example.compiledcircuits.networking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record CrossingPlaceC2SPacket(BlockPos pos,InteractionHand hand){
    public static void encode(CrossingPlaceC2SPacket p,FriendlyByteBuf b){b.writeBlockPos(p.pos);b.writeEnum(p.hand);}
    public static CrossingPlaceC2SPacket decode(FriendlyByteBuf b){return new CrossingPlaceC2SPacket(b.readBlockPos(),b.readEnum(InteractionHand.class));}
    public static void handle(CrossingPlaceC2SPacket p,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->{
            var player=context.getSender();if(player==null||!com.example.compiledcircuits.network.NetworkOperations.authorizeWorldInteraction(player))return;
            var stack=player.getItemInHand(p.hand);if(!stack.is(com.example.compiledcircuits.registry.ModItems.BASIC_WIRE.get())&&!stack.is(com.example.compiledcircuits.registry.ModItems.ID_WIRE.get())&&!stack.is(com.example.compiledcircuits.registry.ModItems.BUNDLED_WIRE.get()))return;
            if(p.pos.distSqr(player.blockPosition())>64||!player.serverLevel().hasChunkAt(p.pos))return;
            var hit=player.pick(8,1,false);if(!(hit instanceof net.minecraft.world.phys.BlockHitResult block)||!block.getBlockPos().equals(p.pos))return;
            com.example.compiledcircuits.network.CrossingPlacement.place(new net.minecraft.world.item.context.UseOnContext(player,p.hand,block));
        });context.setPacketHandled(true);
    }
}
