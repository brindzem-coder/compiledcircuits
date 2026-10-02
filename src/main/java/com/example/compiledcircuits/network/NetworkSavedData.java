package com.example.compiledcircuits.network;

import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import com.example.compiledcircuits.networking.CompiledElementSync;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class NetworkSavedData extends SavedData {
    private final NetworkRuntime runtime = new NetworkRuntime(this);
    NetworkRuntime runtime() { return runtime; }


    private static final String DATA_NAME =
            "compiledcircuits_networks";

    private final Map<Integer, CompiledNetwork> networks =
            new HashMap<>();

    private final NetworkMembershipIndex membership = new NetworkMembershipIndex();
    // Preserved verbatim when strict membership validation fails. Never silently retry on load.
    private final List<CompoundTag> invalidMembershipRecords = new ArrayList<>();

    private MembershipReservations reservations = new MembershipReservations();
    public boolean hasUnknownMembershipReservations() { return reservations.unknown; }
    public Set<BlockPos> getBlockedPositions(String dimension) { return reservations.positions(dimension); }
    public Set<BlockPos> getBlockedPositions(String dimension, long chunk) {
        return java.util.Collections.unmodifiableSet(reservations.chunks.getOrDefault(dimension, Map.of()).getOrDefault(chunk, Set.of()));
    }
    public Set<String> getBlockingRecords(String dimension, BlockPos pos) { return reservations.at(dimension, pos); }

    private void rebuildReservations() {
        reservations = new MembershipReservations();
        for (var record : invalidMembershipRecords) reservations.add(record);
    }

    /** Explicit administrative deletion of one raw record, never automatic reactivation. */
    public boolean removeInvalidMembershipRecord(String recordId) {
        requireMutationThread();
        var affected = new HashSet<String>();
        for (var key : reservations.claims.keySet()) affected.add(key.dimension());
        if (!invalidMembershipRecords.removeIf(record -> record.getString("recordId").equals(recordId))) return false;
        rebuildReservations();
        setDirty();
        for (String dimension : affected) CompiledElementSync.markDimensionDirty(dimension);
        // Unknown reservations affect every dimension, including ones without known positions.
        reservationsChanged = true;
        return true;
    }
    private boolean reservationsChanged = true;
    public boolean consumeReservationsChanged() {
        boolean changed = reservationsChanged; reservationsChanged = false; return changed;
    }

    public long getIsolationWorkSize() {
        long size = invalidMembershipRecords.size();
        if (size > OperationLimits.IDS) return Long.MAX_VALUE;
        for (var record : invalidMembershipRecords) {
            var raw = record.getCompound("raw");
            for (String field : List.of("elements", "wires", "inputs", "outputs")) {
                size += raw.getList(field, Tag.TAG_COMPOUND).size();
                if (size > OperationLimits.ELEMENTS) return size;
            }
        }
        return size;
    }
    public int getInvalidMembershipRecordCount() { return invalidMembershipRecords.size(); }
    public List<String> getInvalidMembershipSummaries() {
        return invalidMembershipRecords.stream().map(record -> {
            var raw = record.getCompound("raw");
            String name = raw.getString("name"), reason = record.getString("reason");
            return record.getString("recordId") + " | " + name.substring(0, Math.min(name.length(), 64))
                    + " (#" + raw.getInt("id") + ") | " + reason.substring(0, Math.min(reason.length(), 512));
        }).toList();
    }

    public boolean hasInvalidMembershipRecords() { return !invalidMembershipRecords.isEmpty(); }

    /** Defensive copies: callers cannot edit the stored evidence/reservations. */
    public List<CompoundTag> getInvalidMembershipRecords() {
        return invalidMembershipRecords.stream().map(CompoundTag::copy).toList();
    }

    private void preserveInvalidMembership(CompoundTag raw, String reason) {
        CompoundTag record = new CompoundTag();
        record.putString("recordId", java.util.UUID.randomUUID().toString());
        record.putString("reason", reason);
        record.put("raw", raw.copy());
        invalidMembershipRecords.add(record);
        reservations.add(record);
        org.slf4j.LoggerFactory.getLogger(NetworkSavedData.class).error(
                "Preserved invalid membership record {} (network {}): {}. Known positions remain reserved; incomplete records block new compilation until administrative resolution.",
                record.getString("recordId"), raw.getInt("id"), reason);
    }

    private void requireResolvedMembership() {
        if (hasUnknownMembershipReservations())
            throw new IllegalStateException("Unresolved saved membership records; new compilation is blocked.");
    }


    // Zero means the positive int ID space has been exhausted; persisted across reloads.
    private int nextNetworkId = 1;
    private final Thread mutationThread = Thread.currentThread();

    private void requireMutationThread() {
        if (Thread.currentThread() != mutationThread)
            throw new IllegalStateException("Network membership must be changed on its owning server thread.");
    }

    public static final class AdmissionException extends IllegalArgumentException {
        public AdmissionException(String message) { super(message); }
    }

    public NetworkSavedData() {
    }

    private final Map<Integer, CircuitFolder> folders =
            new HashMap<>();

    private int nextFolderId = 1;

    public Collection<CircuitFolder> getFolders() {
        return folders.values();
    }

    public CircuitFolder getFolder(int id) {
        return folders.get(id);
    }

    public String getFolderPath(int id) {
        CircuitFolder folder = folders.get(id);
        if (folder == null) return "";
        String parent = getFolderPath(folder.getParentId());
        return parent.isEmpty() ? folder.getName() : parent + "/" + folder.getName();
    }

    public int findFolderByPath(String path) {
        int parentId = 0;
        if (path.isEmpty()) return parentId;
        for (String name : path.split("/")) {
            int found = -1;
            for (CircuitFolder folder : folders.values()) {
                if (folder.getParentId() == parentId && folder.getName().equalsIgnoreCase(name)) {
                    found = folder.getId();
                    break;
                }
            }
            if (found < 0) return -1;
            parentId = found;
        }
        return parentId;
    }

    public boolean folderExists(int id) {
        return id == 0 || folders.containsKey(id);
    }

    public int createFolder(
            String name,
            int parentId
    ) {

        name = name.trim();

        if (name.isEmpty()) {
            return -1;
        }

        if (name.contains("/")
                || name.contains("\\")) {
            return -1;
        }

        if (!folderExists(parentId)) {
            return -1;
        }

        /*
         * Не дозволяємо дві папки з однаковим
         * ім'ям в одному parent.
         */
        for (CircuitFolder folder : folders.values()) {

            if (folder.getParentId() == parentId
                    && folder.getName()
                    .equalsIgnoreCase(name)) {

                return -1;
            }
        }

        int id =
                nextFolderId++;

        folders.put(
                id,
                new CircuitFolder(
                        id,
                        name,
                        parentId
                )
        );

        setDirty();

        return id;
    }

    public boolean renameFolder(
            int folderId,
            String newName
    ) {

        if (folderId == 0) {
            return false;
        }

        CircuitFolder folder =
                folders.get(folderId);

        if (folder == null) {
            return false;
        }

        newName =
                newName.trim();

        if (newName.isEmpty()
                || newName.contains("/")
                || newName.contains("\\")) {

            return false;
        }

        for (CircuitFolder other : folders.values()) {

            if (other.getId() == folderId) {
                continue;
            }

            if (other.getParentId()
                    == folder.getParentId()
                    && other.getName()
                    .equalsIgnoreCase(newName)) {

                return false;
            }
        }

        folder.setName(newName);

        setDirty();

        return true;
    }

    public boolean deleteFolder(
            int folderId
    ) {

        if (folderId == 0) {
            return false;
        }

        if (!folders.containsKey(folderId)) {
            return false;
        }

        /*
         * Є child folders?
         */
        for (CircuitFolder folder : folders.values()) {

            if (folder.getParentId() == folderId) {
                return false;
            }
        }

        /*
         * Є networks?
         */
        for (CompiledNetwork network : networks.values()) {

            if (network.getFolderId() == folderId) {
                return false;
            }
        }

        folders.remove(folderId);

        setDirty();

        return true;
    }

    public boolean moveNetwork(
            int networkId,
            int targetFolderId
    ) {

        CompiledNetwork network =
                networks.get(networkId);

        if (network == null) {
            return false;
        }

        if (!folderExists(targetFolderId)) {
            return false;
        }

        network.setFolderId(
                targetFolderId
        );

        setDirty();

        return true;
    }

    public boolean moveFolder(
            int folderId,
            int targetParentId
    ) {

        if (folderId == 0) {
            return false;
        }

        CircuitFolder folder =
                folders.get(folderId);

        if (folder == null) {
            return false;
        }

        if (!folderExists(targetParentId)) {
            return false;
        }

        if (folderId == targetParentId) {
            return false;
        }

        /*
         * Не дозволяємо перемістити folder
         * всередину власного descendant.
         */
        if (isDescendant(
                targetParentId,
                folderId
        )) {
            return false;
        }

        /*
         * Duplicate name у target.
         */
        for (CircuitFolder other : folders.values()) {

            if (other.getId() == folderId) {
                continue;
            }

            if (other.getParentId()
                    == targetParentId
                    && other.getName()
                    .equalsIgnoreCase(
                            folder.getName()
                    )) {

                return false;
            }
        }

        folder.setParentId(
                targetParentId
        );

        setDirty();

        return true;
    }

    public boolean moveNetworks(Collection<Integer> ids, int targetFolderId) {
        if (ids.isEmpty() || !folderExists(targetFolderId) || !networks.keySet().containsAll(ids)) {
            return false;
        }
        for (int id : ids) networks.get(id).setFolderId(targetFolderId);
        setDirty();
        return true;
    }

    /** An empty result signals a rejected operation; no networks are removed. */
    public List<CompiledNetwork> removeNetworks(Collection<Integer> ids) {
        requireMutationThread();
        if (ids.isEmpty() || !networks.keySet().containsAll(ids)) return List.of();
        List<CompiledNetwork> removed = new ArrayList<>();
        for (int id : new LinkedHashSet<>(ids)) removed.add(networks.remove(id));
        for (CompiledNetwork network : removed) { membership.remove(network); runtime.remove(network); }
        for (CompiledNetwork network : removed) CompiledElementSync.markDimensionDirty(network.getDimension());
        setDirty();
        return removed;
    }

    public Set<Integer> getTopLevelSelectedFolders(Collection<Integer> ids) {
        Set<Integer> selected = new HashSet<>(ids);
        Set<Integer> result = new LinkedHashSet<>();
        for (int id : ids) {
            CircuitFolder folder = folders.get(id);
            if (folder == null) continue;
            int parent = folder.getParentId();
            Set<Integer> visited = new HashSet<>();
            boolean selectedAncestor = false;
            while (parent != 0) {
                if (!visited.add(parent) || selected.contains(parent)) {
                    selectedAncestor = true;
                    break;
                }
                CircuitFolder ancestor = folders.get(parent);
                if (ancestor == null) break;
                parent = ancestor.getParentId();
            }
            if (!selectedAncestor) result.add(id);
        }
        return result;
    }

    public boolean moveFolders(Collection<Integer> ids, int targetParentId) {
        if (ids.isEmpty() || ids.contains(0) || !folderExists(targetParentId)
                || !folders.keySet().containsAll(ids)) return false;
        Set<Integer> topLevel = getTopLevelSelectedFolders(ids);
        if (topLevel.isEmpty()) return false;
        // Includes conflicts between selected folders originally in different parents.
        Set<String> targetNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (CircuitFolder folder : folders.values()) {
            if (folder.getParentId() == targetParentId && !topLevel.contains(folder.getId())) {
                targetNames.add(folder.getName());
            }
        }
        for (int id : topLevel) {
            if (id == targetParentId || isDescendant(targetParentId, id)
                    || !targetNames.add(folders.get(id).getName())) return false;
        }
        for (int id : topLevel) folders.get(id).setParentId(targetParentId);
        setDirty();
        return true;
    }

    public boolean deleteFolders(Collection<Integer> ids) {
        Set<Integer> selected = new HashSet<>(ids);
        if (selected.isEmpty() || selected.contains(0) || !folders.keySet().containsAll(selected)) {
            return false;
        }
        // Only absolutely empty folders may be deleted, even if children are selected too.
        for (CircuitFolder folder : folders.values()) {
            if (selected.contains(folder.getParentId())) return false;
        }
        for (CompiledNetwork network : networks.values()) {
            if (selected.contains(network.getFolderId())) return false;
        }
        for (int id : selected) folders.remove(id);
        setDirty();
        return true;
    }

    private boolean isDescendant(
            int possibleChildId,
            int ancestorId
    ) {

        int current =
                possibleChildId;

        while (current != 0) {

            if (current == ancestorId) {
                return true;
            }

            CircuitFolder folder =
                    folders.get(current);

            if (folder == null) {
                return false;
            }

            current =
                    folder.getParentId();
        }

        return false;
    }

    public static NetworkSavedData get(
            MinecraftServer server
    ) {

        NetworkSavedData data = server
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        NetworkSavedData::load,
                        NetworkSavedData::new,
                        DATA_NAME
                );
        data.runtime.bind(server);
        return data;
    }

    /** Preview only. A successful admission commits the ID; rejection never consumes it. */
    public int getNextNetworkId() {
        requireMutationThread();
        requireResolvedMembership();
        if (nextNetworkId == 0) throw new AdmissionException("Network ID space exhausted.");
        return nextNetworkId;
    }

    public record ElementLocation(CompiledNetwork network, CompiledCircuitElement element) { }

    /** Immutable, deterministic claims; multiple entries explicitly mean ambiguity.
     * No fallback scan, and no claim is discarded because a block is damaged/unloaded.
     */
    public List<ElementLocation> getMembershipOwners(String dimension, BlockPos pos) {
        return membership.owners(dimension, pos);
    }

    /** Only returns an owner when the membership is unambiguous. */
    public ElementLocation findIndexedElementLocation(String dimension, BlockPos pos) {
        List<ElementLocation> owners = membership.owners(dimension, pos);
        return owners.size() == 1 ? owners.get(0) : null;
    }


    private ElementLocation lookup(String metric, String dimension, BlockPos pos, CircuitElementType role) {
        long started = PerformanceDiagnostics.begin();
        PerformanceDiagnostics.add(metric + ".calls", 1);
        try {
            PerformanceDiagnostics.add(metric + ".indexProbes", 1);
            ElementLocation location = findIndexedElementLocation(dimension, pos);
            if (location != null && role != null && location.element().getType() != role) location = null;
            PerformanceDiagnostics.add(metric + (location == null ? ".misses" : ".hits"), 1);
            return location;
        } finally { PerformanceDiagnostics.elapsed(metric, started); }
    }

    public ElementLocation findElementLocation(String dimension, BlockPos pos) {
        return lookup("lookup.findElementLocation", dimension, pos, null);
    }

    public CompiledNetwork findNetworkContaining(ServerLevel level, BlockPos pos) {
        var location = lookup("lookup.findNetworkContaining", level.dimension().location().toString(), pos, null);
        return location == null ? null : location.network();
    }

    public CompiledNetwork findConflict(
            ServerLevel level,
            NetworkScanner.ScanResult result
    ) {
        long diagnosticStart = PerformanceDiagnostics.begin();
        PerformanceDiagnostics.add("lookup.findConflict.calls", 1);
        try {

        String dimension =
                level.dimension()
                        .location()
                        .toString();

        Set<BlockPos> positions = new TreeSet<>();
        positions.addAll(result.wires());
        positions.addAll(result.inputs());
        positions.addAll(result.outputs());
        for (BlockPos pos : positions) {
            PerformanceDiagnostics.add("lookup.conflictPositionsVisited", 1);
            var owners = membership.owners(dimension, pos);
            if (!owners.isEmpty()) {
                PerformanceDiagnostics.add("lookup.findConflict.hits", 1);
                return owners.get(0).network();
            }
        }

        PerformanceDiagnostics.add("lookup.findConflict.misses", 1);
        return null;

        } finally { PerformanceDiagnostics.elapsed("lookup.findConflict", diagnosticStart); }
    }

    public void addNetwork(CompiledNetwork network) {
        addNetworks(List.of(network));
    }

    /** All candidates are admitted together, or none are changed. */
    public void addNetworks(Collection<CompiledNetwork> candidates) {
        admit(candidates, false);
    }

    /** Explicit replacement; only the old network with this ID may relinquish its claims. */
    public void replaceNetwork(CompiledNetwork replacement) {
        admit(List.of(replacement), true);
    }

    private void admit(Collection<CompiledNetwork> candidates, boolean replacing) {
        requireMutationThread();
        requireResolvedMembership();
        var ordered = candidates.stream().sorted(java.util.Comparator.comparingInt(CompiledNetwork::getId)).toList();
        if (ordered.isEmpty()) throw new AdmissionException("No candidate networks.");
        Set<Integer> ids = new HashSet<>();
        CompiledNetwork previous = replacing ? networks.get(ordered.get(0).getId()) : null;
        if (replacing && previous == null) throw new AdmissionException("Replacement network does not exist.");
        int committedNextId = nextNetworkId;
        for (var network : ordered) {
            if (reservations.networkIds.contains(network.getId()))
                throw new AdmissionException("Network ID " + network.getId() + " is reserved by isolated records.");
            for (var element : network.getElements().stream().sorted(java.util.Comparator.comparing(CompiledCircuitElement::getPos)).toList()) {
                var blocked = getBlockingRecords(network.getDimension(), element.getPos());
                if (!blocked.isEmpty()) throw new AdmissionException("Position " + element.getPos().toShortString()
                        + " in " + network.getDimension() + " is reserved by blocked record " + blocked.iterator().next()
                        + ". See /circuit conflicts list.");
            }
            if (!ids.add(network.getId())) throw new AdmissionException("Duplicate candidate network ID " + network.getId());
            if (!replacing && networks.containsKey(network.getId()))
                throw new AdmissionException("Network ID " + network.getId() + " already exists; use explicit replacement.");
            if (committedNextId != 0 && network.getId() >= committedNextId)
                committedNextId = network.getId() == Integer.MAX_VALUE ? 0 : network.getId() + 1;
        }
        Runnable commitMembership = membership.prepareAddition(ordered, previous);
        // Validation and preparation completed. No callbacks until data and index agree.
        for (var network : ordered) networks.put(network.getId(), network);
        commitMembership.run();
        if (previous != null) runtime.remove(previous);
        for (var network : ordered) runtime.add(network);
        nextNetworkId = committedNextId;
        setDirty();
        if (previous != null) CompiledElementSync.markDimensionDirty(previous.getDimension());
        for (var network : ordered) CompiledElementSync.markDimensionDirty(network.getDimension());
    }

    public CompiledNetwork getNetwork(int id) {
        return networks.get(id);
    }

    public Collection<CompiledNetwork> getNetworks() {
        return java.util.Collections.unmodifiableCollection(networks.values());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {

        tag.putInt(
                "nextNetworkId",
                nextNetworkId
        );

        ListTag list = new ListTag();

        for (CompiledNetwork network
                : networks.values()) {

            list.add(network.save());
        }

        tag.put("networks", list);
        ListTag invalid = new ListTag();
        for (CompoundTag record : invalidMembershipRecords) invalid.add(record.copy());
        tag.put("invalidMembershipRecords", invalid);

        tag.putInt(
                "nextFolderId",
                nextFolderId
        );

        ListTag folderList =
                new ListTag();

        for (CircuitFolder folder
                : folders.values()) {

            folderList.add(
                    folder.save()
            );
        }

        tag.put(
                "folders",
                folderList
        );

        return tag;
    }

    public static NetworkSavedData load(
            CompoundTag tag
    ) {

        NetworkSavedData data =
                new NetworkSavedData();

        data.nextNetworkId =
                tag.getInt("nextNetworkId");

        if (data.nextNetworkId < 0 || !tag.contains("nextNetworkId")) {
            data.nextNetworkId = 1;
        }

        ListTag list =
                tag.getList(
                        "networks",
                        Tag.TAG_COMPOUND
                );

        ListTag preserved = tag.getList("invalidMembershipRecords", Tag.TAG_COMPOUND);
        Set<String> recordIds = new HashSet<>();
        boolean normalizedRecords = false;
        for (int i = 0; i < preserved.size(); i++) {
            var record = preserved.getCompound(i).copy();
            String id = record.getString("recordId");
            boolean validId;
            try { validId = java.util.UUID.fromString(id).toString().equals(id); }
            catch (IllegalArgumentException invalid) { validId = false; }
            if (!validId || !recordIds.add(id)) {
                record.putString("recordId", java.util.UUID.randomUUID().toString());
                recordIds.add(record.getString("recordId"));
                normalizedRecords = true;
            }
            data.invalidMembershipRecords.add(record);
        }
        if (tag.contains("networks") && (!(tag.get("networks") instanceof ListTag rawList)
                || (!rawList.isEmpty() && rawList.getElementType() != Tag.TAG_COMPOUND))) {
            var evidence = new CompoundTag(); evidence.put("unreadableNetworks", tag.get("networks").copy());
            data.preserveInvalidMembership(evidence, "Unreadable network list; reservation scope unknown.");
            normalizedRecords = true;
        }
        if (tag.contains("invalidMembershipRecords") && (!(tag.get("invalidMembershipRecords") instanceof ListTag rawPreserved)
                || (!rawPreserved.isEmpty() && rawPreserved.getElementType() != Tag.TAG_COMPOUND))) {
            var evidence = new CompoundTag(); evidence.put("unreadableIsolatedRecords", tag.get("invalidMembershipRecords").copy());
            data.preserveInvalidMembership(evidence, "Unreadable isolation list; reservation scope unknown.");
            normalizedRecords = true;
        }
        data.rebuildReservations();
        Map<Integer, CompoundTag> originalRecords = new HashMap<>();
        Map<Integer, Integer> idCounts = new HashMap<>();
        for (int i = 0; i < list.size(); i++) idCounts.merge(list.getCompound(i).getInt("id"), 1, Integer::sum);
        boolean migratedLegacyData = normalizedRecords;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag raw = list.getCompound(i);
            if (idCounts.get(raw.getInt("id")) > 1) {
                data.preserveInvalidMembership(raw, "Duplicate network ID " + raw.getInt("id"));
                migratedLegacyData = true;
                continue;
            }
            try {
                CompiledNetwork network = CompiledNetwork.load(raw);
                if (network.needsPersistenceUpgrade()) migratedLegacyData = true;
                data.networks.put(network.getId(), network);
                originalRecords.put(network.getId(), raw.copy());
            } catch (IllegalArgumentException invalid) {
                data.preserveInvalidMembership(raw, invalid.getMessage());
                migratedLegacyData = true;
            }
        }

        // Inspect all candidate claims before excluding any record: every participant is blocked.
        var owners = new HashMap<MembershipReservations.Key, List<CompiledNetwork>>();
        for (var network : data.networks.values()) for (var element : network.getElements()) {
            var key = new MembershipReservations.Key(network.getDimension(), element.getPos());
            owners.computeIfAbsent(key, ignored -> new ArrayList<>()).add(network);
        }
        var reasons = new java.util.TreeMap<Integer, String>();
        for (var network : data.networks.values()) if (data.reservations.networkIds.contains(network.getId()))
            reasons.put(network.getId(), "Network ID " + network.getId() + " is also claimed by an isolated record.");
        owners.entrySet().stream().sorted(java.util.Comparator
                .comparing((Map.Entry<MembershipReservations.Key, List<CompiledNetwork>> e) -> e.getKey().dimension())
                .thenComparing(e -> e.getKey().pos())).forEach(entry -> {
            var key = entry.getKey();
            var networksAtPosition = entry.getValue();
            if (networksAtPosition.size() > 1 || !data.reservations.at(key.dimension(), key.pos()).isEmpty()) {
                String reason = "Conflicting membership at " + key.pos().toShortString() + " in " + key.dimension()
                        + "; network IDs " + networksAtPosition.stream().map(CompiledNetwork::getId).sorted().toList();
                for (var network : networksAtPosition) reasons.putIfAbsent(network.getId(), reason);
            }
        });
        for (var entry : reasons.entrySet()) {
            data.preserveInvalidMembership(originalRecords.get(entry.getKey()), entry.getValue());
            data.networks.remove(entry.getKey());
            migratedLegacyData = true;
        }
        // Include isolated IDs in allocation so they cannot be reused after load.
        for (int id : data.reservations.networkIds) if (data.nextNetworkId != 0 && id >= data.nextNetworkId) {
            data.nextNetworkId = id == Integer.MAX_VALUE ? 0 : id + 1;
            migratedLegacyData = true;
        }

        data.nextFolderId =
                tag.contains("nextFolderId")
                        ? tag.getInt("nextFolderId")
                        : 1;

        if (data.nextFolderId <= 0) {
            data.nextFolderId = 1;
        }

        ListTag folderList =
                tag.getList(
                        "folders",
                        Tag.TAG_COMPOUND
                );

        for (int i = 0;
             i < folderList.size();
             i++) {

            CircuitFolder folder =
                    CircuitFolder.load(
                            folderList.getCompound(i)
                    );

            data.folders.put(
                    folder.getId(),
                    folder
            );
        }

        for (CompiledNetwork network : data.networks.values()) {
            data.membership.add(network);
            data.runtime.add(network);
            if (data.nextNetworkId != 0 && network.getId() >= data.nextNetworkId) {
                data.nextNetworkId = network.getId() == Integer.MAX_VALUE ? 0 : network.getId() + 1;
                migratedLegacyData = true;
            }
        }
        if (migratedLegacyData) data.setDirty();
        return data;
    }

    public CompiledNetwork findNetworkByInput(ServerLevel level, BlockPos pos) {
        var location = lookup("lookup.findNetworkByInput", level.dimension().location().toString(), pos, CircuitElementType.INPUT);
        return location == null ? null : location.network();
    }

    public CompiledNetwork findNetworkByOutput(ServerLevel level, BlockPos pos) {
        var location = lookup("lookup.findNetworkByOutput", level.dimension().location().toString(), pos, CircuitElementType.OUTPUT);
        return location == null ? null : location.network();
    }

    public boolean removeNetwork(int id) {
        requireMutationThread();

        CompiledNetwork removed =
                networks.remove(id);

        if (removed == null) {
            return false;
        }

        membership.remove(removed);
        runtime.remove(removed);
        CompiledElementSync.markDimensionDirty(removed.getDimension());
        setDirty();
        return true;
    }
}
