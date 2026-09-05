package com.example.compiledcircuits.networking;

import net.minecraft.network.FriendlyByteBuf;
import com.example.compiledcircuits.client.ClientPacketHandlers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenCompileNameS2CPacket {

    public OpenCompileNameS2CPacket() {
    }

    public static void encode(
            OpenCompileNameS2CPacket packet,
            FriendlyByteBuf buf
    ) {
        /*
         * Packet поки не має data.
         */
    }

    public static OpenCompileNameS2CPacket decode(
            FriendlyByteBuf buf
    ) {

        return new OpenCompileNameS2CPacket();
    }

    public static void handle(
            OpenCompileNameS2CPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {

        NetworkEvent.Context context =
                contextSupplier.get();

        context.enqueueWork(
                () ->
                        DistExecutor.unsafeRunWhenOn(
                                Dist.CLIENT,
                                () ->
                                        () ->
                                                ClientPacketHandlers.openCompileNameScreen()
                        )
        );

        context.setPacketHandled(true);
    }
}
