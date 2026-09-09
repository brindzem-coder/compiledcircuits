package com.example.compiledcircuits.network;

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

    private static final String DATA_NAME =
            "compiledcircuits_networks";

    private final Map<Integer, CompiledNetwork> networks =
            new HashMap<>();

    private int nextNetworkId = 1;

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
        if (ids.isEmpty() || !networks.keySet().containsAll(ids)) return List.of();
        List<CompiledNetwork> removed = new ArrayList<>();
        for (int id : new LinkedHashSet<>(ids)) removed.add(networks.remove(id));
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

        return server
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        NetworkSavedData::load,
                        NetworkSavedData::new,
                        DATA_NAME
                );
    }

    public int getNextNetworkId() {
        return nextNetworkId++;
    }

    public record ElementLocation(CompiledNetwork network, CompiledCircuitElement element) { }

    public ElementLocation findElementLocation(String dimension, BlockPos pos) {
        for (CompiledNetwork network : networks.values()) {
            if (!network.getDimension().equals(dimension)) continue;
            CompiledCircuitElement element = network.getElementAt(pos);
            if (element != null) return new ElementLocation(network, element);
        }
        return null;
    }

    public CompiledNetwork findNetworkContaining(
            ServerLevel level,
            BlockPos pos
    ) {

        String dimension =
                level.dimension()
                        .location()
                        .toString();

        for (CompiledNetwork network : networks.values()) {

            if (!network.getDimension().equals(dimension)) {
                continue;
            }

            if (network.getWires().contains(pos)
                    || network.getInputs().contains(pos)
                    || network.getOutputs().contains(pos)) {

                return network;
            }
        }

        return null;
    }

    public CompiledNetwork findConflict(
            ServerLevel level,
            NetworkScanner.ScanResult result
    ) {

        String dimension =
                level.dimension()
                        .location()
                        .toString();

        for (CompiledNetwork network : networks.values()) {

            if (!network.getDimension().equals(dimension)) {
                continue;
            }

            if (containsAny(
                    network.getWires(),
                    result.wires()
            )) {
                return network;
            }

            if (containsAny(
                    network.getInputs(),
                    result.inputs()
            )) {
                return network;
            }

            if (containsAny(
                    network.getOutputs(),
                    result.outputs()
            )) {
                return network;
            }
        }

        return null;
    }

    private static boolean containsAny(
            java.util.Set<BlockPos> first,
            java.util.Set<BlockPos> second
    ) {

        for (BlockPos pos : second) {
            if (first.contains(pos)) {
                return true;
            }
        }

        return false;
    }

    public void addNetwork(
            CompiledNetwork network
    ) {

        CompiledNetwork previous = networks.put(network.getId(), network);
        if (previous != null) CompiledElementSync.markDimensionDirty(previous.getDimension());
        CompiledElementSync.markDimensionDirty(network.getDimension());
        setDirty();
    }

    public CompiledNetwork getNetwork(int id) {
        return networks.get(id);
    }

    public Collection<CompiledNetwork> getNetworks() {
        return networks.values();
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

        if (data.nextNetworkId <= 0) {
            data.nextNetworkId = 1;
        }

        ListTag list =
                tag.getList(
                        "networks",
                        Tag.TAG_COMPOUND
                );

        boolean migratedLegacyData = false;
        for (int i = 0; i < list.size(); i++) {
            if (!list.getCompound(i).contains("elements", Tag.TAG_LIST)) migratedLegacyData = true;

            CompiledNetwork network =
                    CompiledNetwork.load(
                            list.getCompound(i)
                    );

            if (network.needsPersistenceUpgrade()) migratedLegacyData = true;
            data.networks.put(
                    network.getId(),
                    network
            );
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

        if (migratedLegacyData) data.setDirty();
        return data;
    }

    public CompiledNetwork findNetworkByInput(
            ServerLevel level,
            BlockPos pos
    ) {

        String dimension =
                level.dimension()
                        .location()
                        .toString();

        for (CompiledNetwork network
                : networks.values()) {

            if (!network.getDimension()
                    .equals(dimension)) {
                continue;
            }

            if (network.getInputs().contains(pos)) {
                return network;
            }
        }

        return null;
    }

    public CompiledNetwork findNetworkByOutput(
            ServerLevel level,
            BlockPos pos
    ) {

        String dimension =
                level.dimension()
                        .location()
                        .toString();

        for (CompiledNetwork network
                : networks.values()) {

            if (!network.getDimension()
                    .equals(dimension)) {
                continue;
            }

            if (network.getOutputs().contains(pos)) {
                return network;
            }
        }

        return null;
    }

    public boolean removeNetwork(int id) {

        CompiledNetwork removed =
                networks.remove(id);

        if (removed == null) {
            return false;
        }

        CompiledElementSync.markDimensionDirty(removed.getDimension());
        setDirty();
        return true;
    }
}