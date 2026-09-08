package com.example.compiledcircuits.command;

import com.example.compiledcircuits.network.CompiledNetwork;
import com.example.compiledcircuits.network.CompiledCircuitElement;
import com.example.compiledcircuits.network.CompiledElementFactory;
import com.example.compiledcircuits.network.NetworkCompiler;
import com.example.compiledcircuits.network.NetworkSavedData;
import com.example.compiledcircuits.network.NetworkScanner;
import com.example.compiledcircuits.network.NetworkSelectionData;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.example.compiledcircuits.networking.ModNetworking;
import com.example.compiledcircuits.networking.OpenCompileNameS2CPacket;
import com.example.compiledcircuits.networking.NetworkListS2CPacket;
import net.minecraftforge.network.PacketDistributor;

import com.example.compiledcircuits.networking.NetworkGuiSync;

public final class CircuitCommands {

    private CircuitCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {

        dispatcher.register(
                Commands.literal("circuit")

                        .then(
                                Commands.literal("compile")
                                        .executes(context ->
                                                compile(
                                                        context.getSource()
                                                )
                                        )
                        )

                        .then(Commands.literal("debug_elements")
                                .executes(context -> debugElements(context.getSource())))

                        .then(Commands.literal("open_selected")
                                .executes(context -> openSelectedNetwork(context.getSource())))

                        .then(Commands.literal("compile_named")
                                .executes(context -> openCompileName(context.getSource())))

                        .then(
                                Commands.literal("decompile")
                                        .executes(context ->
                                                decompile(
                                                        context.getSource()
                                                )
                                        )
                        )

                        .then(
                                Commands.literal("list")
                                        .executes(context ->
                                                listNetworks(
                                                        context.getSource()
                                                )
                                        )
                        )
                        .then(
                                Commands.literal("rename")
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                IntegerArgumentType.integer(1)
                                                        )
                                                        .then(
                                                                Commands.argument(
                                                                                "name",
                                                                                StringArgumentType.greedyString()
                                                                        )
                                                                        .executes(context ->
                                                                                renameNetwork(
                                                                                        context.getSource(),
                                                                                        IntegerArgumentType.getInteger(
                                                                                                context,
                                                                                                "id"
                                                                                        ),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "name"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )

                        .then(
                                Commands.literal("folder")
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                IntegerArgumentType.integer(1)
                                                        )
                                                        .then(
                                                                Commands.argument(
                                                                                "path",
                                                                                StringArgumentType.greedyString()
                                                                        )
                                                                        .executes(context ->
                                                                                moveNetworkToFolder(
                                                                                        context.getSource(),
                                                                                        IntegerArgumentType.getInteger(
                                                                                                context,
                                                                                                "id"
                                                                                        ),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "path"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )
                        .then(
                                Commands.literal("gui")
                                        .executes(context ->
                                                openGui(context.getSource())
                                        )
                        )
        );
    }

    private static int openCompileName(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command must be used by a player."));
            return 0;
        }
        if (NetworkSelectionData.get(player) == null) {
            source.sendFailure(Component.literal("No circuit selected."));
            return 0;
        }
        CompiledNetwork conflict = NetworkCompiler.findSelectedConflict(player);
        if (conflict != null) {
            source.sendFailure(Component.literal("Cannot compile: part of this circuit already belongs to "
                    + conflict.getName() + " (#" + conflict.getId() + ")."));
            return 0;
        }
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenCompileNameS2CPacket());
        return 1;
    }

    private static int openSelectedNetwork(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command must be used by a player."));
            return 0;
        }
        BlockPos selectedPos = NetworkSelectionData.get(player);
        if (selectedPos == null) {
            source.sendFailure(Component.literal("No circuit selected."));
            return 0;
        }
        CompiledNetwork network = NetworkSavedData.get(player.getServer())
                .findNetworkContaining(player.serverLevel(), selectedPos);
        if (network == null) {
            source.sendFailure(Component.literal("Selected blocks do not belong to a compiled network."));
            return 0;
        }
        NetworkGuiSync.sendListAndNavigate(player, network.getId());
        return 1;
    }

    private static int debugElements(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command must be used by a player."));
            return 0;
        }
        BlockPos selectedPos = NetworkSelectionData.get(player);
        if (selectedPos == null) {
            source.sendFailure(Component.literal("No circuit selected."));
            return 0;
        }
        CompiledNetwork network = NetworkSavedData.get(player.getServer())
                .findNetworkContaining(player.serverLevel(), selectedPos);
        if (network == null) {
            source.sendFailure(Component.literal("Selected blocks do not belong to a compiled network."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Network #" + network.getId() + " \""
                + network.getName() + "\" — Elements: " + network.getElements().size()), false);
        int count = 0;
        for (CompiledCircuitElement element : network.getElements()) {
            if (count++ >= 20) break;
            BlockPos pos = element.getPos();
            source.sendSuccess(() -> Component.literal("#" + element.getId() + " " + element.getType()
                    + " " + element.getBlockId() + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
        }
        return 1;
    }

    private static int compile(
            CommandSourceStack source
    ) {

        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {

            source.sendFailure(
                    Component.literal(
                            "This command must be used by a player."
                    )
            );

            return 0;
        }

        BlockPos startPos =
                NetworkSelectionData.get(player);

        if (startPos == null) {

            source.sendFailure(
                    Component.literal(
                            "No network selected. Use the Network Selector first."
                    )
            );

            return 0;
        }

        ServerLevel level =
                player.serverLevel();

        NetworkScanner.ScanResult result =
                NetworkScanner.scan(
                        level,
                        startPos
                );

        if (!result.success()) {

            source.sendFailure(
                    Component.literal(
                            "Network scan failed."
                    )
            );

            return 0;
        }

        if (result.totalSize() == 0) {

            source.sendFailure(
                    Component.literal(
                            "Selected network is empty."
                    )
            );

            return 0;
        }

        if (result.inputs().isEmpty()) {

            source.sendFailure(
                    Component.literal(
                            "Network has no Input Endpoint."
                    )
            );

            return 0;
        }

        if (result.outputs().isEmpty()) {

            source.sendFailure(
                    Component.literal(
                            "Network has no Output Endpoint."
                    )
            );

            return 0;
        }

        NetworkSavedData savedData =
                NetworkSavedData.get(
                        source.getServer()
                );

        CompiledNetwork conflict =
                savedData.findConflict(
                        level,
                        result
                );

        if (conflict != null) {

            source.sendFailure(
                    Component.literal(
                            "Cannot compile: part of this circuit already belongs to "
                                    + conflict.getName()
                                    + " (#"
                                    + conflict.getId()
                                    + ")."
                    )
            );

            return 0;
        }

        int networkId =
                savedData.getNextNetworkId();

        String defaultName =
                "Network " + networkId;

        String dimension =
                level.dimension()
                        .location()
                        .toString();

        java.util.List<CompiledCircuitElement> elements = CompiledElementFactory.create(
                level, result.wires(), result.inputs(), result.outputs());

        CompiledNetwork network =
                new CompiledNetwork(
                        networkId,
                        defaultName,
                        0,
                        dimension,
                        result.wires(),
                        result.inputs(),
                        result.outputs(),
                        elements
                );

        savedData.addNetwork(network);

        source.sendSuccess(
                () -> Component.literal(
                        "Compiled "
                                + defaultName
                                + ": "
                                + result.wires().size()
                                + " wires, "
                                + result.inputs().size()
                                + " inputs, "
                                + result.outputs().size()
                                + " outputs."
                ),
                false
        );

        return 1;
    }

    private static int decompile(
            CommandSourceStack source
    ) {

        ServerPlayer player;

        try {

            player =
                    source.getPlayerOrException();

        } catch (Exception e) {

            source.sendFailure(
                    Component.literal(
                            "This command must be used by a player."
                    )
            );

            return 0;
        }

        BlockPos selectedPos =
                NetworkSelectionData.get(player);

        if (selectedPos == null) {

            source.sendFailure(
                    Component.literal(
                            "No network selected. Use the Network Selector first."
                    )
            );

            return 0;
        }

        ServerLevel level =
                player.serverLevel();

        NetworkSavedData savedData =
                NetworkSavedData.get(
                        source.getServer()
                );

        CompiledNetwork network =
                savedData.findNetworkContaining(
                        level,
                        selectedPos
                );

        if (network == null) {

            source.sendFailure(
                    Component.literal(
                            "The selected element does not belong to a compiled network."
                    )
            );

            return 0;
        }

        int id = network.getId();
        String name = network.getName();

        /*
         * Перед видаленням мережі запам'ятовуємо outputs,
         * бо після removeNetwork() вони вже не матимуть
         * network signal.
         */
        java.util.Set<BlockPos> outputs =
                new java.util.HashSet<>(
                        network.getOutputs()
                );

        boolean removed =
                savedData.removeNetwork(id);

        if (!removed) {

            source.sendFailure(
                    Component.literal(
                            "Failed to decompile network."
                    )
            );

            return 0;
        }

        /*
         * Output після decompile тепер повертає 0.
         * Треба повідомити vanilla blocks навколо нього,
         * щоб лампи/redstone тощо оновились.
         */
        com.example.compiledcircuits.networking.BrokenElementSync.broadcastDimension(level);
        com.example.compiledcircuits.networking.NetworkGuiSync.broadcastBrokenList(level.getServer());

        for (BlockPos outputPos : outputs) {

            if (!level.hasChunkAt(outputPos)) {
                continue;
            }

            BlockState outputState =
                    level.getBlockState(outputPos);

            level.updateNeighborsAt(
                    outputPos,
                    outputState.getBlock()
            );
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Decompiled "
                                + name
                                + " (#"
                                + id
                                + ")."
                ),
                false
        );

        return 1;
    }

    private static int listNetworks(
            CommandSourceStack source
    ) {

        NetworkSavedData savedData =
                NetworkSavedData.get(
                        source.getServer()
                );

        java.util.List<CompiledNetwork> networks =
                new java.util.ArrayList<>(
                        savedData.getNetworks()
                );

        if (networks.isEmpty()) {

            source.sendSuccess(
                    () -> Component.literal(
                            "No compiled networks."
                    ),
                    false
            );

            return 1;
        }

        /*
         * Поки сортуємо по ID.
         */
        networks.sort(
                java.util.Comparator.comparingInt(
                        CompiledNetwork::getId
                )
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Compiled networks: "
                                + networks.size()
                ),
                false
        );

        for (CompiledNetwork network : networks) {

            String folder =
                    network.getFolderId() == 0
                            ? "/"
                            : savedData.getFolderPath(network.getFolderId());

            String state =
                    network.isPowered()
                            ? "ON"
                            : "OFF";

            source.sendSuccess(
                    () -> Component.literal(
                            "#"
                                    + network.getId()
                                    + "  "
                                    + network.getName()
                                    + "  [" + state + "]"
                                    + "  folder=" + folder
                                    + "  wires="
                                    + network.getWires().size()
                                    + "  inputs="
                                    + network.getInputs().size()
                                    + "  outputs="
                                    + network.getOutputs().size()
                    ),
                    false
            );
        }

        return 1;
    }

    private static int renameNetwork(
            CommandSourceStack source,
            int id,
            String newName
    ) {

        newName = newName.trim();

        if (newName.isEmpty()) {

            source.sendFailure(
                    Component.literal(
                            "Network name cannot be empty."
                    )
            );

            return 0;
        }

        if (newName.length() > 64) {

            source.sendFailure(
                    Component.literal(
                            "Network name is too long. Maximum length is 64 characters."
                    )
            );

            return 0;
        }

        NetworkSavedData savedData =
                NetworkSavedData.get(
                        source.getServer()
                );

        CompiledNetwork network =
                savedData.getNetwork(id);

        if (network == null) {

            source.sendFailure(
                    Component.literal(
                            "Network #"
                                    + id
                                    + " does not exist."
                    )
            );

            return 0;
        }

        String oldName =
                network.getName();

        network.setName(newName);

        savedData.setDirty();

        String finalNewName = newName;

        source.sendSuccess(
                () -> Component.literal(
                        "Renamed network #"
                                + id
                                + " from \""
                                + oldName
                                + "\" to \""
                                + finalNewName
                                + "\"."
                ),
                false
        );

        return 1;
    }

    private static int moveNetworkToFolder(
            CommandSourceStack source,
            int id,
            String path
    ) {

        path = normalizeFolderPath(path);

        if (path.equalsIgnoreCase("root")) {
            path = "";
        }

        if (path.length() > 256) {

            source.sendFailure(
                    Component.literal(
                            "Folder path is too long. Maximum length is 256 characters."
                    )
            );

            return 0;
        }

        NetworkSavedData savedData =
                NetworkSavedData.get(
                        source.getServer()
                );

        CompiledNetwork network =
                savedData.getNetwork(id);

        if (network == null) {

            source.sendFailure(
                    Component.literal(
                            "Network #"
                                    + id
                                    + " does not exist."
                    )
            );

            return 0;
        }

        int folderId = savedData.findFolderByPath(path);
        if (!savedData.moveNetwork(id, folderId)) {
            source.sendFailure(Component.literal("Folder does not exist. Create it in the Network Manager first."));
            return 0;
        }

        savedData.setDirty();

        String displayedPath =
                path.isEmpty()
                        ? "/"
                        : path;

        source.sendSuccess(
                () -> Component.literal(
                        "Moved network #"
                                + id
                                + " ("
                                + network.getName()
                                + ") to folder "
                                + displayedPath
                                + "."
                ),
                false
        );

        return 1;
    }

    private static String normalizeFolderPath(
            String path
    ) {

        path = path.trim();

        /*
         * Дозволяємо користувачу писати:
         *
         * CPU\ALU
         *
         * або:
         *
         * CPU/ALU
         */

        path = path.replace('\\', '/');

        /*
         * Прибираємо повторні /
         *
         * CPU///ALU
         *
         * →
         *
         * CPU/ALU
         */
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }

        /*
         * Прибираємо / на початку.
         */
        while (path.startsWith("/")) {
            path = path.substring(1);
        }

        /*
         * Прибираємо / в кінці.
         */
        while (path.endsWith("/")) {
            path = path.substring(
                    0,
                    path.length() - 1
            );
        }

        return path;
    }

    private static int openGui(CommandSourceStack source) {

        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(
                    Component.literal("This command must be used by a player.")
            );
            return 0;
        }

        NetworkGuiSync.sendList(player);

        return 1;
    }
}