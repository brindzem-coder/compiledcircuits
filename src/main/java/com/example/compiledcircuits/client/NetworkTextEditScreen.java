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

    public NetworkTextEditScreen(
            Screen parent,
            String title,
            String label,
            String initialValue,
            Consumer<String> onSave
    ) {
        super(
                Component.literal(title)
        );

        this.parent = parent;
        this.label = label;
        this.initialValue = initialValue;
        this.onSave = onSave;
    }

    @Override
    protected void init() {

        int centerX =
                this.width / 2;

        int centerY =
                this.height / 2;

        editBox =
                new EditBox(
                        this.font,
                        centerX - 120,
                        centerY - 15,
                        240,
                        20,
                        Component.literal(label)
                );

        editBox.setMaxLength(256);
        editBox.setValue(initialValue);

        addRenderableWidget(editBox);

        setInitialFocus(editBox);

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
                                button ->
                                        Minecraft.getInstance()
                                                .setScreen(parent)
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

    private void save() {

        onSave.accept(
                editBox.getValue()
        );

        Minecraft.getInstance()
                .setScreen(parent);
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