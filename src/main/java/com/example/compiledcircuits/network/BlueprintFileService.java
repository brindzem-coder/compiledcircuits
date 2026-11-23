package com.example.compiledcircuits.network;

import com.example.compiledcircuits.networking.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Per-session export buffers; imports only populate the held copier, never place blocks. */
public final class BlueprintFileService {
    public static final int ADD = 0, CLEAR = 1, STATUS = 2, SAVE = 3, LOAD = 4, SAVED = 5;
    private static final Map<ServerPlayer, CircuitBlueprint> buffers = new WeakHashMap<>();
    private record Export(UUID token, CircuitBlueprint snapshot) {}
    private static final Map<ServerPlayer, Export> exports = new WeakHashMap<>();
    private record Budget(long window, int requests) {}
    private static final Map<ServerPlayer, Budget> budgets = new WeakHashMap<>();
    private record Upload(UUID token, long started, net.minecraft.world.item.ItemStack tool, BlueprintTransfer transfer) {}
    private static final Map<ServerPlayer, Upload> uploads = new WeakHashMap<>();
    private BlueprintFileService() {}
    public static void forget(ServerPlayer player) { buffers.remove(player); uploads.remove(player); exports.remove(player); budgets.remove(player); }
    static CircuitBlueprint buffer(ServerPlayer player) { return buffers.get(player); }
    private static void message(ServerPlayer player, String text) { player.sendSystemMessage(Component.literal(text)); }

    public static void request(ServerPlayer player, BlueprintFileRequestC2SPacket request) {
        if (player != null && request.action() == SAVED) {
            var export = exports.get(player);
            if (export != null && export.token().equals(request.token())) {
                exports.remove(player);
                if (buffers.get(player) == export.snapshot()) {
                    buffers.remove(player);
                    message(player, "File saved; export buffer cleared.");
                } else message(player, "File saved; the buffer changed during export and has been retained.");
            }
            return;
        }
        if (player == null) return;
        if (!NetworkOperations.canModify(player)) { message(player, "Blueprint commands are unavailable: building is not allowed."); return; }
        // Background damage/membership synchronization must not consume the user's file-command quota.
        long now = player.getServer().getTickCount();
        var budget = budgets.get(player);
        if (budget == null || now < budget.window() || now - budget.window() >= OperationLimits.WINDOW_TICKS)
            budget = new Budget(now, 0);
        if (budget.requests() >= OperationLimits.REQUESTS_PER_WINDOW) {
            message(player, "Too many blueprint commands; retry in a second."); return;
        }
        budgets.put(player, new Budget(budget.window(), budget.requests() + 1));
        try {
            switch (request.action()) {
                case ADD -> {
                    CircuitBlueprint next;
                    var source = request.networkId() > 0 ? NetworkSavedData.get(player.getServer()).getNetwork(request.networkId()) : NetworkSelectionData.network(player);
                    if (source != null) {
                        if (!source.getDimension().equals(player.serverLevel().dimension().location().toString()))
                            throw new IllegalArgumentException("Select a network in the current dimension");
                        next = CircuitBlueprint.capture(source, player.serverLevel());
                    } else {
                        if (request.networkId() != 0) throw new IllegalArgumentException("Network does not exist");
                        var pos = NetworkSelectionData.get(player);
                        if (pos == null) throw new IllegalArgumentException("Select a network with the selector first");
                        next = UncompiledBlueprint.capture(player.serverLevel(), pos, NetworkSelectionData.mask(player));
                    }
                    var previous = buffers.get(player);
                    var combined = previous == null ? next : previous.append(next);
                    // Check the same file limits before replacing the previous buffer.
                    PortableBlueprint.encode(combined);
                    buffers.put(player, combined); status(player);
                }
                case CLEAR -> { buffers.remove(player); exports.remove(player); message(player, "Export buffer cleared."); }
                case STATUS -> status(player);
                case SAVE -> {
                    var blueprint = buffers.get(player);
                    if (blueprint == null) throw new IllegalArgumentException("Export buffer is empty; use /circuitfile add");
                    byte[] bytes = PortableBlueprint.encode(blueprint);
                    exports.put(player, new Export(request.token(), blueprint));
                    for (int offset = 0; offset < bytes.length; offset += PortableBlueprint.PART_BYTES)
                        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BlueprintFilePartS2CPacket(request.token(), offset, bytes.length,
                                Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + PortableBlueprint.PART_BYTES))));
                }
                case LOAD -> {
                    var tool = BlueprintService.tool(player);
                    if (tool.isEmpty()) throw new IllegalArgumentException("Hold a circuit copier to import a file");
                    uploads.put(player, new Upload(request.token(), System.nanoTime(), tool, new BlueprintTransfer()));
                }
                default -> throw new IllegalArgumentException("Unknown blueprint file action");
            }
        } catch (IllegalArgumentException error) { message(player, error.getMessage()); }
    }

    private static void status(ServerPlayer player) {
        var blueprint = buffers.get(player);
        message(player, blueprint == null ? "Export buffer is empty." : "Export buffer: " + blueprint.networkCount() + " networks, "
                + blueprint.save().getList("elements", net.minecraft.nbt.Tag.TAG_COMPOUND).size() + " block entries. Use /circuitfile save <name>.");
    }

    public static void accept(ServerPlayer player, BlueprintFilePartC2SPacket part) {
        if (player == null) return;
        var upload = uploads.get(player);
        if (upload == null || !upload.token().equals(part.token())) return;
        try {
            if (!NetworkOperations.canModify(player) || BlueprintService.tool(player) != upload.tool() || upload.tool().isEmpty()
                    || System.nanoTime() - upload.started() > 30_000_000_000L)
                throw new IllegalArgumentException("Import expired or the held copier changed; retry /circuitfile load");
            var bytes = upload.transfer().accept(part.token(), part.offset(), part.total(), part.bytes());
            if (bytes == null) return;
            uploads.remove(player);
            var blueprint = PortableBlueprint.decode(bytes);
            var id = BlueprintStorage.get(player.getServer()).put(blueprint);
            upload.tool().getOrCreateTag().putUUID("Blueprint", id);
            upload.tool().getOrCreateTag().putString("BlueprintName", blueprint.name());
            player.inventoryMenu.broadcastChanges();
            BlueprintService.request(player, 0);
            message(player, "Imported into copier as uncompiled blocks: " + blueprint.name());
        } catch (IllegalArgumentException error) { uploads.remove(player); message(player, error.getMessage()); }
    }
}
