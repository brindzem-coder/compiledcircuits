package com.example.compiledcircuits.client;

import com.example.compiledcircuits.network.WireIds;
import com.example.compiledcircuits.networking.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.*;

/** Draft settings stay local until Done. Selecting a catalog row loads its current color. */
public final class WireEditorScreen extends Screen {
    private final UUID token;
    private String draft;
    private int rgb, scroll;
    private float hue, saturation, brightness;
    private int x, y, panelWidth, panelHeight, leftWidth, paletteX, paletteY, paletteWidth, paletteHeight, rows;
    private EditBox idBox, hexBox;
    private Button done;
    private boolean finished, updating;
    private int drag;
    private List<String> ids = List.of("0");
    public WireEditorScreen(WireEditorS2CPacket packet) {
        super(Component.translatable("wire.compiledcircuits.title")); token = packet.token(); draft = packet.id(); setColor(packet.color());
    }
    private static Component label(String key) { return Component.translatable("wire.compiledcircuits." + key); }
    public void catalogChanged() {
        var entries = new ArrayList<>(WireClient.colors().keySet()); entries.remove("0"); entries.sort(WireIds::compare); entries.add(0, "0"); ids = entries;
        scroll = Mth.clamp(scroll, 0, Math.max(0, ids.size() - rows));
        if (colorLocked()) {
            setColor(WireClient.color(WireIds.normalize(draft), rgb));
            refreshHex(); drag = 0;
        }
    }
    @Override protected void init() {
        panelWidth = Math.min(430, width - 16); panelHeight = Math.min(300, height - 16);
        x = (width - panelWidth) / 2; y = (height - panelHeight) / 2; leftWidth = panelWidth * 2 / 5;
        paletteX = x + leftWidth + 18; paletteY = y + 100; paletteWidth = panelWidth - leftWidth - 30;
        paletteHeight = Math.max(32, panelHeight - 184); rows = Math.max(1, (panelHeight - 82) / 20);
        idBox = new EditBox(font, paletteX, y + 47, paletteWidth, 20, label("id")); idBox.setMaxLength(WireIds.MAX_LENGTH); idBox.setValue(draft);
        idBox.setResponder(text -> {
            draft = text;
            try { String id = WireIds.normalize(text); if (id.equals("0")) setColor(WireIds.RED); else if (WireClient.colors().containsKey(id)) setColor(WireClient.colors().get(id)); }
            catch (IllegalArgumentException ignored) { }
            refreshHex();
        }); addRenderableWidget(idBox);
        hexBox = new EditBox(font, paletteX + 22, paletteY + paletteHeight + 25, paletteWidth - 22, 18, label("hex"));
        hexBox.setMaxLength(7); refreshHex();
        hexBox.setResponder(text -> {
            if (updating || colorLocked()) return;
            String value = text.startsWith("#") ? text.substring(1) : text;
            if (value.matches("[0-9a-fA-F]{6}")) setColor(Integer.parseInt(value, 16));
        }); addRenderableWidget(hexBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + 12, y + panelHeight - 30, (panelWidth - 32) / 2, 20).build());
        done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> finish()).bounds(x + 20 + (panelWidth - 32) / 2, y + panelHeight - 30, (panelWidth - 32) / 2, 20).build());
        catalogChanged(); setInitialFocus(idBox);
    }
    private boolean zero() { try { return WireIds.normalize(draft).equals("0"); } catch (IllegalArgumentException invalid) { return false; } }
    private boolean colorLocked() { try { return WireClient.isColorLocked(WireIds.normalize(draft)); } catch (IllegalArgumentException invalid) { return false; } }
    private void setColor(int color) {
        rgb = color & 0xffffff;
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        brightness = max; saturation = max == 0 ? 0 : d / max;
        if (d != 0) hue = ((max == r ? (g - b) / d + (g < b ? 6 : 0) : max == g ? (b - r) / d + 2 : (r - g) / d + 4) / 6f);
    }
    private void refreshHex() { if (hexBox == null) return; updating = true; hexBox.setValue(String.format("#%06X", rgb)); updating = false; }
    private void finish() {
        try { String id = WireIds.normalize(draft); finished = true; ModNetworking.CHANNEL.sendToServer(new WireSettingsC2SPacket(token, id, id.equals("0") ? WireIds.RED : rgb, false)); super.onClose(); }
        catch (IllegalArgumentException ignored) { }
    }
    @Override public void onClose() { cancel(); super.onClose(); }
    @Override public void removed() { cancel(); super.removed(); }
    private void cancel() { if (!finished) { finished = true; ModNetworking.CHANNEL.sendToServer(new WireSettingsC2SPacket(token, "0", 0, true)); } }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { idBox.tick(); hexBox.tick(); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); g.fill(x, y, x + panelWidth, y + panelHeight, 0xff20252d);
        g.drawCenteredString(font, title, width / 2, y + 12, 0xffffff);
        g.drawString(font, label("catalog"), x + 12, y + 32, 0xc3cad5);
        g.drawString(font, label("id"), paletteX, y + 32, 0xc3cad5);
        g.drawString(font, zero() ? label("fixed") : colorLocked() ? label("locked_short") : label("palette"), paletteX, y + 80, 0xc3cad5);
        String selected; try { selected = WireIds.normalize(draft); done.active = true; } catch (IllegalArgumentException invalid) { selected = ""; done.active = false; }
        hexBox.setEditable(!colorLocked()); hexBox.active = !colorLocked();
        for (int i = 0; i < rows && i + scroll < ids.size(); i++) {
            String id = ids.get(i + scroll); int rowY = y + 47 + i * 20;
            g.fill(x + 10, rowY, x + leftWidth + 8, rowY + 19, id.equals(selected) ? 0xff3c546b : 0xff2b323e);
            g.fill(x + 15, rowY + 4, x + 26, rowY + 15, 0xff000000 | WireClient.color(id, WireIds.RED));
            boolean locked = !id.equals("0") && WireClient.isColorLocked(id);
            String text = font.plainSubstrByWidth(id, leftWidth - (locked ? 46 : 32)); g.drawString(font, text, x + 31, rowY + 6, 0xffffff);
            if (locked) {
                int lockX = x + leftWidth - 4;
                g.renderOutline(lockX, rowY + 3, 6, 7, 0xffe3ba62);
                g.fill(lockX - 1, rowY + 8, lockX + 7, rowY + 15, 0xffe3ba62);
            }
            if (mouseX >= x + 10 && mouseX < x + leftWidth + 8 && mouseY >= rowY && mouseY < rowY + 19 && !text.equals(id)) g.renderTooltip(font, Component.literal(id), mouseX, mouseY);
        }
        if (ids.size() > rows) {
            int track = rows * 20, thumb = Math.max(8, track * rows / ids.size());
            int offset = scroll * (track - thumb) / (ids.size() - rows);
            g.fill(x + leftWidth + 9, y + 47 + offset, x + leftWidth + 12, y + 47 + offset + thumb, 0xff91a2b9);
        }
        for (int px = 0; px < paletteWidth; px += 2) for (int py = 0; py < paletteHeight; py += 2)
            g.fill(paletteX + px, paletteY + py, paletteX + Math.min(px + 2, paletteWidth), paletteY + Math.min(py + 2, paletteHeight), 0xff000000 | Mth.hsvToRgb(hue, px / (float)(paletteWidth - 1), 1 - py / (float)(paletteHeight - 1)));
        for (int px = 0; px < paletteWidth; px++) g.fill(paletteX + px, paletteY + paletteHeight + 7, paletteX + px + 1, paletteY + paletteHeight + 18, 0xff000000 | Mth.hsvToRgb(px / (float)paletteWidth, 1, 1));
        int sx = paletteX + Math.round(saturation * (paletteWidth - 1)), sy = paletteY + Math.round((1 - brightness) * (paletteHeight - 1));
        g.renderOutline(sx - 2, sy - 2, 5, 5, 0xffffffff);
        g.renderOutline(paletteX + Math.round(hue * (paletteWidth - 1)) - 1, paletteY + paletteHeight + 6, 3, 13, 0xffffffff);
        if (colorLocked()) g.fill(paletteX, paletteY, paletteX + paletteWidth, paletteY + paletteHeight + 19, 0x88000000);
        g.fill(paletteX, paletteY + paletteHeight + 25, paletteX + 18, paletteY + paletteHeight + 43, 0xff000000 | rgb);
        super.render(g, mouseX, mouseY, partialTick);
    }
    private void palette(double mx, double my) {
        if (colorLocked()) return;
        if (drag == 1) { saturation = Mth.clamp((float)(mx - paletteX) / (paletteWidth - 1), 0, 1); brightness = 1 - Mth.clamp((float)(my - paletteY) / (paletteHeight - 1), 0, 1); }
        else hue = Mth.clamp((float)(mx - paletteX) / paletteWidth, 0, 0.99999f);
        rgb = Mth.hsvToRgb(hue, saturation, brightness); refreshHex();
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (mx >= x + 10 && mx < x + leftWidth + 8 && my >= y + 47 && my < y + 47 + rows * 20) {
                int index = scroll + (int)(my - y - 47) / 20; if (index < ids.size()) { idBox.setValue(ids.get(index)); return true; }
            }
            if (mx >= paletteX && mx < paletteX + paletteWidth && !colorLocked()) {
                if (my >= paletteY && my < paletteY + paletteHeight) drag = 1;
                else if (my >= paletteY + paletteHeight + 7 && my < paletteY + paletteHeight + 18) drag = 2;
                if (drag != 0) { palette(mx, my); return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { if (drag != 0) { palette(mx, my); return true; } return super.mouseDragged(mx, my, button, dx, dy); }
    @Override public boolean mouseReleased(double mx, double my, int button) { drag = 0; return super.mouseReleased(mx, my, button); }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= x && mx < x + leftWidth + 12) { scroll = Mth.clamp(scroll - (int)Math.signum(delta), 0, Math.max(0, ids.size() - rows)); return true; }
        return super.mouseScrolled(mx, my, delta);
    }
}
