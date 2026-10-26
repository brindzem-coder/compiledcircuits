package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import com.example.compiledcircuits.CompiledCircuits;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {

    private static final String PROTOCOL_VERSION = "26";

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
        CHANNEL.registerMessage(packetId++, OpenRenameS2CPacket.class, OpenRenameS2CPacket::encode,
                OpenRenameS2CPacket::decode, OpenRenameS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, BlueprintRequestC2SPacket.class, BlueprintRequestC2SPacket::encode, BlueprintRequestC2SPacket::decode, BlueprintRequestC2SPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, BlueprintPlaceC2SPacket.class, BlueprintPlaceC2SPacket::encode, BlueprintPlaceC2SPacket::decode, BlueprintPlaceC2SPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, BlueprintPreviewS2CPacket.class, BlueprintPreviewS2CPacket::encode, BlueprintPreviewS2CPacket::decode, BlueprintPreviewS2CPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, BundleEditorS2CPacket.class, BundleEditorS2CPacket::encode, BundleEditorS2CPacket::decode, BundleEditorS2CPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, BundleSettingsC2SPacket.class, BundleSettingsC2SPacket::encode, BundleSettingsC2SPacket::decode, BundleSettingsC2SPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, WireEditorS2CPacket.class, WireEditorS2CPacket::encode, WireEditorS2CPacket::decode, WireEditorS2CPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, WireCatalogS2CPacket.class, WireCatalogS2CPacket::encode, WireCatalogS2CPacket::decode, WireCatalogS2CPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, WireSettingsC2SPacket.class, WireSettingsC2SPacket::encode, WireSettingsC2SPacket::decode, WireSettingsC2SPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, BenchmarkS2CPacket.class, BenchmarkS2CPacket::encode,
                BenchmarkS2CPacket::decode, BenchmarkS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, BenchmarkRequestC2SPacket.class, BenchmarkRequestC2SPacket::encode,
                BenchmarkRequestC2SPacket::decode, BenchmarkRequestC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++,MembershipRequestC2SPacket.class,
            PerformanceDiagnostics.encoder("MembershipRequestC2SPacket",MembershipRequestC2SPacket::encode),MembershipRequestC2SPacket::decode,MembershipRequestC2SPacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, DamagePartS2CPacket.class,
                PerformanceDiagnostics.encoder("DamagePartS2CPacket",DamagePartS2CPacket::encode),DamagePartS2CPacket::decode,DamagePartS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, DamageResyncC2SPacket.class,
                PerformanceDiagnostics.encoder("DamageResyncC2SPacket",DamageResyncC2SPacket::encode),DamageResyncC2SPacket::decode,DamageResyncC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, DamageAckC2SPacket.class,
                PerformanceDiagnostics.encoder("DamageAckC2SPacket",DamageAckC2SPacket::encode),DamageAckC2SPacket::decode,DamageAckC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(
                packetId++,
                NetworkListS2CPacket.class,
                PerformanceDiagnostics.encoder("NetworkListS2CPacket", NetworkListS2CPacket::encode),
                NetworkListS2CPacket::decode,
                NetworkListS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                NetworkActionC2SPacket.class,
                PerformanceDiagnostics.encoder("NetworkActionC2SPacket", NetworkActionC2SPacket::encode),
                NetworkActionC2SPacket::decode,
                NetworkActionC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                packetId++,
                NetworkHighlightS2CPacket.class,
                PerformanceDiagnostics.encoder("NetworkHighlightS2CPacket", NetworkHighlightS2CPacket::encode),
                NetworkHighlightS2CPacket::decode,
                NetworkHighlightS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                packetId++,
                NetworkBulkActionC2SPacket.class,
                PerformanceDiagnostics.encoder("NetworkBulkActionC2SPacket", NetworkBulkActionC2SPacket::encode),
                NetworkBulkActionC2SPacket::decode,
                NetworkBulkActionC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                packetId++, OpenCompileNameS2CPacket.class,
                PerformanceDiagnostics.encoder("OpenCompileNameS2CPacket", OpenCompileNameS2CPacket::encode), OpenCompileNameS2CPacket::decode,
                OpenCompileNameS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                packetId++, CompileNamedC2SPacket.class,
                PerformanceDiagnostics.encoder("CompileNamedC2SPacket", CompileNamedC2SPacket::encode), CompileNamedC2SPacket::decode,
                CompileNamedC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                packetId++, OpenNetworkManagerAtS2CPacket.class,
                PerformanceDiagnostics.encoder("OpenNetworkManagerAtS2CPacket", OpenNetworkManagerAtS2CPacket::encode), OpenNetworkManagerAtS2CPacket::decode,
                OpenNetworkManagerAtS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, CompiledElementPositionsS2CPacket.class,
                PerformanceDiagnostics.encoder("CompiledElementPositionsS2CPacket", CompiledElementPositionsS2CPacket::encode), CompiledElementPositionsS2CPacket::decode,
                CompiledElementPositionsS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++,CrossingPlaceC2SPacket.class,CrossingPlaceC2SPacket::encode,CrossingPlaceC2SPacket::decode,CrossingPlaceC2SPacket::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
    }
}
