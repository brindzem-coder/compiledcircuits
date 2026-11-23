package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.networking.*;
import com.example.compiledcircuits.registry.ModItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Local files always belong to this client instance, including on dedicated servers. */
@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, value = Dist.CLIENT)
public final class BlueprintFilesClient {
    private record Pending(UUID token, Connection connection, Path path, long started, BlueprintTransfer transfer) {}
    private static Pending pending;
    private static Path folder() { return Minecraft.getInstance().gameDirectory.toPath().resolve("compiledcircuits/blueprints"); }
    private static Path path(String name) {
        if (!name.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Use 1–64 letters A–Z, digits, underscores or hyphens for the file name");
        return folder().resolve(name + ".nbt");
    }
    private static void message(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal(text));
    }
    @SubscribeEvent public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("circuitfile")
                .executes(c -> { message("/circuitfile add [id], clear, status, save <name>, load <name>, list. Files: " + folder()); return 1; })
                .then(Commands.literal("add").executes(c -> request(BlueprintFileService.ADD, 0))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> request(BlueprintFileService.ADD, IntegerArgumentType.getInteger(c, "id")))))
                .then(Commands.literal("clear").executes(c -> request(BlueprintFileService.CLEAR, 0)))
                .then(Commands.literal("status").executes(c -> request(BlueprintFileService.STATUS, 0)))
                .then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.word()).executes(c -> save(StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("load").then(Commands.argument("name", StringArgumentType.word()).executes(c -> load(StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("list").executes(c -> list())));
    }
    private static int request(int action, int id) {
        ModNetworking.CHANNEL.sendToServer(new BlueprintFileRequestC2SPacket(action, id, UUID.randomUUID())); return 1;
    }
    private static int save(String name) {
        try {
            var target = path(name); Files.createDirectories(folder());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("File already exists; choose another name: " + name);
            var connection = Minecraft.getInstance().getConnection();
            if (connection == null) return 0;
            var token = UUID.randomUUID();
            pending = new Pending(token, connection.getConnection(), target, System.nanoTime(), new BlueprintTransfer());
            ModNetworking.CHANNEL.sendToServer(new BlueprintFileRequestC2SPacket(BlueprintFileService.SAVE, 0, token));
            return 1;
        } catch (IOException | IllegalArgumentException error) { message(error.getMessage()); return 0; }
    }
    public static void accept(BlueprintFilePartS2CPacket part, Connection connection) {
        var request = pending; var live = Minecraft.getInstance().getConnection();
        if (request == null || live == null || live.getConnection() != connection || request.connection() != connection
                || !request.token().equals(part.token())) return;
        try {
            if (System.nanoTime() - request.started() > 30_000_000_000L) throw new IllegalArgumentException("Export timed out; retry /circuitfile save");
            byte[] bytes = request.transfer().accept(part.token(), part.offset(), part.total(), part.bytes());
            if (bytes == null) return;
            pending = null;
            bytes = PortableBlueprint.encode(PortableBlueprint.decode(bytes));
            // Publish only a complete file and never replace an existing export.
            var temporary = Files.createTempFile(folder(), ".export-", ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, request.path());
            } finally { Files.deleteIfExists(temporary); }
            ModNetworking.CHANNEL.sendToServer(new BlueprintFileRequestC2SPacket(BlueprintFileService.SAVED, 0, request.token()));
            message("Saved blueprint: " + request.path());
        } catch (IOException | IllegalArgumentException error) { pending = null; message("Export failed: " + error.getMessage()); }
    }
    private static int load(String name) {
        try {
            var player = Minecraft.getInstance().player;
            if (player == null || !(player.getMainHandItem().is(ModItems.CIRCUIT_COPIER.get()) || player.getOffhandItem().is(ModItems.CIRCUIT_COPIER.get())))
                throw new IllegalArgumentException("Hold a circuit copier to import a file");
            var source = path(name);
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Blueprint file not found: " + name);
            byte[] bytes;
            try (var stream = Files.newInputStream(source)) { bytes = stream.readNBytes(PortableBlueprint.MAX_BYTES + 1); }
            bytes = PortableBlueprint.encode(PortableBlueprint.decode(bytes));
            var token = UUID.randomUUID();
            ModNetworking.CHANNEL.sendToServer(new BlueprintFileRequestC2SPacket(BlueprintFileService.LOAD, 0, token));
            for (int offset = 0; offset < bytes.length; offset += PortableBlueprint.PART_BYTES)
                ModNetworking.CHANNEL.sendToServer(new BlueprintFilePartC2SPacket(token, offset, bytes.length,
                        Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + PortableBlueprint.PART_BYTES))));
            message("Loading blueprint into copier: " + name); return 1;
        } catch (IOException | IllegalArgumentException error) { message("Import failed: " + error.getMessage()); return 0; }
    }
    private static int list() {
        try {
            Files.createDirectories(folder());
            try (var files = Files.list(folder())) {
                var names = files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).map(p -> p.getFileName().toString())
                        .filter(n -> n.matches("[A-Za-z0-9_-]{1,64}\\.nbt")).sorted().limit(100).toList();
                message("Blueprint files: " + (names.isEmpty() ? "none" : String.join(", ", names)) + " | " + folder());
            }
            return 1;
        } catch (IOException error) { message(error.getMessage()); return 0; }
    }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { pending = null; }
}
