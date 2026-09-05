package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.CompiledCircuits;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {

    private static final String PROTOCOL_VERSION = "2";

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
    }
}