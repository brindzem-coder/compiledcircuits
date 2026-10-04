package com.example.compiledcircuits.client;

import com.example.compiledcircuits.networking.NetworkListS2CPacket;
import com.example.compiledcircuits.networking.BrokenElementListS2CPacket;
import java.util.*;
import static com.example.compiledcircuits.client.ManagerState.*;

/** Immutable server records and derived rows, with no Screen, renderer or transport dependency. */
final class ManagerModel {
    enum SearchResultType { FOLDER, NETWORK }
    record SearchResult(SearchResultType type, int id, String name, String path, NetworkListS2CPacket.Entry network) {}
    record VisibleFolderRow(FolderNode node, int depth) {}
    private final ManagerState state;
    List<NetworkListS2CPacket.Entry> entries = List.of();
    List<NetworkListS2CPacket.FolderEntry> folders = List.of();
    List<VisibleFolderRow> visibleFolderRows = List.of();
    List<NetworkListS2CPacket.Entry> visibleNetworks = List.of();
    List<SearchResult> searchResults = List.of();
    List<BrokenElementListS2CPacket.Entry> brokenEntries = List.of();
    private Map<Integer, NetworkListS2CPacket.Entry> networks = Map.of();
    private Map<Integer, FolderNode> nodes = Map.of();
    private final Set<Integer> brokenNetworkIds = new HashSet<>();
    FolderNode rootFolder;
    String warning = "";
    long treeBuilds, rowBuilds, searchBuilds;

    ManagerModel(ManagerState state) { this.state = state; }
    NetworkListS2CPacket.Entry network(int id) { return networks.get(id); }
    FolderNode folder(int id) { return nodes.get(id); }
    boolean isNetworkBroken(int id) { return brokenNetworkIds.contains(id); }
    boolean isSearchMode() { return !state.searchText.trim().isEmpty(); }

    void update(List<NetworkListS2CPacket.Entry> newEntries, List<NetworkListS2CPacket.FolderEntry> newFolders) {
        var entryCopy = List.copyOf(newEntries); var folderCopy = List.copyOf(newFolders);
        if (rootFolder != null && entries.equals(entryCopy) && folders.equals(folderCopy)) return;
        int current = state.selectedFolderId;
        var ancestry = new ArrayList<Integer>(); var visited = new HashSet<Integer>();
        while (current != 0 && visited.add(current)) {
            ancestry.add(current); var n = folder(current); if (n == null) break; current = n.getParentId();
        }
        entries = entryCopy;
        var index = new LinkedHashMap<Integer, NetworkListS2CPacket.Entry>();
        for (var entry : entries) index.putIfAbsent(entry.id(), entry);
        networks = Map.copyOf(index);
        if (rootFolder == null || !folders.equals(folderCopy)) { folders = folderCopy; buildTree(); }
        if (folder(state.selectedFolderId) == null) {
            state.selectedFolderId = ancestry.stream().filter(id -> folder(id) != null).findFirst().orElse(0);
            state.networkScroll = 0;
        }
        state.reconcile(this);
        rebuildVisibleFolderRows(); rebuildVisibleNetworks(); rebuildSearchResults();
    }

    private void buildTree() {
        treeBuilds++; warning = "";
        var map = new LinkedHashMap<Integer, FolderNode>();
        rootFolder = new FolderNode(0, "/", -1); map.put(0, rootFolder);
        for (var folder : folders) {
            if (folder.id() <= 0 || map.containsKey(folder.id())) { warning = "Invalid folder records; showing safe tree."; continue; }
            map.put(folder.id(), new FolderNode(folder.id(), folder.name(), folder.parentId()));
        }
        // Validate parent chains before attaching. Invalid branches are displayed at root;
        // original server records remain untouched. Iterative traversal also handles deep trees.
        for (var node : map.values()) {
            if (node == rootFolder) continue;
            var visited = new HashSet<Integer>(); int id = node.getId(); boolean valid = true;
            while (id != 0) {
                var ancestor = map.get(id);
                if (ancestor == null || !visited.add(id)) { valid = false; break; }
                id = ancestor.getParentId();
            }
            if (!valid) warning = "Invalid folder hierarchy; affected folders shown at root.";
            (valid ? map.get(node.getParentId()) : rootFolder).addChild(node);
        }
        nodes = Map.copyOf(map);
    }
    void toggleFolder(int id) {
        if (folder(id) == null) return;
        if (!state.collapsedFolders.add(id)) state.collapsedFolders.remove(id);
        rebuildVisibleFolderRows();
    }
    void expandPathToFolder(int id) {
        state.collapsedFolders.remove(0); var visited = new HashSet<Integer>();
        while (id != 0 && visited.add(id)) {
            var node = folder(id); if (node == null) break;
            state.collapsedFolders.remove(id); id = node.getParentId();
        }
        rebuildVisibleFolderRows();
    }
    void rebuildVisibleFolderRows() {
        rowBuilds++; var rows = new ArrayList<VisibleFolderRow>();
        var stack = new ArrayDeque<VisibleFolderRow>(); stack.push(new VisibleFolderRow(rootFolder, 0));
        while (!stack.isEmpty()) {
            var row = stack.pop(); var node = row.node(); rows.add(row);
            node.setExpanded(!state.collapsedFolders.contains(node.getId()));
            if (!node.isExpanded()) continue;
            var children = node.getChildren();
            for (int i = children.size()-1; i >= 0; i--) stack.push(new VisibleFolderRow(children.get(i), row.depth()+1));
        }
        visibleFolderRows = List.copyOf(rows);
    }
    void rebuildVisibleNetworks() {
        visibleNetworks = entries.stream().filter(e -> e.folderId() == state.selectedFolderId)
                .sorted(Comparator.comparingInt(NetworkListS2CPacket.Entry::id)).toList();
    }
    void rebuildSearchResults() {
        searchBuilds++; var results = new ArrayList<SearchResult>();
        String query = state.searchText.trim().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            if (state.searchFilter != SearchFilter.FOLDERS) for (var entry : entries)
                if (entry.name().toLowerCase(Locale.ROOT).contains(query)) results.add(new SearchResult(
                        SearchResultType.NETWORK, entry.id(), entry.name(), getFolderPath(entry.folderId()), entry));
            if (state.searchFilter != SearchFilter.NETWORKS) for (var folder : folders)
                if (folder.id() != 0 && folder.name().toLowerCase(Locale.ROOT).contains(query)) results.add(new SearchResult(
                        SearchResultType.FOLDER, folder.id(), folder.name(), getFolderPath(folder.id()), null));
            results.sort(Comparator.comparing(SearchResult::type).thenComparing(SearchResult::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(SearchResult::id));
        }
        searchResults = List.copyOf(results);
    }
    String getFolderPath(int id) {
        var parts = new ArrayList<String>(); var seen = new HashSet<Integer>();
        while (id != 0 && seen.add(id)) {
            var node = folder(id); if (node == null) break;
            parts.add(node.getName()); id = node.getParentId();
        }
        Collections.reverse(parts); return parts.isEmpty() ? "/" : String.join("/", parts);
    }
    void updateBroken(List<BrokenElementListS2CPacket.Entry> entries) {
        brokenEntries = List.copyOf(entries); brokenNetworkIds.clear();
        var keys = new HashSet<BrokenKey>();
        for (var entry : brokenEntries) { brokenNetworkIds.add(entry.networkId()); keys.add(key(entry)); }
        if (state.selectedBrokenKeys.retainAll(keys)) state.selectAllArmedNetworkId = null;
        state.stopBrokenPaint();
    }
    static BrokenKey key(BrokenElementListS2CPacket.Entry entry) { return new BrokenKey(entry.networkId(), entry.elementId()); }
    Set<Integer> selectedBrokenNetworks() {
        var ids = new LinkedHashSet<Integer>();
        for (var entry : brokenEntries) if (state.selectedBrokenKeys.contains(key(entry))) ids.add(entry.networkId());
        return ids;
    }
    void selectAllBroken() {
        if (brokenEntries.isEmpty()) return;
        state.stopBrokenPaint(); var ids = selectedBrokenNetworks();
        Integer only = ids.size() == 1 ? ids.iterator().next() : null;
        boolean all = only == null || Objects.equals(state.selectAllArmedNetworkId, only);
        state.selectedBrokenKeys.clear();
        for (var entry : brokenEntries) if (all || entry.networkId() == only) state.selectedBrokenKeys.add(key(entry));
        state.selectAllArmedNetworkId = all ? null : only;
    }
}
