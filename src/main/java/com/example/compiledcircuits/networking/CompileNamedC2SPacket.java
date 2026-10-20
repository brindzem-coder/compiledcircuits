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

        if (name == null || name.length() > com.example.compiledcircuits.network.OperationLimits.NAME) throw new IllegalArgumentException("Invalid name length");
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
        if (context.getDirection() != net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER) {
            context.setPacketHandled(true); return;
        }

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
