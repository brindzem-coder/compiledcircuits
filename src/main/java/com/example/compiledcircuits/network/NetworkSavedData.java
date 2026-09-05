package com.example.compiledcircuits.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

        networks.put(
                network.getId(),
                network
        );

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

        for (int i = 0; i < list.size(); i++) {

            CompiledNetwork network =
                    CompiledNetwork.load(
                            list.getCompound(i)
                    );

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

        setDirty();
        return true;
    }
}