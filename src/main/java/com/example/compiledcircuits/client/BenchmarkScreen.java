package com.example.compiledcircuits.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only measurements; benchmark execution is deliberately a command. */
public final class BenchmarkScreen extends Screen {
    private final Screen parent;
    private final int id;
    private int scroll;
    private boolean returning;
    public BenchmarkScreen(Screen parent, int id) { super(Component.literal("Network measurements")); this.parent = parent; this.id = id; }
    NetworkManagerScreen parentManager() { return parent instanceof NetworkManagerScreen manager ? manager : null; }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(width / 2 - 40, height - 26, 80, 20).build());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { returning = true; minecraft.setScreen(parent); }
    @Override public void removed() {
        if (!returning && parent instanceof NetworkManagerScreen manager) manager.closeSession();
        super.removed();
    }
    @Override public void tick() {
        if (parent instanceof NetworkManagerScreen manager) {
            if (!manager.contextValid()) { manager.closeSession(); minecraft.setScreen(null); return; }
            manager.sessionTick();
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) { scroll = Math.max(0, scroll - (int) delta * 20); return true; }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g); g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        var wrapped = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (var line : ClientBenchmarks.lines(id)) wrapped.addAll(font.split(Component.literal(line), Math.max(40, width - 24)));
        scroll = Math.min(scroll, Math.max(0, wrapped.size() * 13 - (height - 62)));
        g.enableScissor(8, 28, width - 8, height - 32);
        int y = 28 - scroll;
        for (var line : wrapped) { g.drawString(font, line, 12, y, 0xEEEEEE); y += 13; }
        g.disableScissor(); super.render(g, mouseX, mouseY, partial);
    }
}
