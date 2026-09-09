package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.CompiledCircuits;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {

    private static final String PROTOCOL_VERSION = "8";

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.ChannelBuilder
                    .named(new ResourceLocation(
                            CompiledCircuits.MOD_ID,
                            "main"
                    ))
                    .networkProtocolVersion(
                            () -> PROTOCOL_VERSION
                    )
                    .clientAcceptedVersions(
                            PROTOCOL_VERSION::equals
                    )
                    .serverAcceptedVersions(
                            PROTOCOL_VERSION::equals
                    )
                    .simpleChannel();

    private static int packetId = 0;

    private ModNetworking() {
    }

    public static void register() {
        CHANNEL.registerMessage(packetId++, BrokenElementListS2CPacket.class,
                BrokenElementListS2CPacket::encode, BrokenElementListS2CPacket::decode,
                BrokenElementListS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                packetId++, BrokenElementsS2CPacket.class,
                BrokenElementsS2CPacket::encode, BrokenElementsS2CPacket::decode,
                BrokenElementsS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));


        CHANNEL.registerMessage(
                packetId++,
                NetworkListS2CPacket.class,
                NetworkListS2CPacket::encode,
                NetworkListS2CPacket::decode,
                NetworkListS2CPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                NetworkActionC2SPacket.class,
                NetworkActionC2SPacket::encode,
                NetworkActionC2SPacket::decode,
                NetworkActionC2SPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                NetworkHighlightS2CPacket.class,
                NetworkHighlightS2CPacket::encode,
                NetworkHighlightS2CPacket::decode,
                NetworkHighlightS2CPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                NetworkBulkActionC2SPacket.class,
                NetworkBulkActionC2SPacket::encode,
                NetworkBulkActionC2SPacket::decode,
                NetworkBulkActionC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                packetId++, OpenCompileNameS2CPacket.class,
                OpenCompileNameS2CPacket::encode, OpenCompileNameS2CPacket::decode,
                OpenCompileNameS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                packetId++, CompileNamedC2SPacket.class,
                CompileNamedC2SPacket::encode, CompileNamedC2SPacket::decode,
                CompileNamedC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                packetId++, OpenNetworkManagerAtS2CPacket.class,
                OpenNetworkManagerAtS2CPacket::encode, OpenNetworkManagerAtS2CPacket::decode,
                OpenNetworkManagerAtS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }
}