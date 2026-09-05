package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.NetworkCompiler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CompileNamedC2SPacket {

    private final String name;

    public CompileNamedC2SPacket(
            String name
    ) {

        this.name = name;
    }

    public static void encode(
            CompileNamedC2SPacket packet,
            FriendlyByteBuf buf
    ) {

        buf.writeUtf(
                packet.name,
                64
        );
    }

    public static CompileNamedC2SPacket decode(
            FriendlyByteBuf buf
    ) {

        return new CompileNamedC2SPacket(
                buf.readUtf(64)
        );
    }

    public static void handle(
            CompileNamedC2SPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {

        NetworkEvent.Context context =
                contextSupplier.get();

        ServerPlayer player =
                context.getSender();

        if (player == null) {

            context.setPacketHandled(true);

            return;
        }

        context.enqueueWork(
                () ->
                        NetworkCompiler.compileSelected(
                                player,
                                packet.name
                        )
        );

        context.setPacketHandled(true);
    }
}
