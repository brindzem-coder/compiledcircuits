package com.example.compiledcircuits.client;

import java.util.*;

/** Per-open-manager state. Widget recreation never owns or resets selection. */
final class ManagerState {
    enum MainViewMode { NETWORKS, BROKEN }
    enum SelectionType { NONE, FOLDERS, NETWORKS }
    enum SearchFilter { BOTH, NETWORKS, FOLDERS }
    enum DragType { NONE, NETWORK, FOLDER }
    record BrokenKey(int networkId, int elementId) {}

    MainViewMode mainViewMode = MainViewMode.NETWORKS;
    SearchFilter searchFilter = SearchFilter.BOTH;
    SelectionType selectionType = SelectionType.NONE;
    final Set<Integer> selectedFolderIds = new LinkedHashSet<>();
    final Set<Integer> selectedNetworkIds = new LinkedHashSet<>();
    final Set<Integer> collapsedFolders = new HashSet<>();
    final Set<BrokenKey> selectedBrokenKeys = new LinkedHashSet<>();
    String searchText = "";
    boolean searchFocused;
    int selectedFolderId, folderScroll, networkScroll, searchScroll, brokenScroll;
    Integer selectAllArmedNetworkId;
    boolean restoreBrokenFocus = true;

    // Transient pointer gestures are cancelled on snapshot, resize and screen removal.
    DragType dragType = DragType.NONE;
    int dragId = -1, paintStartId = -1;
    double dragStartX, dragStartY, paintLastX, paintLastY, searchPaintLastX, searchPaintLastY;
    boolean dragging, shiftPaintSelecting, paintDragging, searchPaintSelecting;
    SelectionType paintSelectionType = SelectionType.NONE, searchPaintSelectionType = SelectionType.NONE;
    final Set<Integer> paintVisitedIds = new HashSet<>(), searchPaintVisitedIds = new HashSet<>();
    boolean brokenPaintSelecting;
    final Set<BrokenKey> brokenPaintVisited = new HashSet<>();
    double brokenPaintX, brokenPaintY;

    void clearSelection() {
        selectedFolderIds.clear(); selectedNetworkIds.clear(); selectionType = SelectionType.NONE;
    }
    void selectItem(SelectionType type, int id, boolean additive) {
        if (type == SelectionType.NONE || type == SelectionType.FOLDERS && id == 0) return;
        if (selectionType != type || !additive) clearSelection();
        Set<Integer> selected = type == SelectionType.FOLDERS ? selectedFolderIds : selectedNetworkIds;
        if (!additive || !selected.remove(id)) selected.add(id);
        selectionType = selected.isEmpty() ? SelectionType.NONE : type;
    }
    void addPaint(SelectionType type, int id) {
        if (type == SelectionType.NONE || type == SelectionType.FOLDERS && id == 0) return;
        if (selectionType != type) clearSelection();
        selectionType = type;
        (type == SelectionType.FOLDERS ? selectedFolderIds : selectedNetworkIds).add(id);
    }
    void reconcile(ManagerModel model) {
        selectedFolderIds.removeIf(id -> id == 0 || model.folder(id) == null);
        selectedNetworkIds.removeIf(id -> model.network(id) == null);
        collapsedFolders.removeIf(id -> model.folder(id) == null);
        if (selectedFolderIds.isEmpty() && selectedNetworkIds.isEmpty()) selectionType = SelectionType.NONE;
        clearDrag(); stopBrokenPaint();
    }
    void clearDrag() {
        searchPaintSelecting = false; searchPaintSelectionType = SelectionType.NONE; searchPaintVisitedIds.clear();
        dragType = DragType.NONE; dragId = -1; dragging = false;
        shiftPaintSelecting = false; paintSelectionType = SelectionType.NONE; paintVisitedIds.clear();
        paintStartId = -1; paintDragging = false;
    }
    void stopBrokenPaint() { brokenPaintSelecting = false; brokenPaintVisited.clear(); }
}
