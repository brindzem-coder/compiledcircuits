package com.example.compiledcircuits.client;

import com.example.compiledcircuits.network.OperationLimits;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class RenameNetworkScreen extends Screen {
    private final int networkId;
    private final Connection connection;
    private final String initialName;
    private EditBox name;
    private Button rename;

    public RenameNetworkScreen(int networkId, String initialName, Connection connection) {
        super(Component.translatable("screen.compiledcircuits.rename"));
        this.networkId = networkId;
        this.initialName = initialName;
        this.connection = connection;
    }
    @Override protected void init() {
        String text = name == null ? initialName : name.getValue();
        name = new EditBox(font, width / 2 - 120, height / 2 - 15, 240, 20, title);
        name.setMaxLength(OperationLimits.NAME);
        name.setValue(text);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 - 120, height / 2 + 20, 115, 20).build());
        rename = addRenderableWidget(Button.builder(Component.translatable("screen.compiledcircuits.rename.submit"), button -> submit())
                .bounds(width / 2 + 5, height / 2 + 20, 115, 20).build());
        name.setResponder(value -> rename.active = !value.trim().isEmpty());
        rename.active = !text.trim().isEmpty();
        setInitialFocus(name);
    }
    private boolean connected() {
        return minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection;
    }
    private void submit() {
        if (!rename.active || !connected()) return;
        // Bind the dialog to its original network, even if selection changes while it is open.
        minecraft.getConnection().sendCommand("circuit rename " + networkId + " " + name.getValue().trim());
        onClose();
    }
    @Override public void tick() {
        if (!connected()) onClose();
        else name.tick();
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float delta) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 45, 0xFFFFFF);
        super.render(graphics, x, y, delta);
    }
}
