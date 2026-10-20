package com.example.compiledcircuits.client;

import com.example.compiledcircuits.networking.ModNetworking;
import com.example.compiledcircuits.networking.NetworkBulkActionC2SPacket;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Collection;
import com.example.compiledcircuits.networking.NetworkActionC2SPacket;
import com.example.compiledcircuits.networking.NetworkListS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.example.compiledcircuits.client.ManagerState.*;
import static com.example.compiledcircuits.client.ManagerModel.*;
import static com.example.compiledcircuits.client.ManagerLayout.*;

public class NetworkManagerScreen
        extends Screen {

    private final ManagerState state = new ManagerState();
    private final ManagerModel model = new ManagerModel(state);
    private final ManagerLayout layout = new ManagerLayout();
    private final ManagerActions actions;
    private final Object connectionContext, levelContext;
    private ManagerRenderer renderer;
    private Button mainViewButton, brokenSelectAllButton, brokenOpenNetworkButton, brokenDecompileButton, brokenRepairButton;
    private Button highlightButton, renameButton, decompileButton, newFolderButton, renameFolderButton, deleteFolderButton, searchFilterButton;
    private EditBox searchBox;
    private Integer initialNavigateNetworkId;
    private boolean openingChild;

    public NetworkManagerScreen(List<NetworkListS2CPacket.Entry> nextEntries,
                                List<NetworkListS2CPacket.FolderEntry> nextFolders) {
        this(nextEntries, nextFolders, null);
    }
    public NetworkManagerScreen(List<NetworkListS2CPacket.Entry> nextEntries,
                                List<NetworkListS2CPacket.FolderEntry> nextFolders, Integer initialNavigateNetworkId) {
        super(Component.literal("Network Manager"));
        this.initialNavigateNetworkId = initialNavigateNetworkId;
        var mc = Minecraft.getInstance();
        connectionContext = mc.getConnection(); levelContext = mc.level;
        model.update(nextEntries, nextFolders);
        actions = new ManagerActions(model, new ManagerActions.Transport() {
            public void single(NetworkActionC2SPacket.Action action, int id, String value) {
                ModNetworking.CHANNEL.sendToServer(new NetworkActionC2SPacket(action, id, value));
            }
            public void bulk(NetworkBulkActionC2SPacket.BulkAction action, Set<Integer> ids, int target) {
                ModNetworking.CHANNEL.sendToServer(new NetworkBulkActionC2SPacket(action, ids, target));
            }
            public void highlight(Set<Integer> ids) { ClientHighlightSync.request(ids); }
            public void refresh() { Minecraft.getInstance().getConnection().sendCommand("circuit gui refresh"); }
        }, System::nanoTime, this::contextValid);
    }
    boolean contextValid() {
        var mc = Minecraft.getInstance();
        return mc.getConnection() == connectionContext && mc.level == levelContext && mc.player != null;
    }
    void closeSession() { actions.close(); }
    void sessionTick() { actions.tick(); }

    public void updateData(List<NetworkListS2CPacket.Entry> nextEntries,
                           List<NetworkListS2CPacket.FolderEntry> nextFolders) {
        if (!actions.active()) return;
        model.update(nextEntries, nextFolders);
        actions.refreshed();
        layout.clamp(model, state);
        updateButtonsSafe();
    }

    // =========================================================
    // INIT
    // =========================================================

    @Override
    protected void init() {
        openingChild = false;
        stopBrokenPaint();
        clearDrag();

        layout.resize(width, height);
        renderer = new ManagerRenderer(model, state, layout, font);

        String searchText = state.searchText;
        boolean restoreSearchFocus = state.searchFocused;
        mainViewButton = addRenderableWidget(Button.builder(Component.literal(getMainViewButtonText()),
                button -> toggleMainView()).bounds(8, 30, 72, 20).build());
        int searchWidth = Math.max(40, Math.min(SEARCH_WIDTH, width - 96 - SEARCH_FILTER_WIDTH));
        int totalSearchWidth = searchWidth + SEARCH_GAP + SEARCH_FILTER_WIDTH;
        int searchX = Math.max(84, (this.width - totalSearchWidth) / 2);
        searchBox = new EditBox(this.font, searchX, 30, searchWidth, SEARCH_HEIGHT,
                Component.literal("Search"));
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchText);
        searchBox.setResponder(value -> {
            state.searchText = value;
            state.searchScroll = 0;
            clearDrag();
            rebuildSearchResults();
            updateButtonsSafe();
        });
        addRenderableWidget(searchBox);
        if (restoreSearchFocus) { setInitialFocus(searchBox); searchBox.setFocused(true); }
        searchFilterButton = addRenderableWidget(Button.builder(
                        Component.literal(getSearchFilterLabel()), button -> cycleSearchFilter())
                .bounds(searchX + searchWidth + SEARCH_GAP, 30, SEARCH_FILTER_WIDTH, SEARCH_HEIGHT)
                .build());

        int buttonY = this.height - 28;
        int actionWidth = Math.max(50, (width - 20) / 2);
        brokenSelectAllButton = addRenderableWidget(Button.builder(Component.literal("Select All"),
                button -> selectAllBrokenSmart()).bounds(8, buttonY - 24, actionWidth, 20).build());
        brokenOpenNetworkButton = addRenderableWidget(Button.builder(Component.literal("Open Network"),
                button -> openSelectedBrokenNetwork()).bounds(12 + actionWidth, buttonY - 24, actionWidth, 20).build());
        brokenRepairButton = addRenderableWidget(Button.builder(Component.literal("Repair Network"),
                button -> repairSelectedBrokenNetworks()).bounds(8, buttonY, actionWidth, 20).build());
        brokenDecompileButton = addRenderableWidget(Button.builder(Component.literal("Decompile Network"),
                button -> decompileSelectedBrokenNetworks()).bounds(12 + actionWidth, buttonY, actionWidth, 20).build());

        int x = layout.folderPanelWidth + 4;
        int networkButtonWidth = (this.width - x - 16) / 3;

        highlightButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("Highlight"),
                                        button ->
                                                highlightSelected()
                                )
                                .bounds(
                                        x,
                                        buttonY,
                                        networkButtonWidth,
                                        20
                                )
                                .build()
                );

        x += networkButtonWidth + 4;

        renameButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("Rename"),
                                        button ->
                                                renameSelected()
                                )
                                .bounds(
                                        x,
                                        buttonY,
                                        networkButtonWidth,
                                        20
                                )
                                .build()
                );

        x += networkButtonWidth + 4;

        decompileButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("Decompile"),
                                        button ->
                                                decompileSelected()
                                )
                                .bounds(
                                        x,
                                        buttonY,
                                        networkButtonWidth,
                                        20
                                )
                                .build()
                );

        int folderButtonY =
                this.height - 28;

        int available =
                layout.folderPanelWidth - 16;

        int folderButtonWidth =
                (available - 8) / 3;

        int fx = 8;

        newFolderButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("New"),
                                        button ->
                                                createFolder()
                                )
                                .bounds(
                                        fx,
                                        folderButtonY,
                                        folderButtonWidth,
                                        20
                                )
                                .build()
                );

        fx += folderButtonWidth + 4;

        renameFolderButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("Rename"),
                                        button ->
                                                renameFolder()
                                )
                                .bounds(
                                        fx,
                                        folderButtonY,
                                        folderButtonWidth,
                                        20
                                )
                                .build()
                );

        fx += folderButtonWidth + 4;

        deleteFolderButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("Delete"),
                                        button ->
                                                deleteFolder()
                                )
                                .bounds(
                                        fx,
                                        folderButtonY,
                                        folderButtonWidth,
                                        20
                                )
                                .build()
                );

        addRenderableWidget(
                Button.builder(
                                Component.literal("Close"),
                                button -> onClose()
                        )
                        .bounds(
                                this.width - 72,
                                8,
                                62,
                                20
                        )
                        .build()
        );

        rebuildSearchResults();
        updateButtons();
        clampScrolls();
        onBrokenListUpdated();
        updateWidgetVisibility();
        if (initialNavigateNetworkId != null) {
            int target = initialNavigateNetworkId;
            initialNavigateNetworkId = null;
            navigateToNetwork(target);
        }
    }

    // =========================================================
    // FOLDER TREE
    // =========================================================

    private void createFolder() {
        openEdit("Create Folder", "Folder name:", "", NetworkActionC2SPacket.Action.CREATE_FOLDER, state.selectedFolderId);
    }

    private void renameFolder() {
        if (state.selectedFolderIds.size() != 1) return;
        int id = state.selectedFolderIds.iterator().next(); var folder = model.folder(id);
        if (folder != null) openEdit("Rename Folder", "Folder name:", folder.getName(), NetworkActionC2SPacket.Action.RENAME_FOLDER, id);
    }

    private void deleteFolder() {
        sendBulk(NetworkBulkActionC2SPacket.BulkAction.DELETE_FOLDERS, state.selectedFolderIds, 0);
    }

    private void rebuildVisibleNetworks() { model.rebuildVisibleNetworks(); updateButtonsSafe(); clampScrolls(); }

    private int getVisibleFolderRowCount() { return layout.folders().count(); }

    private int getVisibleNetworkRowCount() { return layout.networks().count(); }

    private void clampScrolls() { layout.clamp(model, state); }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (state.mainViewMode == MainViewMode.BROKEN) {
            if (layout.brokenWheelContains(mouseX, mouseY)) {
                state.brokenScroll -= (int) Math.signum(delta);
                clampBrokenScroll();
                if (state.brokenPaintSelecting) {
                    state.brokenPaintX = mouseX;
                    state.brokenPaintY = mouseY;
                    if (Screen.hasShiftDown()) paintBrokenTo(mouseX, mouseY);
                    else stopBrokenPaint();
                }
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        if (isSearchMode()) {
            if (layout.search().contains(mouseX, mouseY)) {
                state.searchScroll += delta > 0 ? -1 : delta < 0 ? 1 : 0;
                clampSearchScroll();
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        if (mouseY >= LIST_TOP && mouseY < this.height - BOTTOM_MARGIN) {
            int step = delta > 0 ? -1 : delta < 0 ? 1 : 0;
            if (layout.folders().contains(mouseX, mouseY)) {
                state.folderScroll += step;
                clampScrolls();
                return true;
            }
            if (layout.networks().contains(mouseX, mouseY)) {
                state.networkScroll += step;
                clampScrolls();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // Only complete rendered rows are interactive; the bottom remainder is blank.
    private int getFolderIndexAt(double x, double y) { return layout.folders().index(x, y, state.folderScroll, model.visibleFolderRows.size()); }

    // =========================================================
    // MOUSE
    // =========================================================

    private void clearSelection() { state.clearSelection(); updateButtonsSafe(); }

    private void selectItem(SelectionType type, int id, boolean additive) { state.selectItem(type, id, additive); updateButtonsSafe(); }

    private void selectFolderItem(int id, boolean additive) {
        selectItem(SelectionType.FOLDERS, id, additive);
    }

    private void selectNetworkItem(int id, boolean additive) {
        selectItem(SelectionType.NETWORKS, id, additive);
    }

    private NetworkListS2CPacket.Entry getVisibleNetworkAt(double x, double y) {
        int index = layout.networks().index(x, y, state.networkScroll, model.visibleNetworks.size());
        return index < 0 ? null : model.visibleNetworks.get(index);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (state.mainViewMode == MainViewMode.BROKEN) {
            if (button == 0 && isInsideBrokenList(mouseX, mouseY)) {
                var entry = getBrokenEntryAt(mouseX, mouseY);
                stopBrokenPaint();
                if (entry != null) {
                    if (Screen.hasShiftDown()) toggleBrokenSelection(entry);
                    else selectOnlyBroken(entry);
                    if (Screen.hasShiftDown()) {
                        state.brokenPaintSelecting = true;
                        state.brokenPaintVisited.add(getBrokenKey(entry));
                        state.brokenPaintX = mouseX;
                        state.brokenPaintY = mouseY;
                    }
                } else if (!Screen.hasShiftDown()) clearBrokenSelection();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        clearDrag();
        boolean shift = Screen.hasShiftDown();
        if (isSearchMode()) {
            SearchResult result = getSearchResultAt(mouseX, mouseY);
            if (result != null) {
                if (shift) beginSearchPaintSelection(result, mouseX, mouseY);
                else activateSearchResult(result, false);
                return true;
            }
            // Hidden folder/network rows must never receive search-mode clicks.
            if (layout.search().contains(mouseX, mouseY)) {
                if (!shift) clearSelection();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int folderIndex = getFolderIndexAt(mouseX, mouseY);
        if (folderIndex >= 0) {
            VisibleFolderRow row = model.visibleFolderRows.get(folderIndex);
            FolderNode folder = row.node();
            int arrowX = 12 + row.depth() * 14;
            if (mouseX >= arrowX && mouseX <= arrowX + 12 && !folder.getChildren().isEmpty()) {
                model.toggleFolder(folder.getId());
                clampScrolls();
                return true;
            }
            state.selectedFolderId = folder.getId();
            state.networkScroll = 0;
            if (folder.getId() == 0) {
                clearSelection();
            } else {
                beginItemPress(SelectionType.FOLDERS, folder.getId(), shift, mouseX, mouseY);
            }
            rebuildVisibleNetworks();
            return true;
        }
        NetworkListS2CPacket.Entry network = getVisibleNetworkAt(mouseX, mouseY);
        if (network != null) {
            beginItemPress(SelectionType.NETWORKS, network.id(), shift, mouseX, mouseY);
            return true;
        }
        if (mouseY >= LIST_TOP && mouseY < height - BOTTOM_MARGIN
                && ((layout.folders().contains(mouseX, mouseY))
                || (layout.networks().contains(mouseX, mouseY)))) {
            if (!shift) clearSelection();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void beginItemPress(SelectionType type, int id, boolean shift, double x, double y) {
        state.dragStartX = x;
        state.dragStartY = y;
        if (shift) {
            selectItem(type, id, true);
            state.shiftPaintSelecting = true;
            state.paintSelectionType = type;
            state.paintStartId = id;
            state.paintLastX = x;
            state.paintLastY = y;
        } else {
            Set<Integer> selected = type == SelectionType.FOLDERS ? state.selectedFolderIds : state.selectedNetworkIds;
            // Preserve a group until release distinguishes an ordinary click from a move.
            if (!selected.contains(id)) selectItem(type, id, false);
            state.dragType = type == SelectionType.FOLDERS ? DragType.FOLDER : DragType.NETWORK;
            state.dragId = id;
        }
    }

    private void beginSearchPaintSelection(SearchResult result, double x, double y) {
        state.searchPaintSelecting = true;
        state.searchPaintSelectionType = result.type() == SearchResultType.NETWORK
                ? SelectionType.NETWORKS : SelectionType.FOLDERS;
        state.searchPaintVisitedIds.clear();
        activateSearchResult(result, true);
        state.searchPaintVisitedIds.add(result.id());
        state.searchPaintLastX = x;
        state.searchPaintLastY = y;
    }

    private void addSearchResultToPaintSelection(SearchResult result) {
        if (!state.searchPaintSelecting || result == null) return;
        SelectionType type = result.type() == SearchResultType.NETWORK
                ? SelectionType.NETWORKS : SelectionType.FOLDERS;
        // IDs are separate namespaces: ignore the other type before marking an ID visited.
        if (type != state.searchPaintSelectionType || (type == SelectionType.FOLDERS && result.id() == 0)
                || !state.searchPaintVisitedIds.add(result.id())) return;
        state.addPaint(type, result.id());
        updateButtonsSafe();
    }

    private void paintSearchTo(double x, double y, boolean shift) {
        if (shift && isSearchMode()) {
            // Sample the travelled segment so fast movement does not skip complete rows.
            int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x - state.searchPaintLastX),
                    Math.abs(y - state.searchPaintLastY)) / 8.0D));
            for (int i = 1; i <= steps; i++) {
                double fraction = (double) i / steps;
                addSearchResultToPaintSelection(getSearchResultAt(
                        state.searchPaintLastX + (x - state.searchPaintLastX) * fraction,
                        state.searchPaintLastY + (y - state.searchPaintLastY) * fraction));
            }
        }
        state.searchPaintLastX = x;
        state.searchPaintLastY = y;
    }

    private void clearDrag() { state.clearDrag(); }

    private void addToPaintSelection(int id) {
        if (state.paintSelectionType == SelectionType.NONE
                || (state.paintSelectionType == SelectionType.FOLDERS && id == 0)
                || !state.paintVisitedIds.add(id)) return;
        if (state.selectionType != state.paintSelectionType) clearSelection();
        state.selectionType = state.paintSelectionType;
        if (state.selectionType == SelectionType.FOLDERS) state.selectedFolderIds.add(id);
        else state.selectedNetworkIds.add(id);
        updateButtonsSafe();
    }

    private void paintAt(double x, double y) {
        if (state.paintSelectionType == SelectionType.FOLDERS) {
            FolderNode folder = getFolderAt(x, y);
            if (folder != null) addToPaintSelection(folder.getId());
        } else {
            NetworkListS2CPacket.Entry network = getVisibleNetworkAt(x, y);
            if (network != null) addToPaintSelection(network.id());
        }
    }

    private void paintTo(double x, double y) {
        double dx = x - state.dragStartX;
        double dy = y - state.dragStartY;
        if (!state.paintDragging && dx * dx + dy * dy <= 16.0D) return;
        state.paintDragging = true;
        // A Shift-click toggles; once it becomes a drag, every traversed row is added.
        addToPaintSelection(state.paintStartId);
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x - state.paintLastX),
                Math.abs(y - state.paintLastY)) / 8.0D));
        for (int i = 1; i <= steps; i++) {
            double fraction = (double) i / steps;
            paintAt(state.paintLastX + (x - state.paintLastX) * fraction, state.paintLastY + (y - state.paintLastY) * fraction);
        }
        state.paintLastX = x;
        state.paintLastY = y;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (state.mainViewMode == MainViewMode.BROKEN) {
            if (button == 0 && state.brokenPaintSelecting) {
                if (Screen.hasShiftDown()) paintBrokenTo(mouseX, mouseY);
                else stopBrokenPaint();
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (button == 0 && state.searchPaintSelecting) {
            paintSearchTo(mouseX, mouseY, Screen.hasShiftDown());
            return true;
        }
        if (button == 0 && state.shiftPaintSelecting) {
            if (Screen.hasShiftDown()) paintTo(mouseX, mouseY);
            else {
                state.paintLastX = mouseX;
                state.paintLastY = mouseY;
            }
            return true;
        }
        if (button == 0 && state.dragType != DragType.NONE) {
            if (Screen.hasShiftDown()) return true;
            double dx = mouseX - state.dragStartX;
            double dy = mouseY - state.dragStartY;
            if (dx * dx + dy * dy > 16.0D) state.dragging = true;
            if (state.dragging) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private FolderNode getFolderAt(double mouseX, double mouseY) {
        int index = getFolderIndexAt(mouseX, mouseY);
        return index < 0 ? null : model.visibleFolderRows.get(index).node();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) stopBrokenPaint();
        if (state.mainViewMode == MainViewMode.BROKEN) return super.mouseReleased(mouseX, mouseY, button);
        if (button == 0 && (state.searchPaintSelecting || state.shiftPaintSelecting)) {
            clearDrag();
            return true;
        }
        if (button == 0 && state.dragType != DragType.NONE) {
            if (state.dragging) {
                FolderNode target = getFolderAt(mouseX, mouseY);
                if (target != null && !Screen.hasShiftDown()) {
                    sendBulk(state.dragType == DragType.NETWORK
                                    ? NetworkBulkActionC2SPacket.BulkAction.MOVE_NETWORKS
                                    : NetworkBulkActionC2SPacket.BulkAction.MOVE_FOLDERS,
                            state.dragType == DragType.NETWORK ? state.selectedNetworkIds : state.selectedFolderIds,
                            target.getId());
                }
            } else {
                selectItem(state.dragType == DragType.NETWORK ? SelectionType.NETWORKS : SelectionType.FOLDERS,
                        state.dragId, false);
            }
            clearDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void activateSearchResult(SearchResult result, boolean shift) {
        if (shift) {
            if (result.type() == SearchResultType.NETWORK) selectNetworkItem(result.id(), true);
            else selectFolderItem(result.id(), true);
        } else if (result.type() == SearchResultType.NETWORK) {
            navigateToNetwork(result.id());
        } else {
            navigateToFolder(result.id());
        }
    }

    private void expandPathToFolder(int id) { model.expandPathToFolder(id); clampScrolls(); }

    private int findVisibleFolderRowIndex(int folderId) {
        for (int i = 0; i < model.visibleFolderRows.size(); i++) {
            if (model.visibleFolderRows.get(i).node().getId() == folderId) return i;
        }
        return -1;
    }

    private void scrollFolderIntoView(int folderId) {
        int index = findVisibleFolderRowIndex(folderId);
        if (index < 0) return;
        int count = getVisibleFolderRowCount();
        if (index < state.folderScroll) state.folderScroll = index;
        else if (index >= state.folderScroll + count) state.folderScroll = index - count + 1;
        clampScrolls();
    }

    private int findVisibleNetworkIndex(int networkId) {
        for (int i = 0; i < model.visibleNetworks.size(); i++) {
            if (model.visibleNetworks.get(i).id() == networkId) return i;
        }
        return -1;
    }

    private void scrollNetworkIntoView(int networkId) {
        int index = findVisibleNetworkIndex(networkId);
        if (index < 0) return;
        int count = getVisibleNetworkRowCount();
        if (index < state.networkScroll) state.networkScroll = index;
        else if (index >= state.networkScroll + count) state.networkScroll = index - count + 1;
        clampScrolls();
    }

    private void exitSearchMode() {
        clearDrag();
        if (searchBox != null) {
            searchBox.setValue("");
            searchBox.setFocused(false);
            if (getFocused() == searchBox) setFocused(null);
        }
        model.rebuildSearchResults();
        state.searchScroll = 0;
    }

    private void navigateToFolder(int folderId) {
        if (folderId == 0 || findFolderNode(folderId) == null) return;
        expandPathToFolder(folderId);
        state.selectedFolderId = folderId;
        state.networkScroll = 0;
        rebuildVisibleNetworks();
        selectFolderItem(folderId, false);
        exitSearchMode();
        scrollFolderIntoView(folderId);
        updateButtonsSafe();
    }

    private void navigateToNetwork(int networkId) {
        NetworkListS2CPacket.Entry target = model.network(networkId);
        if (target == null || findFolderNode(target.folderId()) == null) return;
        // Expand root as well, including when navigating to a root network.
        expandPathToFolder(target.folderId());
        state.selectedFolderId = target.folderId();
        state.networkScroll = 0;
        rebuildVisibleNetworks();
        selectNetworkItem(networkId, false);
        exitSearchMode();
        scrollFolderIntoView(target.folderId());
        scrollNetworkIntoView(networkId);
        updateButtonsSafe();
    }

    private FolderNode findFolderNode(int id) { return model.folder(id); }

    // =========================================================
    // ACTION BUTTONS
    // =========================================================

    private void updateButtonsSafe() {

        if (highlightButton != null) {
            updateButtons();
        }
    }

    private void updateButtons() {
        boolean networks = state.selectionType == SelectionType.NETWORKS && !state.selectedNetworkIds.isEmpty();
        boolean foldersSelected = state.selectionType == SelectionType.FOLDERS && !state.selectedFolderIds.isEmpty();
        newFolderButton.active = !isSearchMode();
        highlightButton.active = networks;
        renameButton.active = networks && state.selectedNetworkIds.size() == 1;
        decompileButton.active = networks;
        renameFolderButton.active = foldersSelected && state.selectedFolderIds.size() == 1;
        deleteFolderButton.active = foldersSelected;
    }

    private void highlightSelected() { actions.highlight(state.selectedNetworkIds); }

    private void renameSelected() {
        if (state.selectedNetworkIds.size() != 1) return;
        int id = state.selectedNetworkIds.iterator().next(); var network = model.network(id);
        if (network != null) openEdit("Rename Network", "Network name:", network.name(), NetworkActionC2SPacket.Action.RENAME_NETWORK, id);
    }
    private void openEdit(String title, String label, String initial, NetworkActionC2SPacket.Action action, int id) {
        var edit = actions.edit(action, id);
        openingChild = true;
        Minecraft.getInstance().setScreen(new NetworkTextEditScreen(this, title, label, initial,
                value -> {
                    if (!edit.save(value) && Minecraft.getInstance().player != null)
                        Minecraft.getInstance().player.displayClientMessage(Component.literal("Action unavailable: target changed, request pending, or session closed."), false);
                }, com.example.compiledcircuits.network.OperationLimits.NAME, edit::cancel));
    }

    private void decompileSelected() {
        sendBulk(NetworkBulkActionC2SPacket.BulkAction.DECOMPILE_NETWORKS, state.selectedNetworkIds, 0);
    }

    private void sendBulk(NetworkBulkActionC2SPacket.BulkAction action, Collection<Integer> ids, int targetId) { actions.bulk(action, ids, targetId); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        renderer.render(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override
    public void tick() {
        if (!contextValid()) { actions.close(); Minecraft.getInstance().setScreen(null); return; }
        sessionTick();
        if (searchBox != null) state.searchFocused = searchBox.isFocused();
        super.tick();
    }

    // =========================================================
    // RENDER
    // =========================================================

    private String getMainViewButtonText() {
        return state.mainViewMode == MainViewMode.NETWORKS ? "Networks" : "Broken";
    }

    private void toggleMainView() {
        exitSearchMode();
        setFocused(null);
        state.mainViewMode = state.mainViewMode == MainViewMode.NETWORKS ? MainViewMode.BROKEN : MainViewMode.NETWORKS;
        state.brokenScroll = 0;
        clearBrokenSelection();
        mainViewButton.setMessage(Component.literal(getMainViewButtonText()));
        updateWidgetVisibility();
    }

    private void updateWidgetVisibility() {
        boolean networkMode = state.mainViewMode == MainViewMode.NETWORKS;
        searchBox.visible = networkMode;
        searchBox.active = networkMode;
        for (Button button : List.of(searchFilterButton, highlightButton, renameButton, decompileButton,
                newFolderButton, renameFolderButton, deleteFolderButton)) {
            button.visible = networkMode;
            button.active = networkMode;
        }
        if (networkMode) updateButtons();
        updateBrokenActionButtons();
    }

    public void onBrokenListUpdated() {
        model.updateBroken(ClientBrokenElementList.getEntries());
        if (state.restoreBrokenFocus) {
            state.restoreBrokenFocus = false;
            for (var entry : model.brokenEntries) {
                if (ClientBrokenElements.getFocused().contains(
                        new ClientBrokenElements.FocusedBrokenPos(entry.dimension(), entry.pos()))) {
                    state.selectedBrokenKeys.add(getBrokenKey(entry));
                }
            }
        }
        clampBrokenScroll();
        syncBrokenFocusToRenderer();
    }

    public void onDamageUnconfirmed() {
        for (Button button : new Button[]{brokenSelectAllButton, brokenOpenNetworkButton, brokenDecompileButton, brokenRepairButton})
            if (button != null) button.active = false;
        stopBrokenPaint();
    }

    private BrokenKey getBrokenKey(com.example.compiledcircuits.networking.BrokenElementListS2CPacket.Entry entry) { return ManagerModel.key(entry); }

    private boolean isBrokenSelected(com.example.compiledcircuits.networking.BrokenElementListS2CPacket.Entry entry) {
        return state.selectedBrokenKeys.contains(getBrokenKey(entry));
    }

    private void selectOnlyBroken(com.example.compiledcircuits.networking.BrokenElementListS2CPacket.Entry entry) {
        state.selectAllArmedNetworkId = null;
        state.selectedBrokenKeys.clear();
        state.selectedBrokenKeys.add(getBrokenKey(entry));
        syncBrokenFocusToRenderer();
    }

    private void toggleBrokenSelection(com.example.compiledcircuits.networking.BrokenElementListS2CPacket.Entry entry) {
        state.selectAllArmedNetworkId = null;
        BrokenKey key = getBrokenKey(entry);
        if (!state.selectedBrokenKeys.add(key)) state.selectedBrokenKeys.remove(key);
        syncBrokenFocusToRenderer();
    }

    private Set<Integer> getSelectedBrokenNetworkIds() { return model.selectedBrokenNetworks(); }

    private void selectAllBrokenSmart() {
        if (!ClientDamageSync.ready()) return;
        model.selectAllBroken(); syncBrokenFocusToRenderer();
    }

    private void openSelectedBrokenNetwork() {
        if (!ClientDamageSync.ready()) return;
        Set<Integer> networks = getSelectedBrokenNetworkIds();
        if (networks.size() != 1) return;
        stopBrokenPaint();
        state.selectAllArmedNetworkId = null;
        state.mainViewMode = MainViewMode.NETWORKS;
        mainViewButton.setMessage(Component.literal(getMainViewButtonText()));
        updateWidgetVisibility();
        navigateToNetwork(networks.iterator().next());
    }

    private void repairSelectedBrokenNetworks() {
        if (!ClientDamageSync.ready()) return;
        Set<Integer> networks = getSelectedBrokenNetworkIds();
        if (networks.isEmpty()) return;
        sendBulk(NetworkBulkActionC2SPacket.BulkAction.REPAIR_NETWORKS, networks, 0);
    }

    private void decompileSelectedBrokenNetworks() {
        if (!ClientDamageSync.ready()) return;
        Set<Integer> networks = getSelectedBrokenNetworkIds();
        if (networks.isEmpty()) return;
        sendBulk(NetworkBulkActionC2SPacket.BulkAction.DECOMPILE_NETWORKS, networks, 0);
    }

    private void updateBrokenActionButtons() {
        if (brokenSelectAllButton == null || brokenOpenNetworkButton == null || brokenDecompileButton == null || brokenRepairButton == null) return;
        boolean visible = state.mainViewMode == MainViewMode.BROKEN;
        brokenSelectAllButton.visible = visible;
        brokenOpenNetworkButton.visible = visible;
        brokenDecompileButton.visible = visible;
        brokenRepairButton.visible = visible;
        int count = getSelectedBrokenNetworkIds().size();
        boolean ready = visible && ClientDamageSync.ready();
        brokenSelectAllButton.active = ready && !model.brokenEntries.isEmpty();
        brokenOpenNetworkButton.active = ready && count == 1;
        brokenDecompileButton.active = ready && count > 0;
        brokenRepairButton.active = ready && count > 0;
        brokenRepairButton.setMessage(Component.literal(count <= 1 ? "Repair Network" : "Repair Networks (" + count + ")"));
        brokenDecompileButton.setMessage(Component.literal(count <= 1 ? "Decompile Network" : "Decompile Networks (" + count + ")"));
    }

    private void stopBrokenPaint() { state.stopBrokenPaint(); }

    private void clearBrokenSelection() {
        state.selectAllArmedNetworkId = null;
        state.selectedBrokenKeys.clear();
        stopBrokenPaint();
        ClientBrokenElements.clearFocused();
        updateBrokenActionButtons();
    }

    private void syncBrokenFocusToRenderer() {
        Set<ClientBrokenElements.FocusedBrokenPos> focused = new LinkedHashSet<>();
        for (var entry : model.brokenEntries) {
            if (isBrokenSelected(entry)) focused.add(new ClientBrokenElements.FocusedBrokenPos(entry.dimension(), entry.pos()));
        }
        ClientBrokenElements.setFocused(focused);
        updateBrokenActionButtons();
    }

    private void paintBrokenTo(double mouseX, double mouseY) {
        state.selectAllArmedNetworkId = null;
        // Sample the movement so fast drags do not skip rows between mouse events.
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(mouseX - state.brokenPaintX), Math.abs(mouseY - state.brokenPaintY)) / 4));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            var entry = getBrokenEntryAt(state.brokenPaintX + (mouseX - state.brokenPaintX) * t,
                    state.brokenPaintY + (mouseY - state.brokenPaintY) * t);
            if (entry != null && state.brokenPaintVisited.add(getBrokenKey(entry))) state.selectedBrokenKeys.add(getBrokenKey(entry));
        }
        state.brokenPaintX = mouseX;
        state.brokenPaintY = mouseY;
        syncBrokenFocusToRenderer();
    }

    private boolean isInsideBrokenList(double x, double y) { return layout.broken().contains(x, y); }

    private com.example.compiledcircuits.networking.BrokenElementListS2CPacket.Entry getBrokenEntryAt(double x, double y) {
        int index = ClientDamageSync.ready() ? layout.broken().index(x, y, state.brokenScroll, model.brokenEntries.size()) : -1;
        return index < 0 ? null : model.brokenEntries.get(index);
    }

    private int getVisibleBrokenRowCount() { return layout.broken().count(); }

    private void clampBrokenScroll() {
        state.brokenScroll = Math.max(0, Math.min(state.brokenScroll,
                Math.max(0, model.brokenEntries.size() - getVisibleBrokenRowCount())));
    }

    private boolean isSearchMode() { return model.isSearchMode(); }

    private String getSearchFilterLabel() {
        return switch (state.searchFilter) {
            case BOTH -> "Both";
            case NETWORKS -> "Networks";
            case FOLDERS -> "Folders";
        };
    }

    private void cycleSearchFilter() {
        state.searchFilter = switch (state.searchFilter) {
            case BOTH -> SearchFilter.NETWORKS;
            case NETWORKS -> SearchFilter.FOLDERS;
            case FOLDERS -> SearchFilter.BOTH;
        };
        if (searchFilterButton != null) {
            searchFilterButton.setMessage(Component.literal(getSearchFilterLabel()));
        }
        state.searchScroll = 0;
        clearDrag();
        clearSelection();
        rebuildSearchResults();
    }

    private void rebuildSearchResults() { model.rebuildSearchResults(); clampSearchScroll(); }

    private int getVisibleSearchRowCount() { return layout.search().count(); }

    private int getMaxSearchScroll() {
        return Math.max(0, model.searchResults.size() - getVisibleSearchRowCount());
    }

    private void clampSearchScroll() {
        state.searchScroll = Math.max(0, Math.min(state.searchScroll, getMaxSearchScroll()));
    }

    private SearchResult getSearchResultAt(double x, double y) {
        int index = isSearchMode() ? layout.search().index(x, y, state.searchScroll, model.searchResults.size()) : -1;
        return index < 0 ? null : model.searchResults.get(index);
    }

    // =========================================================
    // ETC
    // =========================================================

    @Override
    public void onClose() {
        actions.close();
        state.selectAllArmedNetworkId = null;
        ClientBrokenElements.onGuiClosed();
        super.onClose();
    }

    @Override
    public void removed() {
        stopBrokenPaint();
        clearDrag();
        if (searchBox != null) state.searchFocused = searchBox.isFocused();
        if (!openingChild) actions.close();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
