package com.example.compiledcircuits.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class NetworkTextEditScreen
        extends Screen {

    private final Screen parent;

    private final String label;
    private final String initialValue;

    private final Consumer<String> onSave;

    private EditBox editBox;
    private final int maxLength;
    private final Runnable onCancel;
    private boolean finished, returningToParent;

    public NetworkTextEditScreen(
            Screen parent,
            String title,
            String label,
            String initialValue,
            Consumer<String> onSave
    ) {
        this(parent, title, label, initialValue, onSave, com.example.compiledcircuits.network.OperationLimits.NAME);
    }

    public NetworkTextEditScreen(Screen parent, String title, String label, String initialValue,
                                 Consumer<String> onSave, int maxLength) {
        this(parent, title, label, initialValue, onSave, maxLength, () -> {});
    }
    public NetworkTextEditScreen(Screen parent, String title, String label, String initialValue,
                                 Consumer<String> onSave, int maxLength, Runnable onCancel) {
        super(Component.literal(title));
        this.onCancel = onCancel;

        this.parent = parent;
        this.label = label;
        this.initialValue = initialValue;
        this.onSave = onSave;
        this.maxLength = maxLength;
    }

    @Override
    protected void init() {

        int centerX =
                this.width / 2;

        int centerY =
                this.height / 2;

        String text = editBox == null ? initialValue : editBox.getValue();
        int cursor = editBox == null ? text.length() : editBox.getCursorPosition();
        boolean focused = editBox == null || editBox.isFocused();
        editBox =
                new EditBox(
                        this.font,
                        centerX - 120,
                        centerY - 15,
                        240,
                        20,
                        Component.literal(label)
                );

        editBox.setMaxLength(maxLength);
        editBox.setValue(text);
        editBox.setCursorPosition(cursor);

        addRenderableWidget(editBox);

        if (focused) { setInitialFocus(editBox); editBox.setFocused(true); }

        addRenderableWidget(
                Button.builder(
                                Component.literal("Save"),
                                button -> save()
                        )
                        .bounds(
                                centerX - 105,
                                centerY + 20,
                                100,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("Cancel"),
                                button -> onClose()
                        )
                        .bounds(
                                centerX + 5,
                                centerY + 20,
                                100,
                                20
                        )
                        .build()
        );
    }

    NetworkManagerScreen parentManager() { return parent instanceof NetworkManagerScreen manager ? manager : null; }
    private void returnToParent() {
        var manager = parentManager();
        returningToParent = manager == null || manager.contextValid();
        Minecraft.getInstance().setScreen(returningToParent ? parent : null);
    }
    private void save() {
        if (finished || Minecraft.getInstance().screen != this) return;
        finished = true;
        onSave.accept(editBox.getValue());
        if (Minecraft.getInstance().screen == this) returnToParent();
    }
    @Override public void onClose() {
        if (!finished) { finished = true; onCancel.run(); }
        returnToParent();
    }
    @Override public void tick() {
        var manager = parentManager();
        if (manager != null && !manager.contextValid()) {
            manager.closeSession(); finished = true; onCancel.run(); Minecraft.getInstance().setScreen(null); return;
        }
        if (manager != null) manager.sessionTick();
        super.tick();
    }
    @Override public void removed() {
        var manager = parentManager();
        if (!returningToParent) {
            if (!finished) { finished = true; onCancel.run(); }
            if (manager != null) manager.closeSession();
        }
        super.removed();
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {

        if (keyCode == 257
                || keyCode == 335) {

            save();
            return true;
        }

        return super.keyPressed(
                keyCode,
                scanCode,
                modifiers
        );
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        renderBackground(graphics);

        graphics.drawCenteredString(
                this.font,
                this.title,
                this.width / 2,
                this.height / 2 - 55,
                0xFFFFFF
        );

        graphics.drawString(
                this.font,
                label,
                this.width / 2 - 120,
                this.height / 2 - 30,
                0xAAAAAA
        );

        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }
}