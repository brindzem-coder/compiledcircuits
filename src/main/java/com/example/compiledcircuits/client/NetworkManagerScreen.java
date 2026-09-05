package com.example.compiledcircuits.client;

import com.example.compiledcircuits.networking.ModNetworking;
import com.example.compiledcircuits.networking.NetworkActionC2SPacket;
import com.example.compiledcircuits.networking.NetworkListS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    private static final int TOP = 35;
    private static final int LIST_TOP = 52;
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


    private NetworkListS2CPacket.Entry selectedNetwork;

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

    private int selectedFolderId = 0;

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
        if (findFolderNode(selectedFolderId) == null) selectedFolderId = 0;
        selectedNetwork = null;
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

        if (selectedFolderId == 0) {
            return;
        }

        FolderNode folder =
                findFolderNode(
                        selectedFolderId
                );

        if (folder == null) {
            return;
        }

        Minecraft.getInstance()
                .setScreen(
                        new NetworkTextEditScreen(
                                this,
                                "Rename Folder",
                                "Folder name:",
                                folder.getName(),
                                value ->
                                        ModNetworking.CHANNEL.sendToServer(
                                                new NetworkActionC2SPacket(
                                                        NetworkActionC2SPacket.Action.RENAME_FOLDER,
                                                        selectedFolderId,
                                                        value
                                                )
                                        )
                        )
                );
    }

    private void deleteFolder() {

        if (selectedFolderId == 0) {
            return;
        }

        ModNetworking.CHANNEL.sendToServer(
                new NetworkActionC2SPacket(
                        NetworkActionC2SPacket.Action.DELETE_FOLDER,
                        selectedFolderId,
                        ""
                )
        );
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

        /*
         * Якщо раніше була вибрана network,
         * але тепер ми відкрили іншу папку —
         * прибираємо selection.
         */
        if (selectedNetwork != null
                && !visibleNetworks.contains(
                selectedNetwork
        )) {

            selectedNetwork = null;
        }

        updateButtonsSafe();
    }

    // =========================================================
    // MOUSE
    // =========================================================

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {

        if (button == 0) {
            clearDrag();

            /*
             * LEFT FOLDER PANEL
             */
            if (mouseX >= 5
                    && mouseX < folderPanelWidth - 3
                    && mouseY >= LIST_TOP
                    && mouseY < this.height - BOTTOM_MARGIN) {

                int index =
                        (int) (
                                (mouseY - LIST_TOP)
                                        / FOLDER_ROW_HEIGHT
                        );

                if (index >= 0
                        && index < visibleFolderRows.size()) {

                    VisibleFolderRow row =
                            visibleFolderRows.get(
                                    index
                            );

                    FolderNode folder =
                            row.node();

                    /*
                     * Область маленької стрілочки.
                     */
                    int arrowX =
                            12
                                    + row.depth() * 14;

                    if (mouseX >= arrowX
                            && mouseX <= arrowX + 12
                            && !folder.getChildren().isEmpty()) {

                        folder.toggleExpanded();

                        rebuildVisibleFolderRows();

                        return true;
                    }

                    /*
                     * Клік по самій папці.
                     */
                    selectedFolderId = folder.getId();
                    if (selectedFolderId != 0) {
                        startDrag(DragType.FOLDER, selectedFolderId, mouseX, mouseY);
                    }

                    selectedNetwork = null;

                    rebuildVisibleNetworks();

                    return true;
                }
            }

            /*
             * RIGHT NETWORK PANEL
             */
            int networkLeft =
                    folderPanelWidth + 8;

            if (mouseX >= networkLeft
                    && mouseX <= this.width - 8
                    && mouseY >= LIST_TOP
                    && mouseY < this.height - BOTTOM_MARGIN) {

                int index =
                        (int) (
                                (mouseY - LIST_TOP)
                                        / NETWORK_ROW_HEIGHT
                        );

                if (index >= 0
                        && index < visibleNetworks.size()) {

                    selectedNetwork =
                            visibleNetworks.get(
                                    index
                            );

                    startDrag(DragType.NETWORK, selectedNetwork.id(), mouseX, mouseY);
                    updateButtons();

                    return true;
                }
            }
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button
        );
    }

    private enum DragType { NONE, NETWORK, FOLDER }

    private DragType dragType = DragType.NONE;
    private int dragId = -1;
    private double dragStartX;
    private double dragStartY;
    private boolean dragging;

    private void startDrag(DragType type, int id, double x, double y) {
        dragType = type;
        dragId = id;
        dragStartX = x;
        dragStartY = y;
        dragging = false;
    }

    private void clearDrag() {
        dragType = DragType.NONE;
        dragId = -1;
        dragging = false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragType != DragType.NONE) {
            double dx = mouseX - dragStartX;
            double dy = mouseY - dragStartY;
            if (dx * dx + dy * dy > 16.0D) dragging = true;
            if (dragging) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private FolderNode getFolderAt(double mouseX, double mouseY) {
        if (mouseX < 7 || mouseX >= folderPanelWidth - 2 || mouseY < LIST_TOP) return null;
        int index = (int) ((mouseY - LIST_TOP) / FOLDER_ROW_HEIGHT);
        if (index < 0 || index >= visibleFolderRows.size()
                || LIST_TOP + (index + 1) * FOLDER_ROW_HEIGHT > height - BOTTOM_MARGIN) return null;
        return visibleFolderRows.get(index).node();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            FolderNode target = getFolderAt(mouseX, mouseY);
            if (target != null) {
                ModNetworking.CHANNEL.sendToServer(new NetworkActionC2SPacket(
                        dragType == DragType.NETWORK
                                ? NetworkActionC2SPacket.Action.MOVE_NETWORK
                                : NetworkActionC2SPacket.Action.MOVE_FOLDER,
                        dragId, target.getId(), ""));
            }
            clearDrag();
            return true;
        }
        clearDrag();
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

        boolean active =
                selectedNetwork != null;

        highlightButton.active = active;
        renameButton.active = active;
        renameFolderButton.active = selectedFolderId != 0;
        deleteFolderButton.active = selectedFolderId != 0;
        decompileButton.active = active;
    }

    private void highlightSelected() {

        if (selectedNetwork == null) {
            return;
        }

        sendAction(
                NetworkActionC2SPacket.Action.HIGHLIGHT,
                ""
        );
    }

    private void renameSelected() {

        if (selectedNetwork == null) {
            return;
        }

        Minecraft.getInstance()
                .setScreen(
                        new NetworkTextEditScreen(
                                this,
                                "Rename Network",
                                "Network name:",
                                selectedNetwork.name(),
                                value ->
                                        sendAction(
                                                NetworkActionC2SPacket.Action.RENAME_NETWORK,
                                                value
                                        )
                        )
                );
    }

    private void decompileSelected() {

        if (selectedNetwork == null) {
            return;
        }

        sendAction(
                NetworkActionC2SPacket.Action.DECOMPILE,
                ""
        );
    }

    private void sendAction(
            NetworkActionC2SPacket.Action action,
            String value
    ) {

        if (selectedNetwork == null) {
            return;
        }

        ModNetworking.CHANNEL.sendToServer(
                new NetworkActionC2SPacket(
                        action,
                        selectedNetwork.id(),
                        value
                )
        );
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
                30,
                this.width - 5,
                this.height - 35,
                0xAA111111
        );

        /*
         * Left folder background.
         */
        graphics.fill(
                7,
                32,
                folderPanelWidth - 2,
                this.height - 37,
                0xAA181818
        );

        /*
         * Separator.
         */
        graphics.fill(
                folderPanelWidth,
                32,
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
                "Folders",
                12,
                37,
                0xAAAAAA
        );

        graphics.drawString(
                this.font,
                "Networks",
                folderPanelWidth + 10,
                37,
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
            graphics.drawString(font, dragType == DragType.NETWORK ? "Move network" : "Move folder",
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

        for (VisibleFolderRow row : visibleFolderRows) {

            if (rowY + FOLDER_ROW_HEIGHT
                    > this.height - BOTTOM_MARGIN) {
                break;
            }

            FolderNode folder =
                    row.node();

            boolean selected =
                    selectedFolderId == folder.getId();

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

        for (NetworkListS2CPacket.Entry entry
                : visibleNetworks) {

            if (rowY + NETWORK_ROW_HEIGHT
                    > this.height - BOTTOM_MARGIN) {
                break;
            }

            boolean selected =
                    selectedNetwork != null
                            && selectedNetwork.id()
                            == entry.id();

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