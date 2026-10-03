package com.example.compiledcircuits.network;

import com.example.compiledcircuits.networking.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import java.util.*;

/** Server-authoritative user operations. Internal load/audit/runtime keep their own APIs. */
public final class NetworkOperations {
    public enum Action { COMPILE, OPEN_COMPILE, DECOMPILE, RENAME_NETWORK, MOVE_NETWORKS, MOVE_PATH,
        CREATE_FOLDER, RENAME_FOLDER, MOVE_FOLDERS, DELETE_FOLDERS, REPAIR, HIGHLIGHT,
        LIST, GUI, SELECTED, DEBUG, LIST_CONFLICTS, REMOVE_CONFLICT }
    public enum Code { OK, QUEUED, FORBIDDEN, NOT_FOUND, INVALID_ARGUMENT, CONFLICT, RATE_LIMITED,
        INVALID_START, INCOMPLETE_UNLOADED, TOO_LARGE, CHANGED_DURING_SCAN, BUSY, TIMEOUT, CANCELLED }
    public record Result(Code code, String message, List<CompiledNetwork> networks) {
        public boolean success() { return code == Code.OK || code == Code.QUEUED; }
    }
    private static Result result(Code code, String message) { return new Result(code, message, List.of()); }
    private static Result ok(String message) { return result(Code.OK, message); }
    private static final Map<Object, Budget> budgets = new IdentityHashMap<>();
    private static final class Budget { long window, lastNotice = Long.MIN_VALUE; int requests, work; }
    private NetworkOperations() {}
    public static boolean canModify(ServerPlayer player) {
        return player != null && !player.isRemoved() && !player.isSpectator() && player.mayBuild();
    }
    public static void forget(Object actor) {
        if (actor instanceof ServerPlayer player) { CompilationJobs.cancel(player); RepairJobs.cancel(player); }
        budgets.remove(actor);
    }
    public static void clear() { budgets.clear(); }
    private static Object actor(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer p ? p : source.getServer();
    }
    private static Result charge(CommandSourceStack source, int work, boolean request) {
        Object key = actor(source); long now = source.getServer().getTickCount();
        Budget b = budgets.get(key);
        if (b == null) {
            if (budgets.size() >= OperationLimits.MAX_ACTORS) return result(Code.RATE_LIMITED, "Too many active request sessions.");
            b = new Budget(); b.window = now; budgets.put(key, b);
        }
        if (now - b.window >= OperationLimits.WINDOW_TICKS || now < b.window) {
            b.window = now; b.requests = 0; b.work = 0;
        }
        if ((request && b.requests >= OperationLimits.REQUESTS_PER_WINDOW) || work > OperationLimits.WORK_PER_WINDOW - b.work)
            return result(Code.RATE_LIMITED, "Request limit reached; retry shortly.");
        if (request) b.requests++;
        b.work += work;
        return null;
    }
    public static int reply(CommandSourceStack source, Result result) {
        if (result.success()) {
            if (!result.message().isEmpty()) source.sendSuccess(() -> Component.literal(result.message()), false);
            return 1;
        }
        Budget b = budgets.get(actor(source)); long now = source.getServer().getTickCount();
        if (b == null && result.code() == Code.RATE_LIMITED) return 0;
        if (b != null) {
            if (b.lastNotice != Long.MIN_VALUE && now - b.lastNotice < OperationLimits.WINDOW_TICKS) return 0;
            b.lastNotice = now;
        }
        source.sendFailure(Component.literal(result.code() + ": " + result.message()));
        return 0;
    }
    private static boolean readOnly(Action action) {
        return switch (action) { case LIST, GUI, SELECTED, DEBUG, HIGHLIGHT, LIST_CONFLICTS -> true; default -> false; };
    }
    private static boolean validName(String name, boolean folder) {
        return name != null && name.length() <= OperationLimits.NAME && !name.trim().isEmpty()
                && (!folder || (!name.contains("/") && !name.contains("\\")));
    }
    public static Result execute(CommandSourceStack source, Action action, Collection<Integer> rawIds, int target, String value) {
        if (source == null || source.getServer() == null || !source.getServer().isSameThread())
            return result(Code.FORBIDDEN, "A server-thread actor is required.");
        if (action == null) return result(Code.INVALID_ARGUMENT, "Missing action.");
        Result rate = charge(source, 0, true);
        if (rate != null) return rate;
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
        boolean admin = action == Action.REMOVE_CONFLICT || action == Action.LIST_CONFLICTS;
        if (admin && !source.hasPermission(2)) return result(Code.FORBIDDEN, "Administrator permission level 2 is required.");
        if (!readOnly(action)) {
            if (player != null) {
                if (player.isRemoved() || player.isSpectator() || (!admin && !player.mayBuild()))
                    return result(Code.FORBIDDEN, "Building is not allowed for this player.");
            } else if (source.getEntity() != null || !source.hasPermission(2)
                    || !(action == Action.RENAME_NETWORK || action == Action.MOVE_PATH || action == Action.REMOVE_CONFLICT)) {
                return result(Code.FORBIDDEN, "This operation requires a player.");
            }
        }
        if (action == null || rawIds == null || rawIds.size() > OperationLimits.IDS || target < 0
                || (value != null && value.length() > OperationLimits.PATH)) return result(Code.INVALID_ARGUMENT, "Invalid request size or fields.");
        if ((action == Action.GUI || action == Action.SELECTED || action == Action.DEBUG || action == Action.HIGHLIGHT) && player == null)
            return result(Code.FORBIDDEN, "This operation requires a player.");
        if ((action == Action.RENAME_NETWORK || action == Action.RENAME_FOLDER || action == Action.CREATE_FOLDER)
                && !validName(value, action != Action.RENAME_NETWORK)) return result(Code.INVALID_ARGUMENT, "Name must contain 1–64 characters; folder names cannot contain slashes.");
        if (action == Action.COMPILE && value != null && !validName(value, false)) return result(Code.INVALID_ARGUMENT, "Network name must contain 1–64 characters.");
        Set<Integer> ids = new LinkedHashSet<>();
        for (Integer id : rawIds) {
            if (id == null || id < 0 || (id == 0 && action != Action.CREATE_FOLDER)) return result(Code.INVALID_ARGUMENT, "Invalid target ID.");
            ids.add(id);
        }
        var data = NetworkSavedData.get(source.getServer());
        boolean folderTargets = action == Action.CREATE_FOLDER || action == Action.RENAME_FOLDER || action == Action.MOVE_FOLDERS || action == Action.DELETE_FOLDERS;
        boolean networkTargets = action == Action.RENAME_NETWORK || action == Action.MOVE_NETWORKS || action == Action.MOVE_PATH
                || action == Action.DECOMPILE || action == Action.REPAIR || action == Action.HIGHLIGHT;
        if ((folderTargets || networkTargets) && ids.isEmpty()) return result(Code.INVALID_ARGUMENT, "No targets selected.");
        boolean single = action == Action.CREATE_FOLDER || action == Action.RENAME_FOLDER || action == Action.RENAME_NETWORK || action == Action.MOVE_PATH;
        if (single && ids.size() != 1) return result(Code.INVALID_ARGUMENT, "Exactly one target is required.");
        for (int id : ids) {
            if (folderTargets && !data.folderExists(id)) return result(Code.NOT_FOUND, "Folder does not exist.");
            if (networkTargets && data.getNetwork(id) == null) return result(Code.NOT_FOUND, "Network does not exist or is isolated.");
        }
        if ((action == Action.MOVE_NETWORKS || action == Action.MOVE_FOLDERS) && !data.folderExists(target))
            return result(Code.NOT_FOUND, "Destination folder does not exist.");
        // Bound full-list/metadata work before traversing any collection or building a response.
        long total = (long)data.getNetworks().size() + data.getFolders().size();
        if (total > OperationLimits.ELEMENTS) return result(Code.INVALID_ARGUMENT, "Too many metadata entries for this synchronous request.");
        rate = charge(source, (int)total, false); if (rate != null) return rate;
        long responseWork = 0;
        for (var n : data.getNetworks()) {
            responseWork += n.getBrokenElements().size();
            if (responseWork > OperationLimits.ELEMENTS) return result(Code.INVALID_ARGUMENT, "Too many broken entries for this synchronous request.");
        }
        rate = charge(source, (int)responseWork, false); if (rate != null) return rate;
        if (action == Action.COMPILE) return compile(source, player, data, value);
        if (action == Action.OPEN_COMPILE) return NetworkSelectionData.get(player) == null
                ? result(Code.NOT_FOUND, "No circuit selected.") : ok("");
        if (action == Action.REMOVE_CONFLICT) {
            long isolationWork = data.getIsolationWorkSize();
            if (isolationWork > OperationLimits.ELEMENTS) return result(Code.INVALID_ARGUMENT, "Isolation data exceeds synchronous administration limit.");
            rate = charge(source, (int)isolationWork, false); if (rate != null) return rate;
            try { if (!UUID.fromString(value).toString().equals(value)) return result(Code.INVALID_ARGUMENT, "Invalid record UUID."); }
            catch (RuntimeException invalid) { return result(Code.INVALID_ARGUMENT, "Invalid record UUID."); }
            return data.removeInvalidMembershipRecord(value) ? ok("Removed isolated record " + value + ". Other records remain blocked.")
                    : result(Code.NOT_FOUND, "Isolated record does not exist.");
        }
        if (action == Action.LIST_CONFLICTS) {
            if (data.getInvalidMembershipRecordCount() > OperationLimits.IDS) return result(Code.INVALID_ARGUMENT, "Too many isolated records to list in one request.");
            rate = charge(source, data.getInvalidMembershipRecordCount(), false);
            return rate == null ? ok("") : rate;
        }
        if (action == Action.LIST || action == Action.GUI) return ok("");
        if (action == Action.SELECTED || action == Action.DEBUG) {
            var pos = NetworkSelectionData.get(player);
            var n = pos == null ? null : data.findNetworkContaining(player.serverLevel(), pos);
            return n == null ? result(Code.NOT_FOUND, "No compiled network selected.") : new Result(Code.OK, "", List.of(n));
        }
        boolean folders = action == Action.CREATE_FOLDER || action == Action.RENAME_FOLDER || action == Action.MOVE_FOLDERS || action == Action.DELETE_FOLDERS;
        if (ids.isEmpty()) return result(Code.INVALID_ARGUMENT, "No targets selected.");
        int first = ids.iterator().next();
        List<CompiledNetwork> networks = new ArrayList<>(); long work = 0;
        for (int id : ids) {
            if (folders) {
                if (!data.folderExists(id)) return result(Code.NOT_FOUND, "Folder does not exist.");
            } else {
                var n = data.getNetwork(id);
                if (n == null) return result(Code.NOT_FOUND, "Network does not exist or is isolated.");
                networks.add(n); work += n.getElements().size();
                if (work > OperationLimits.ELEMENTS) return result(Code.INVALID_ARGUMENT, "Request exceeds 50000 elements.");
            }
        }
        rate = charge(source, (int)work, false); if (rate != null) return rate;
        boolean success;
        switch (action) {
            case HIGHLIGHT -> { return new Result(Code.OK, "", List.copyOf(networks)); }
            case RENAME_NETWORK -> { networks.get(0).setName(value.trim()); data.setDirty(); success = true; }
            case MOVE_NETWORKS -> success = data.moveNetworks(ids, target);
            case MOVE_PATH -> {
                String path = value == null ? "" : value.trim().replace('\\', '/');
                path = path.replaceAll("/+", "/").replaceAll("^/|/$", "");
                if (path.equalsIgnoreCase("root")) path = "";
                for (String part : path.split("/")) if (!path.isEmpty() && !validName(part, true)) return result(Code.INVALID_ARGUMENT, "Invalid folder path.");
                int folder = data.findFolderByPath(path);
                success = folder >= 0 && data.moveNetworks(ids, folder);
            }
            case CREATE_FOLDER -> success = data.createFolder(value.trim(), first) >= 0;
            case RENAME_FOLDER -> success = data.renameFolder(first, value.trim());
            case MOVE_FOLDERS -> success = data.moveFolders(ids, target);
            case DELETE_FOLDERS -> success = data.deleteFolders(ids);
            case DECOMPILE -> {
                if(!data.canRetireNetworks(networks))return result(Code.BUSY,"Decompile cleanup queue is full; retry after pending work completes.");
                var removed = data.removeNetworks(ids); success = !removed.isEmpty();
                if (success) {
                    DamageNotifications.removed(source.getServer(), removed);
                    // Output callbacks run in the shared runtime budget at tick END.
                }
            }
            case REPAIR -> { return RepairJobs.submit(source,player,data,networks); }
            default -> { return result(Code.INVALID_ARGUMENT, "Unsupported operation."); }
        }
        return success ? ok("Operation completed.") : result(Code.CONFLICT, "Invalid destination, nonempty folder, duplicate name or folder cycle. Nothing changed.");
    }
    private static Result compile(CommandSourceStack source, ServerPlayer player, NetworkSavedData data, String name) {
        if (data.hasUnknownMembershipReservations()) return result(Code.CONFLICT, "Unresolved saved membership records.");
        var pos = NetworkSelectionData.get(player);
        if (pos == null) return result(Code.NOT_FOUND, "No circuit selected.");
        Result rate = charge(source, OperationLimits.ELEMENTS, false); if (rate != null) return rate;
        return CompilationJobs.submit(source, player, data, pos, name);
    }
}
