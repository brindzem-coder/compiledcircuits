package com.example.compiledcircuits.client;

import com.example.compiledcircuits.network.WireIds;
import com.example.compiledcircuits.networking.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.UUID;

public final class BundleChannelScreen extends Screen {
    private final UUID token;
    private String value;
    private EditBox field;
    private boolean finished;
    private BundleChannelScreen(BundleEditorS2CPacket packet) {
        super(Component.translatable("bundle.compiledcircuits.editor")); token = packet.token(); value = packet.id();
    }
    public static void open(BundleEditorS2CPacket packet) { Minecraft.getInstance().setScreen(new BundleChannelScreen(packet)); }
    @Override protected void init() {
        if (field != null) value = field.getValue();
        field = new EditBox(font, width / 2 - 120, height / 2 - 10, 240, 20, title);
        field.setMaxLength(64); field.setFilter(s -> s.chars().allMatch(c -> c >= '0' && c <= '9')); field.setValue(value);
        addRenderableWidget(field); setInitialFocus(field);
        var done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> finish(false))
                .bounds(width / 2 + 5, height / 2 + 25, 115, 20).build());
        done.active = !field.getValue().isEmpty(); field.setResponder(s -> done.active = !s.isEmpty());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 - 120, height / 2 + 25, 115, 20).build());
    }
    private void finish(boolean cancel) {
        if (finished) return;
        String id = cancel ? "0" : WireIds.normalize(field.getValue()); finished = true;
        ModNetworking.CHANNEL.sendToServer(new BundleSettingsC2SPacket(token, id, cancel)); minecraft.setScreen(null);
    }
    @Override public void onClose() { finish(true); }
    @Override public void removed() {
        if (!finished) { finished = true; ModNetworking.CHANNEL.sendToServer(new BundleSettingsC2SPacket(token, "0", true)); }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
        renderBackground(graphics); super.render(graphics, x, y, tick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 38, 0xffffff);
    }
}
