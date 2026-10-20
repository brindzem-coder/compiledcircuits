package com.example.compiledcircuits.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import com.example.compiledcircuits.networking.NetworkListS2CPacket;
import java.util.List;

import static com.example.compiledcircuits.client.ManagerState.*;
import static com.example.compiledcircuits.client.ManagerModel.*;
import static com.example.compiledcircuits.client.ManagerLayout.*;

/** Read-only drawing adapter; owns no selection and sends no operations. */
final class ManagerRenderer {
    private final ManagerModel model;
    private final ManagerState state;
    private final ManagerLayout layout;
    private final Font font;
    ManagerRenderer(ManagerModel model, ManagerState state, ManagerLayout layout, Font font) {
        this.model=model;this.state=state;this.layout=layout;this.font=font;
    }
    void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        /*
         * Main window background.
         */
        graphics.fill(
                5,
                TOP,
                layout.width - 5,
                layout.height - 35,
                0xAA111111
        );

        if (state.mainViewMode == MainViewMode.BROKEN) {
            graphics.drawCenteredString(font, "Compiled Circuits - Network Manager" + (model.warning.isEmpty() ? "" : " [invalid folders]"), layout.width / 2, 12, 0xFFFFFF);
            renderBrokenMode(graphics, mouseX, mouseY);
            return;
        }

        if (!isSearchMode()) {
            /*
             * Left folder background.
             */
            graphics.fill(
                    7,
                    TOP + 2,
                    layout.folderPanelWidth - 2,
                    layout.height - 37,
                    0xAA181818
            );

            /*
             * Separator.
             */
            graphics.fill(
                    layout.folderPanelWidth,
                    TOP + 2,
                    layout.folderPanelWidth + 1,
                    layout.height - 37,
                    0xFF555555
            );
        }

        graphics.drawCenteredString(
                font,
                "Compiled Circuits - Network Manager" + (model.warning.isEmpty() ? "" : " [invalid folders]"),
                layout.width / 2,
                12,
                0xFFFFFF
        );

        if (isSearchMode()) {
            graphics.drawString(font, "Search Results (last output)", 12, TOP + 2, 0xAAAAAA);
            graphics.drawString(font, "Selected (including hidden): " + state.selectedNetworkIds.size() + " networks, " + state.selectedFolderIds.size() + " folders", 12, TOP + 15, 0x999999, false);
            renderSearchResults(graphics, mouseX, mouseY);
        } else {
            graphics.drawString(
                    font,
                    "Folders (" + state.selectedFolderIds.size() + " selected)",
                    12,
                    TOP + 2,
                    0xAAAAAA
            );

            graphics.drawString(
                    font,
                    "Networks (last output)",
                    layout.folderPanelWidth + 10,
                    TOP + 2,
                    0xAAAAAA
            );

            graphics.drawString(font, "Folder: " + getFolderPath(state.selectedFolderId),
                    layout.folderPanelWidth + 10, TOP + 13, 0x999999, false);
            graphics.drawString(font, "Selected networks: " + state.selectedNetworkIds.size(),
                    layout.folderPanelWidth + 10, TOP + 24, 0x999999, false);

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
        }

        if (state.dragging) {
            graphics.drawString(font, state.dragType == DragType.NETWORK
                    ? "Move " + state.selectedNetworkIds.size() + " networks"
                    : "Move " + state.selectedFolderIds.size() + " folders",
                    mouseX + 10, mouseY + 10, 0xFFFFAA);
        }
    }

    private void renderBrokenMode(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "Broken Elements", 13, TOP + 6, 0xFFFFFF, false);
        graphics.drawString(font, "Total: " + model.brokenEntries.size() + " | Selected: " + state.selectedBrokenKeys.size(), 13, TOP + 18, 0xAAAAAA, false);
        if(!ClientDamageSync.ready()){
            graphics.drawString(font,"Damage data: "+ClientDamageSync.status()+" — synchronizing",13,LIST_TOP+3,0xFFBB55,false);return;
        }
        if (model.brokenEntries.isEmpty()) {
            graphics.drawString(font, "All compiled networks are healthy.", 13, LIST_TOP + 3, 0xAAAAAA, false);
            return;
        }
        graphics.enableScissor(layout.broken().left(), LIST_TOP, Math.max(layout.broken().left(), layout.broken().right()), Math.max(LIST_TOP, getBrokenBottom()));
        try {
            for (int row = 0; row < getVisibleBrokenRowCount() && state.brokenScroll + row < model.brokenEntries.size(); row++) {
                var entry = model.brokenEntries.get(state.brokenScroll + row);
                int y = LIST_TOP + row * BROKEN_ROW_HEIGHT;
                boolean selected = isBrokenSelected(entry);
                boolean hovered = entry.equals(getBrokenEntryAt(mouseX, mouseY));
                if (selected || hovered) {
                    graphics.fill(layout.broken().left(), y, layout.broken().right(), y + BROKEN_ROW_HEIGHT - 1,
                            selected ? 0x663399FF : 0x33222222);
                }
                String line1 = entry.networkName() + " (#" + entry.networkId() + ") | Element #"
                        + entry.elementId() + " | " + getBlockDisplayName(entry.blockId());
                var pos = entry.pos();
                String line2 = ClientDamageSync.folderPath(entry.networkId());
                String line3 = "X: " + pos.getX() + " Y: " + pos.getY() + " Z: " + pos.getZ()
                        + " | " + entry.type().name() + " | " + getDimensionDisplayName(entry.dimension());
                graphics.drawString(font, line1, 13, y + 3, 0xFFFFFF, false);
                graphics.drawString(font, line2, 13, y + 14, 0xAAAAAA, false);
                graphics.drawString(font, line3, 13, y + 25, 0x999999, false);
            }
        } finally {
            graphics.disableScissor();
        }
        var hoveredEntry = getBrokenEntryAt(mouseX, mouseY);
        if (hoveredEntry != null) {
            graphics.renderTooltip(font, List.of(
                    Component.literal("Expected: " + getBlockDisplayName(hoveredEntry.blockId())),
                    Component.literal("Actual: " + getBlockDisplayName(hoveredEntry.actualBlockId()))
            ), java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private String getDimensionDisplayName(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> dimension;
        };
    }

    private String getBlockDisplayName(String blockId) {
        StringBuilder result = new StringBuilder();
        for (String part : blockId.substring(blockId.indexOf(':') + 1).split("_")) {
            if (part.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private void renderSearchResults(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = layout.search().left();
        int right = layout.search().right();
        int rowY = LIST_TOP;
        if (model.searchResults.isEmpty()) {
            graphics.drawString(font, "No results.", left + 5, rowY + 6, 0x888888, false);
            return;
        }
        int end = Math.min(model.searchResults.size(), state.searchScroll + getVisibleSearchRowCount());
        for (int i = state.searchScroll; i < end; i++) {
            if (rowY + SEARCH_ROW_HEIGHT > layout.height - BOTTOM_MARGIN) break;
            SearchResult result = model.searchResults.get(i);
            boolean network = result.type() == SearchResultType.NETWORK;
            boolean selected = network ? state.selectedNetworkIds.contains(result.id())
                    : state.selectedFolderIds.contains(result.id());
            boolean hovered = mouseX >= left && mouseX <= right
                    && mouseY >= rowY && mouseY < rowY + SEARCH_ROW_HEIGHT;
            if (selected || hovered) {
                graphics.fill(left, rowY, right, rowY + SEARCH_ROW_HEIGHT - 2,
                        selected ? 0x663399FF : 0x33222222);
            }
            String typeLabel = network ? "[NETWORK]" : "[FOLDER]";
            graphics.drawString(font, typeLabel, left + 6, rowY + 4,
                    network ? 0xAAAAFF : 0xFFCC66, false);
            int nameX = left + 12 + font.width(typeLabel);
            String title = network ? "#" + result.id() + "  " + result.name() : result.name();
            int nameRight = network ? right - 46 : right - 6;
            graphics.drawString(font, font.plainSubstrByWidth(title, Math.max(0, nameRight - nameX)),
                    nameX, rowY + 4, 0xFFFFFF, false);
            if (network && result.network() != null) {
                boolean powered = result.network().powered() && !isNetworkBroken(result.network().id());
                graphics.drawString(font, powered ? "[ON]" : "[OFF]", right - 38,
                        rowY + 4, powered ? 0x55FF55 : 0xFF5555, false);
            }
            graphics.drawString(font, font.plainSubstrByWidth(result.path(),
                    Math.max(0, right - left - 12)), left + 6, rowY + 17, 0x999999, false);
            rowY += SEARCH_ROW_HEIGHT;
        }
    }

    private void renderFolderTree(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {

        int rowY = LIST_TOP;

        int endIndex = Math.min(model.visibleFolderRows.size(), state.folderScroll + getVisibleFolderRowCount());
        for (int i = state.folderScroll; i < endIndex; i++) {
            VisibleFolderRow row = model.visibleFolderRows.get(i);

            if (rowY + FOLDER_ROW_HEIGHT
                    > layout.height - BOTTOM_MARGIN) {
                break;
            }

            FolderNode folder =
                    row.node();

            boolean selected =
                    state.selectedFolderIds.contains(folder.getId());

            boolean hovered =
                    layout.folders().contains(mouseX, mouseY)
                            && mouseY >= rowY
                            && mouseY < rowY + FOLDER_ROW_HEIGHT;

            if (selected) {

                graphics.fill(
                        layout.folders().left() + 1,
                        rowY,
                        layout.folders().right() - 1,
                        rowY + FOLDER_ROW_HEIGHT,
                        0x884477AA
                );

            } else if (hovered) {

                graphics.fill(
                        layout.folders().left() + 1,
                        rowY,
                        layout.folders().right() - 1,
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
                    font,
                    arrow,
                    x,
                    rowY + 5,
                    0xAAAAAA,
                    false
            );

            graphics.drawString(
                    font,
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

        int left = layout.networks().left();

        int rowY = LIST_TOP;

        if (model.visibleNetworks.isEmpty()) {

            graphics.drawString(
                    font,
                    "No networks in this folder.",
                    left + 5,
                    rowY + 6,
                    0x888888,
                    false
            );

            return;
        }

        int endIndex = Math.min(model.visibleNetworks.size(), state.networkScroll + getVisibleNetworkRowCount());
        for (int i = state.networkScroll; i < endIndex; i++) {
            NetworkListS2CPacket.Entry entry = model.visibleNetworks.get(i);

            if (rowY + NETWORK_ROW_HEIGHT
                    > layout.height - BOTTOM_MARGIN) {
                break;
            }

            boolean selected =
                    state.selectedNetworkIds.contains(entry.id());

            boolean hovered =
                    mouseX >= left
                            && mouseX <= layout.networks().right()
                            && mouseY >= rowY
                            && mouseY < rowY
                            + NETWORK_ROW_HEIGHT;

            if (selected) {

                graphics.fill(
                        left,
                        rowY,
                        layout.networks().right(),
                        rowY + NETWORK_ROW_HEIGHT - 2,
                        0x663399FF
                );

            } else if (hovered) {

                graphics.fill(
                        left,
                        rowY,
                        layout.networks().right(),
                        rowY + NETWORK_ROW_HEIGHT - 2,
                        0x33222222
                );
            }

            int stateColor =
                    entry.powered() && !isNetworkBroken(entry.id())
                            ? 0x55FF55
                            : 0xFF5555;

            String state =
                    entry.powered() && !isNetworkBroken(entry.id())
                            ? "ON"
                            : "OFF";

            String status = "[" + state + "]";
            boolean broken = isNetworkBroken(entry.id());
            int right = layout.width - 12;
            int stateX = right - font.width(status);
            graphics.drawString(font, font.plainSubstrByWidth("#" + entry.id() + "  " + entry.name(),
                    Math.max(0, stateX - left - 12)), left + 6, rowY + 4, 0xFFFFFF, false);
            graphics.drawString(font, status, stateX, rowY + 4, stateColor, false);
            int brokenX = right - font.width("BROKEN");
            String counts = "W:" + entry.wires() + "  I:" + entry.inputs() + "  O:" + entry.outputs();
            graphics.drawString(font, font.plainSubstrByWidth(counts,
                    Math.max(0, (broken ? brokenX - 6 : right) - left - 6)),
                    left + 6, rowY + 17, 0x999999, false);
            if (broken) graphics.drawString(font, "BROKEN", brokenX, rowY + 17, 0xFF5555, false);

            rowY +=
                    NETWORK_ROW_HEIGHT;
        }
    }
    private boolean isSearchMode() { return model.isSearchMode(); }
    private String getFolderPath(int id) { return model.getFolderPath(id); }
    private boolean isNetworkBroken(int id) { return model.isNetworkBroken(id); }
    private boolean isBrokenSelected(com.example.compiledcircuits.networking.BrokenElementListS2CPacket.Entry e) { return state.selectedBrokenKeys.contains(ManagerModel.key(e)); }
    private com.example.compiledcircuits.networking.BrokenElementListS2CPacket.Entry getBrokenEntryAt(double x,double y) {
        int i = ClientDamageSync.ready() ? layout.broken().index(x,y,state.brokenScroll,model.brokenEntries.size()) : -1;
        return i < 0 ? null : model.brokenEntries.get(i);
    }
    private int getBrokenBottom() { return layout.broken().bottom(); }
    private int getVisibleBrokenRowCount() { return layout.broken().count(); }
    private int getVisibleFolderRowCount() { return layout.folders().count(); }
    private int getVisibleNetworkRowCount() { return layout.networks().count(); }
    private int getVisibleSearchRowCount() { return layout.search().count(); }

}
