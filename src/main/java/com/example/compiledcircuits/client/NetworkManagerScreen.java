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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NetworkManagerScreen
        extends Screen {

    // -------------------------
    // Layout
    // -------------------------

    private static final int TOP = 55;
    private static final int LIST_TOP = 72;
    private static final int SEARCH_WIDTH = 190;
    private static final int SEARCH_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 40;

    private static final int FOLDER_ROW_HEIGHT = 18;
    private static final int NETWORK_ROW_HEIGHT = 31;

    // Ліва панель займає приблизно 38%.
    private int folderPanelWidth;

    // -------------------------
    // Data
    // -------------------------

    private final List<NetworkListS2CPacket.Entry> entries;

    private final List<NetworkListS2CPacket.FolderEntry> folders;

    private FolderNode rootFolder;


    private final Set<Integer> selectedFolderIds = new LinkedHashSet<>();
    private final Set<Integer> selectedNetworkIds = new LinkedHashSet<>();
    private enum SelectionType { NONE, FOLDERS, NETWORKS }
    private SelectionType selectionType = SelectionType.NONE;

    /*
     * Це вже "розгорнутий" список дерева,
     * який реально зараз видно.
     */
    private final List<VisibleFolderRow> visibleFolderRows =
            new ArrayList<>();

    /*
     * Мережі правої панелі.
     */
    private final List<NetworkListS2CPacket.Entry> visibleNetworks =
            new ArrayList<>();

    // -------------------------
    // Buttons
    // -------------------------

    private Button highlightButton;
    private Button renameButton;
    private Button decompileButton;

    private Button newFolderButton;
    private Button renameFolderButton;
    private Button deleteFolderButton;
    private EditBox searchBox;

    private int selectedFolderId = 0;
    private int folderScroll = 0;
    private int networkScroll = 0;

    public NetworkManagerScreen(
            List<NetworkListS2CPacket.Entry> entries,
            List<NetworkListS2CPacket.FolderEntry> folders
    ) {

        super(
                Component.literal(
                        "Network Manager"
                )
        );

        this.entries =
                new ArrayList<>(entries);

        this.folders =
                new ArrayList<>(folders);

        buildFolderTree();
        rebuildVisibleNetworks();
    }

    public void updateData(List<NetworkListS2CPacket.Entry> entries,
                           List<NetworkListS2CPacket.FolderEntry> folders) {
        java.util.Set<Integer> collapsed = new java.util.HashSet<>();
        rememberCollapsed(rootFolder, collapsed);
        this.entries.clear();
        this.entries.addAll(entries);
        this.folders.clear();
        this.folders.addAll(folders);
        buildFolderTree();
        for (int id : collapsed) {
            FolderNode node = findFolderNode(id);
            if (node != null) node.toggleExpanded();
        }
        if (findFolderNode(selectedFolderId) == null) {
            selectedFolderId = 0;
            networkScroll = 0;
        }
        clearSelection();
        clearDrag();
        rebuildVisibleFolderRows();
        rebuildVisibleNetworks();
    }

    private void rememberCollapsed(FolderNode node, java.util.Set<Integer> collapsed) {
        if (!node.isExpanded()) collapsed.add(node.getId());
        for (FolderNode child : node.getChildren()) rememberCollapsed(child, collapsed);
    }

    // =========================================================
    // INIT
    // =========================================================

    @Override
    protected void init() {

        folderPanelWidth =
                Math.max(
                        140,
                        (int) (this.width * 0.38F)
                );

        String searchText = searchBox == null ? "" : searchBox.getValue();
        int searchX = (this.width - SEARCH_WIDTH) / 2;
        searchBox = new EditBox(this.font, searchX, 30, SEARCH_WIDTH, SEARCH_HEIGHT,
                Component.literal("Search"));
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchText);
        addRenderableWidget(searchBox);

        int buttonY = this.height - 28;

        int x = folderPanelWidth + 4;
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
                folderPanelWidth - 16;

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

        updateButtons();
        clampScrolls();
    }

    // =========================================================
    // FOLDER TREE
    // =========================================================

    private void createFolder() {

        Minecraft.getInstance()
                .setScreen(
                        new NetworkTextEditScreen(
                                this,
                                "Create Folder",
                                "Folder name:",
                                "",
                                value ->
                                        ModNetworking.CHANNEL.sendToServer(
                                                new NetworkActionC2SPacket(
                                                        NetworkActionC2SPacket.Action.CREATE_FOLDER,
                                                        selectedFolderId,
                                                        value
                                                )
                                        )
                        )
                );
    }

    private void renameFolder() {
        if (selectedFolderIds.size() != 1) return;
        int id = selectedFolderIds.iterator().next();
        FolderNode folder = findFolderNode(id);
        if (folder == null) return;
        Minecraft.getInstance().setScreen(new NetworkTextEditScreen(this,
                "Rename Folder", "Folder name:", folder.getName(),
                value -> ModNetworking.CHANNEL.sendToServer(new NetworkActionC2SPacket(
                        NetworkActionC2SPacket.Action.RENAME_FOLDER, id, value))));
    }

    private void deleteFolder() {
        sendBulk(NetworkBulkActionC2SPacket.BulkAction.DELETE_FOLDERS, selectedFolderIds, 0);
    }

    private void buildFolderTree() {

        rootFolder =
                new FolderNode(
                        0,
                        "/",
                        -1
                );

        java.util.Map<Integer, FolderNode> map =
                new java.util.HashMap<>();

        map.put(
                0,
                rootFolder
        );

        /*
         * Спочатку створюємо nodes.
         */
        for (NetworkListS2CPacket.FolderEntry folder
                : folders) {

            map.put(
                    folder.id(),
                    new FolderNode(
                            folder.id(),
                            folder.name(),
                            folder.parentId()
                    )
            );
        }

        /*
         * Потім зв'язуємо parent → child.
         */
        for (NetworkListS2CPacket.FolderEntry folder
                : folders) {

            FolderNode node =
                    map.get(
                            folder.id()
                    );

            FolderNode parent =
                    map.get(
                            folder.parentId()
                    );

            if (parent == null) {
                parent = rootFolder;
            }

            parent.addChild(node);
        }

        rebuildVisibleFolderRows();
    }

    private void rebuildVisibleFolderRows() {

        visibleFolderRows.clear();

        /*
         * Root завжди показуємо.
         */
        addVisibleFolder(
                rootFolder,
                0
        );
        if (this.height > 0) clampScrolls();
    }

    private void addVisibleFolder(
            FolderNode node,
            int depth
    ) {

        visibleFolderRows.add(
                new VisibleFolderRow(
                        node,
                        depth
                )
        );

        if (!node.isExpanded()) {
            return;
        }

        for (FolderNode child
                : node.getChildren()) {

            addVisibleFolder(
                    child,
                    depth + 1
            );
        }
    }

    private void rebuildVisibleNetworks() {

        visibleNetworks.clear();

        for (NetworkListS2CPacket.Entry entry : entries) {

            if (entry.folderId()
                    == selectedFolderId) {

                visibleNetworks.add(
                        entry
                );
            }

        }

        visibleNetworks.sort(
                Comparator.comparingInt(
                        NetworkListS2CPacket.Entry::id
                )
        );

        updateButtonsSafe();
        if (this.height > 0) clampScrolls();
    }

    private int getVisibleFolderRowCount() {
        return Math.max(1, (this.height - BOTTOM_MARGIN - LIST_TOP) / FOLDER_ROW_HEIGHT);
    }

    private int getVisibleNetworkRowCount() {
        return Math.max(1, (this.height - BOTTOM_MARGIN - LIST_TOP) / NETWORK_ROW_HEIGHT);
    }

    private int getMaxFolderScroll() {
        return Math.max(0, visibleFolderRows.size() - getVisibleFolderRowCount());
    }

    private int getMaxNetworkScroll() {
        return Math.max(0, visibleNetworks.size() - getVisibleNetworkRowCount());
    }

    private void clampScrolls() {
        folderScroll = Math.max(0, Math.min(folderScroll, getMaxFolderScroll()));
        networkScroll = Math.max(0, Math.min(networkScroll, getMaxNetworkScroll()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= LIST_TOP && mouseY < this.height - BOTTOM_MARGIN) {
            int step = delta > 0 ? -1 : delta < 0 ? 1 : 0;
            if (mouseX >= 7 && mouseX < folderPanelWidth - 2) {
                folderScroll += step;
                clampScrolls();
                return true;
            }
            if (mouseX >= folderPanelWidth + 8 && mouseX <= this.width - 8) {
                networkScroll += step;
                clampScrolls();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // Only complete rendered rows are interactive; the bottom remainder is blank.
    private int getFolderIndexAt(double mouseX, double mouseY) {
        if (mouseX < 7 || mouseX >= folderPanelWidth - 2 || mouseY < LIST_TOP) return -1;
        int row = (int) ((mouseY - LIST_TOP) / FOLDER_ROW_HEIGHT);
        if (row >= getVisibleFolderRowCount()
                || LIST_TOP + (row + 1) * FOLDER_ROW_HEIGHT > height - BOTTOM_MARGIN) return -1;
        int index = folderScroll + row;
        return index < visibleFolderRows.size() ? index : -1;
    }

    // =========================================================
    // MOUSE
    // =========================================================

    private void clearSelection() {
        selectedFolderIds.clear();
        selectedNetworkIds.clear();
        selectionType = SelectionType.NONE;
        updateButtonsSafe();
    }

    private void selectItem(SelectionType type, int id, boolean additive) {
        if (type == SelectionType.FOLDERS && id == 0) return;
        if (selectionType != type || !additive) clearSelection();
        Set<Integer> selected = type == SelectionType.FOLDERS ? selectedFolderIds : selectedNetworkIds;
        if (!additive || !selected.remove(id)) selected.add(id);
        selectionType = selected.isEmpty() ? SelectionType.NONE : type;
        updateButtonsSafe();
    }

    private void selectFolderItem(int id, boolean additive) {
        selectItem(SelectionType.FOLDERS, id, additive);
    }

    private void selectNetworkItem(int id, boolean additive) {
        selectItem(SelectionType.NETWORKS, id, additive);
    }

    private NetworkListS2CPacket.Entry getVisibleNetworkAt(double mouseX, double mouseY) {
        if (mouseX < folderPanelWidth + 8 || mouseX > width - 8 || mouseY < LIST_TOP) return null;
        int row = (int) ((mouseY - LIST_TOP) / NETWORK_ROW_HEIGHT);
        int index = networkScroll + row;
        if (row >= getVisibleNetworkRowCount()
                || LIST_TOP + (row + 1) * NETWORK_ROW_HEIGHT > height - BOTTOM_MARGIN
                || index >= visibleNetworks.size()) return null;
        return visibleNetworks.get(index);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        clearDrag();
        boolean shift = Screen.hasShiftDown();
        int folderIndex = getFolderIndexAt(mouseX, mouseY);
        if (folderIndex >= 0) {
            VisibleFolderRow row = visibleFolderRows.get(folderIndex);
            FolderNode folder = row.node();
            int arrowX = 12 + row.depth() * 14;
            if (mouseX >= arrowX && mouseX <= arrowX + 12 && !folder.getChildren().isEmpty()) {
                folder.toggleExpanded();
                rebuildVisibleFolderRows();
                return true;
            }
            selectedFolderId = folder.getId();
            networkScroll = 0;
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
                && ((mouseX >= 7 && mouseX < folderPanelWidth - 2)
                || (mouseX >= folderPanelWidth + 8 && mouseX <= width - 8))) {
            if (!shift) clearSelection();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private enum DragType { NONE, NETWORK, FOLDER }
    private DragType dragType = DragType.NONE;
    private int dragId = -1;
    private double dragStartX;
    private double dragStartY;
    private boolean dragging;
    private boolean shiftPaintSelecting;
    private SelectionType paintSelectionType = SelectionType.NONE;
    private final Set<Integer> paintVisitedIds = new HashSet<>();
    private int paintStartId = -1;
    private double paintLastX;
    private double paintLastY;
    private boolean paintDragging;

    private void beginItemPress(SelectionType type, int id, boolean shift, double x, double y) {
        dragStartX = x;
        dragStartY = y;
        if (shift) {
            selectItem(type, id, true);
            shiftPaintSelecting = true;
            paintSelectionType = type;
            paintStartId = id;
            paintLastX = x;
            paintLastY = y;
        } else {
            Set<Integer> selected = type == SelectionType.FOLDERS ? selectedFolderIds : selectedNetworkIds;
            // Preserve a group until release distinguishes an ordinary click from a move.
            if (!selected.contains(id)) selectItem(type, id, false);
            dragType = type == SelectionType.FOLDERS ? DragType.FOLDER : DragType.NETWORK;
            dragId = id;
        }
    }

    private void clearDrag() {
        dragType = DragType.NONE;
        dragId = -1;
        dragging = false;
        shiftPaintSelecting = false;
        paintSelectionType = SelectionType.NONE;
        paintVisitedIds.clear();
        paintStartId = -1;
        paintDragging = false;
    }

    private void addToPaintSelection(int id) {
        if (paintSelectionType == SelectionType.NONE
                || (paintSelectionType == SelectionType.FOLDERS && id == 0)
                || !paintVisitedIds.add(id)) return;
        if (selectionType != paintSelectionType) clearSelection();
        selectionType = paintSelectionType;
        if (selectionType == SelectionType.FOLDERS) selectedFolderIds.add(id);
        else selectedNetworkIds.add(id);
        updateButtonsSafe();
    }

    private void paintAt(double x, double y) {
        if (paintSelectionType == SelectionType.FOLDERS) {
            FolderNode folder = getFolderAt(x, y);
            if (folder != null) addToPaintSelection(folder.getId());
        } else {
            NetworkListS2CPacket.Entry network = getVisibleNetworkAt(x, y);
            if (network != null) addToPaintSelection(network.id());
        }
    }

    private void paintTo(double x, double y) {
        double dx = x - dragStartX;
        double dy = y - dragStartY;
        if (!paintDragging && dx * dx + dy * dy <= 16.0D) return;
        paintDragging = true;
        // A Shift-click toggles; once it becomes a drag, every traversed row is added.
        addToPaintSelection(paintStartId);
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x - paintLastX),
                Math.abs(y - paintLastY)) / 8.0D));
        for (int i = 1; i <= steps; i++) {
            double fraction = (double) i / steps;
            paintAt(paintLastX + (x - paintLastX) * fraction, paintLastY + (y - paintLastY) * fraction);
        }
        paintLastX = x;
        paintLastY = y;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && shiftPaintSelecting) {
            if (Screen.hasShiftDown()) paintTo(mouseX, mouseY);
            else {
                paintLastX = mouseX;
                paintLastY = mouseY;
            }
            return true;
        }
        if (button == 0 && dragType != DragType.NONE) {
            if (Screen.hasShiftDown()) return true;
            double dx = mouseX - dragStartX;
            double dy = mouseY - dragStartY;
            if (dx * dx + dy * dy > 16.0D) dragging = true;
            if (dragging) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private FolderNode getFolderAt(double mouseX, double mouseY) {
        int index = getFolderIndexAt(mouseX, mouseY);
        return index < 0 ? null : visibleFolderRows.get(index).node();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && shiftPaintSelecting) {
            clearDrag();
            return true;
        }
        if (button == 0 && dragType != DragType.NONE) {
            if (dragging) {
                FolderNode target = getFolderAt(mouseX, mouseY);
                if (target != null && !Screen.hasShiftDown()) {
                    sendBulk(dragType == DragType.NETWORK
                                    ? NetworkBulkActionC2SPacket.BulkAction.MOVE_NETWORKS
                                    : NetworkBulkActionC2SPacket.BulkAction.MOVE_FOLDERS,
                            dragType == DragType.NETWORK ? selectedNetworkIds : selectedFolderIds,
                            target.getId());
                }
            } else {
                selectItem(dragType == DragType.NETWORK ? SelectionType.NETWORKS : SelectionType.FOLDERS,
                        dragId, false);
            }
            clearDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private FolderNode findFolderNode(int id) {
        return findFolderRecursive(rootFolder, id);
    }

    private FolderNode findFolderRecursive(FolderNode node, int id) {
        if (node.getId() == id) return node;
        for (FolderNode child : node.getChildren()) {
            FolderNode found = findFolderRecursive(child, id);
            if (found != null) return found;
        }
        return null;
    }

    // =========================================================
    // ACTION BUTTONS
    // =========================================================

    private void updateButtonsSafe() {

        if (highlightButton != null) {
            updateButtons();
        }
    }

    private void updateButtons() {
        boolean networks = selectionType == SelectionType.NETWORKS && !selectedNetworkIds.isEmpty();
        boolean folders = selectionType == SelectionType.FOLDERS && !selectedFolderIds.isEmpty();
        newFolderButton.active = true;
        highlightButton.active = networks;
        renameButton.active = networks && selectedNetworkIds.size() == 1;
        decompileButton.active = networks;
        renameFolderButton.active = folders && selectedFolderIds.size() == 1;
        deleteFolderButton.active = folders;
    }

    private void highlightSelected() {
        sendBulk(NetworkBulkActionC2SPacket.BulkAction.HIGHLIGHT_NETWORKS, selectedNetworkIds, 0);
    }

    private void renameSelected() {
        if (selectedNetworkIds.size() != 1) return;
        int id = selectedNetworkIds.iterator().next();
        NetworkListS2CPacket.Entry network = entries.stream().filter(entry -> entry.id() == id)
                .findFirst().orElse(null);
        if (network == null) return;
        Minecraft.getInstance().setScreen(new NetworkTextEditScreen(this,
                "Rename Network", "Network name:", network.name(),
                value -> ModNetworking.CHANNEL.sendToServer(new NetworkActionC2SPacket(
                        NetworkActionC2SPacket.Action.RENAME_NETWORK, id, value))));
    }

    private void decompileSelected() {
        sendBulk(NetworkBulkActionC2SPacket.BulkAction.DECOMPILE_NETWORKS, selectedNetworkIds, 0);
    }

    private void sendBulk(NetworkBulkActionC2SPacket.BulkAction action, Collection<Integer> ids, int targetId) {
        if (!ids.isEmpty()) {
            ModNetworking.CHANNEL.sendToServer(new NetworkBulkActionC2SPacket(action, ids, targetId));
        }
    }

    // =========================================================
    // RENDER
    // =========================================================

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        renderBackground(graphics);

        /*
         * Main window background.
         */
        graphics.fill(
                5,
                TOP,
                this.width - 5,
                this.height - 35,
                0xAA111111
        );

        /*
         * Left folder background.
         */
        graphics.fill(
                7,
                TOP + 2,
                folderPanelWidth - 2,
                this.height - 37,
                0xAA181818
        );

        /*
         * Separator.
         */
        graphics.fill(
                folderPanelWidth,
                TOP + 2,
                folderPanelWidth + 1,
                this.height - 37,
                0xFF555555
        );

        graphics.drawCenteredString(
                this.font,
                "Compiled Circuits - Network Manager",
                this.width / 2,
                12,
                0xFFFFFF
        );

        graphics.drawString(
                this.font,
                "Folders (" + selectedFolderIds.size() + " selected)",
                12,
                TOP + 2,
                0xAAAAAA
        );

        graphics.drawString(
                this.font,
                "Networks (" + selectedNetworkIds.size() + " selected)",
                folderPanelWidth + 10,
                TOP + 2,
                0xAAAAAA
        );

        renderFolderTree(
                graphics,
                mouseX,
                mouseY
        );

        renderNetworks(
                graphics,
                mouseX,
                mouseY
        );

        if (dragging) {
            graphics.drawString(font, dragType == DragType.NETWORK
                    ? "Move " + selectedNetworkIds.size() + " networks"
                    : "Move " + selectedFolderIds.size() + " folders",
                    mouseX + 10, mouseY + 10, 0xFFFFAA);
        }

        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    private void renderFolderTree(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {

        int rowY = LIST_TOP;

        int endIndex = Math.min(visibleFolderRows.size(), folderScroll + getVisibleFolderRowCount());
        for (int i = folderScroll; i < endIndex; i++) {
            VisibleFolderRow row = visibleFolderRows.get(i);

            if (rowY + FOLDER_ROW_HEIGHT
                    > this.height - BOTTOM_MARGIN) {
                break;
            }

            FolderNode folder =
                    row.node();

            boolean selected =
                    selectedFolderIds.contains(folder.getId());

            boolean hovered =
                    mouseX >= 7
                            && mouseX < folderPanelWidth - 2
                            && mouseY >= rowY
                            && mouseY < rowY + FOLDER_ROW_HEIGHT;

            if (selected) {

                graphics.fill(
                        8,
                        rowY,
                        folderPanelWidth - 3,
                        rowY + FOLDER_ROW_HEIGHT,
                        0x884477AA
                );

            } else if (hovered) {

                graphics.fill(
                        8,
                        rowY,
                        folderPanelWidth - 3,
                        rowY + FOLDER_ROW_HEIGHT,
                        0x44333333
                );
            }

            int x =
                    12 + row.depth() * 14;

            String arrow;

            if (folder.getChildren().isEmpty()) {

                arrow = " ";

            } else if (folder.isExpanded()) {

                arrow = "▼";

            } else {

                arrow = "▶";
            }

            graphics.drawString(
                    this.font,
                    arrow,
                    x,
                    rowY + 5,
                    0xAAAAAA,
                    false
            );

            graphics.drawString(
                    this.font,
                    folder.getName(),
                    x + 13,
                    rowY + 5,
                    selected
                            ? 0xFFFFFF
                            : 0xCCCCCC,
                    false
            );

            rowY +=
                    FOLDER_ROW_HEIGHT;
        }
    }

    private void renderNetworks(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {

        int left =
                folderPanelWidth + 8;

        int rowY = LIST_TOP;

        if (visibleNetworks.isEmpty()) {

            graphics.drawString(
                    this.font,
                    "No networks in this folder.",
                    left + 5,
                    rowY + 6,
                    0x888888,
                    false
            );

            return;
        }

        int endIndex = Math.min(visibleNetworks.size(), networkScroll + getVisibleNetworkRowCount());
        for (int i = networkScroll; i < endIndex; i++) {
            NetworkListS2CPacket.Entry entry = visibleNetworks.get(i);

            if (rowY + NETWORK_ROW_HEIGHT
                    > this.height - BOTTOM_MARGIN) {
                break;
            }

            boolean selected =
                    selectedNetworkIds.contains(entry.id());

            boolean hovered =
                    mouseX >= left
                            && mouseX <= this.width - 8
                            && mouseY >= rowY
                            && mouseY < rowY
                            + NETWORK_ROW_HEIGHT;

            if (selected) {

                graphics.fill(
                        left,
                        rowY,
                        this.width - 8,
                        rowY + NETWORK_ROW_HEIGHT - 2,
                        0x663399FF
                );

            } else if (hovered) {

                graphics.fill(
                        left,
                        rowY,
                        this.width - 8,
                        rowY + NETWORK_ROW_HEIGHT - 2,
                        0x33222222
                );
            }

            int stateColor =
                    entry.powered()
                            ? 0x55FF55
                            : 0xFF5555;

            String state =
                    entry.powered()
                            ? "ON"
                            : "OFF";

            graphics.drawString(
                    this.font,
                    "#"
                            + entry.id()
                            + "  "
                            + entry.name(),
                    left + 6,
                    rowY + 4,
                    0xFFFFFF,
                    false
            );

            graphics.drawString(
                    this.font,
                    "[" + state + "]",
                    this.width - 45,
                    rowY + 4,
                    stateColor,
                    false
            );

            graphics.drawString(
                    this.font,
                    "W:"
                            + entry.wires()
                            + "  I:"
                            + entry.inputs()
                            + "  O:"
                            + entry.outputs(),
                    left + 6,
                    rowY + 17,
                    0x999999,
                    false
            );

            rowY +=
                    NETWORK_ROW_HEIGHT;
        }
    }

    // =========================================================
    // ETC
    // =========================================================

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record VisibleFolderRow(
            FolderNode node,
            int depth
    ) {
    }
}
