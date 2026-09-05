package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkCompiler {

    private NetworkCompiler() {
    }

    public static boolean compileSelected(
            ServerPlayer player,
            String requestedName
    ) {

        ServerLevel level =
                player.serverLevel();

        /*
         * Беремо server-side selection,
         * яку вже зберігає Network Selector.
         */
        BlockPos selectedPos =
                NetworkSelectionData.get(
                        player
                );

        if (selectedPos == null) {

            player.sendSystemMessage(
                    Component.literal(
                            "No circuit selected."
                    )
            );

            return false;
        }

        String name =
                requestedName == null
                        ? ""
                        : requestedName.trim();

        if (name.isEmpty()) {

            player.sendSystemMessage(
                    Component.literal(
                            "Network name cannot be empty."
                    )
            );

            return false;
        }

        if (name.length() > 64) {

            player.sendSystemMessage(
                    Component.literal(
                            "Network name is too long. Maximum: 64 characters."
                    )
            );

            return false;
        }

        /*
         * Авторитетний server scan.
         */
        NetworkScanner.ScanResult result =
                NetworkScanner.scan(
                        level,
                        selectedPos
                );

        if (!result.success()) {

            player.sendSystemMessage(
                    Component.literal(
                            "Could not scan the selected circuit."
                    )
            );

            return false;
        }

        if (result.totalSize() <= 0) {

            player.sendSystemMessage(
                    Component.literal(
                            "Selected circuit is empty."
                    )
            );

            return false;
        }

        if (result.inputs().isEmpty()) {

            player.sendSystemMessage(
                    Component.literal(
                            "Cannot compile: circuit has no Input Endpoint."
                    )
            );

            return false;
        }

        if (result.outputs().isEmpty()) {

            player.sendSystemMessage(
                    Component.literal(
                            "Cannot compile: circuit has no Output Endpoint."
                    )
            );

            return false;
        }

        NetworkSavedData savedData =
                NetworkSavedData.get(
                        player.getServer()
                );

        CompiledNetwork conflict =
                savedData.findConflict(
                        level,
                        result
                );

        if (conflict != null) {

            player.sendSystemMessage(
                    Component.literal(
                            "Cannot compile: part of this circuit already belongs to "
                                    + conflict.getName()
                                    + " (#"
                                    + conflict.getId()
                                    + ")."
                    )
            );

            return false;
        }

        int networkId =
                savedData.getNextNetworkId();

        String dimension =
                level.dimension()
                        .location()
                        .toString();

        /*
         * Нова network створюється у root folder.
         *
         * folderId = 0
         */
        CompiledNetwork network =
                new CompiledNetwork(
                        networkId,
                        name,
                        0,
                        dimension,
                        result.wires(),
                        result.inputs(),
                        result.outputs()
                );

        savedData.addNetwork(
                network
        );

        NetworkRuntime.inputChanged(level, network.getInputs().iterator().next());

        player.sendSystemMessage(
                Component.literal(
                        "Compiled network #"
                                + networkId
                                + ": "
                                + name
                )
        );

        return true;
    }
}
