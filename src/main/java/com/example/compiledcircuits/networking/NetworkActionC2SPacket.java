package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.CompiledNetwork;
import com.example.compiledcircuits.network.NetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class NetworkActionC2SPacket {

    public enum Action {

        HIGHLIGHT,

        RENAME_NETWORK,
        MOVE_NETWORK,
        DECOMPILE,

        CREATE_FOLDER,
        RENAME_FOLDER,
        DELETE_FOLDER,
        MOVE_FOLDER
    }

    private final Action action;
    private final int networkId;
    private final String value;

    private final int targetId;

    public NetworkActionC2SPacket(
            Action action,
            int networkOrFolderId,
            int targetId,
            String value
    ) {
        this.action = action;
        this.networkId = networkOrFolderId;
        this.targetId = targetId;
        this.value = value;
    }

    public NetworkActionC2SPacket(
            Action action,
            int id,
            String value
    ) {
        this(
                action,
                id,
                0,
                value
        );
    }


    public static void encode(
            NetworkActionC2SPacket packet,
            FriendlyByteBuf buf
    ) {
        buf.writeEnum(packet.action);
        buf.writeInt(packet.networkId);
        buf.writeInt(packet.targetId);
        buf.writeUtf(packet.value);
    }

    public static NetworkActionC2SPacket decode(
            FriendlyByteBuf buf
    ) {
        return new NetworkActionC2SPacket(
                buf.readEnum(Action.class),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(256)
        );
    }

    public static void handle(
            NetworkActionC2SPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context =
                contextSupplier.get();

        context.enqueueWork(() -> {

            ServerPlayer player =
                    context.getSender();

            if (player == null) {
                return;
            }

            NetworkSavedData data =
                    NetworkSavedData.get(
                            player.getServer()
                    );

            CompiledNetwork network =
                    data.getNetwork(
                            packet.networkId
                    );

            if (network == null && (packet.action == Action.HIGHLIGHT
                    || packet.action == Action.RENAME_NETWORK
                    || packet.action == Action.DECOMPILE)) {

                player.sendSystemMessage(
                        Component.literal(
                                "Network #"
                                        + packet.networkId
                                        + " does not exist."
                        )
                );

                NetworkGuiSync.sendList(player);
                return;
            }

            switch (packet.action) {

                case HIGHLIGHT ->
                        highlight(player, network);

                case RENAME_NETWORK ->
                        rename(
                                player,
                                data,
                                network,
                                packet.value
                        );

                case DECOMPILE ->
                        decompile(
                                player,
                                data,
                                network
                        );

                case CREATE_FOLDER -> {

                    int result =
                            data.createFolder(
                                    packet.value,
                                    packet.networkId
                            );

                    if (result < 0) {

                        player.sendSystemMessage(
                                Component.literal(
                                        "Could not create folder."
                                )
                        );
                    }

                    NetworkGuiSync.sendList(player);
                }

                case RENAME_FOLDER -> {

                    boolean success =
                            data.renameFolder(
                                    packet.networkId,
                                    packet.value
                            );

                    if (!success) {

                        player.sendSystemMessage(
                                Component.literal(
                                        "Could not rename folder."
                                )
                        );
                    }

                    NetworkGuiSync.sendList(player);
                }

                case DELETE_FOLDER -> {

                    boolean success =
                            data.deleteFolder(
                                    packet.networkId
                            );

                    if (!success) {

                        player.sendSystemMessage(
                                Component.literal(
                                        "Folder is not empty or cannot be deleted."
                                )
                        );
                    }

                    NetworkGuiSync.sendList(player);
                }

                case MOVE_FOLDER -> {

                    boolean success =
                            data.moveFolder(
                                    packet.networkId,
                                    packet.targetId
                            );

                    if (!success) {

                        player.sendSystemMessage(
                                Component.literal(
                                        "Could not move folder."
                                )
                        );
                    }

                    NetworkGuiSync.sendList(player);
                }

                case MOVE_NETWORK -> {

                    boolean success =
                            data.moveNetwork(
                                    packet.networkId,
                                    packet.targetId
                            );

                    if (!success) {

                        player.sendSystemMessage(
                                Component.literal(
                                        "Could not move network."
                                )
                        );
                    }

                    NetworkGuiSync.sendList(player);
                }
            }
        });

        context.setPacketHandled(true);
    }

    private static void highlight(
            ServerPlayer player,
            CompiledNetwork network
    ) {

        ModNetworking.CHANNEL.send(
                PacketDistributor.PLAYER.with(
                        () -> player
                ),
                new NetworkHighlightS2CPacket(
                        network.getWires(),
                        network.getInputs(),
                        network.getOutputs()
                )
        );
    }

    private static void rename(
            ServerPlayer player,
            NetworkSavedData data,
            CompiledNetwork network,
            String value
    ) {

        String name =
                value.trim();

        if (name.isEmpty()) {

            player.sendSystemMessage(
                    Component.literal(
                            "Network name cannot be empty."
                    )
            );

            return;
        }

        if (name.length() > 64) {

            player.sendSystemMessage(
                    Component.literal(
                            "Network name is too long."
                    )
            );

            return;
        }

        network.setName(name);
        data.setDirty();

        NetworkGuiSync.sendList(player);
    }

    private static void decompile(
            ServerPlayer player,
            NetworkSavedData data,
            CompiledNetwork network
    ) {

        int id =
                network.getId();

        Set<BlockPos> outputs =
                new HashSet<>(
                        network.getOutputs()
                );

        ServerLevel level =
                findNetworkLevel(
                        player,
                        network
                );

        data.removeNetwork(id);

        /*
         * Output після видалення network
         * починає повертати 0.
         */
        if (level != null) {

            for (BlockPos outputPos : outputs) {

                if (!level.hasChunkAt(outputPos)) {
                    continue;
                }

                BlockState state =
                        level.getBlockState(
                                outputPos
                        );

                level.updateNeighborsAt(
                        outputPos,
                        state.getBlock()
                );
            }
        }

        NetworkGuiSync.sendList(player);
    }

    private static ServerLevel findNetworkLevel(
            ServerPlayer player,
            CompiledNetwork network
    ) {

        ResourceLocation location =
                ResourceLocation.tryParse(
                        network.getDimension()
                );

        if (location == null) {
            return null;
        }

        ResourceKey<Level> key =
                ResourceKey.create(
                        Registries.DIMENSION,
                        location
                );

        return player.getServer()
                .getLevel(key);
    }
}