package com.example.compiledcircuits.network;

import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkCompiler {

    private NetworkCompiler() {
    }

    public static CompiledNetwork findSelectedConflict(ServerPlayer player) {
        BlockPos selectedPos = NetworkSelectionData.get(player);
        if (selectedPos == null) return null;
        ServerLevel level = player.serverLevel();
        NetworkScanner.ScanResult scan = NetworkScanner.scan(level, selectedPos);
        if (!scan.success() || scan.totalSize() <= 0) return null;
        return NetworkSavedData.get(player.getServer()).findConflict(level, scan);
    }

    public static boolean compileSelected(
            ServerPlayer player,
            String requestedName
    ) {
        long diagnosticStart = PerformanceDiagnostics.begin();
        PerformanceDiagnostics.add("compile.calls", 1);
        try {
        if (NetworkSavedData.get(player.getServer()).hasUnknownMembershipReservations()) {
            player.sendSystemMessage(Component.literal("Compilation blocked: unresolved saved membership records. See server log."));
            return false;
        }

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

        final int networkId;
        final CompiledNetwork network;
        try {
            networkId = savedData.getNextNetworkId();
            var elements = CompiledElementFactory.create(level, result.wires(), result.inputs(), result.outputs());
            network = new CompiledNetwork(networkId, name, 0,
                    level.dimension().location().toString(), elements);
            savedData.addNetwork(network);
        } catch (IllegalArgumentException rejected) {
            player.sendSystemMessage(Component.literal("Cannot compile: " + rejected.getMessage()));
            return false;
        }

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
        } finally { PerformanceDiagnostics.elapsed("compile", diagnosticStart); }
    }
}
