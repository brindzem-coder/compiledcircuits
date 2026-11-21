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
    private final boolean mapping;
    private String error="";
    private String value;
    private EditBox field;
    private boolean finished;
    private BundleChannelScreen(BundleEditorS2CPacket packet) {
        super(Component.translatable(packet.mapping()?"mapping.compiledcircuits.editor":"bundle.compiledcircuits.editor")); token = packet.token(); value = packet.id(); mapping=packet.mapping();
    }
    public static void open(BundleEditorS2CPacket packet) { Minecraft.getInstance().setScreen(packet.keyboard() ? new KeyboardBindingScreen(packet) : new BundleChannelScreen(packet)); }
    @Override protected void init() {
        if (field != null) value = field.getValue();
        field = new EditBox(font, width / 2 - 120, height / 2 - 10, 240, 20, title);
        field.setMaxLength(mapping?2048:64); field.setFilter(s -> s.chars().allMatch(c -> c >= '0' && c <= '9' || mapping && (c==','||c=='-'||c=='='||c==' '))); field.setValue(value);
        addRenderableWidget(field); setInitialFocus(field);
        var done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> finish(false))
                .bounds(width / 2 + 5, height / 2 + 25, 115, 20).build());
        done.active = valid(field.getValue()); field.setResponder(s -> done.active = valid(s));
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 - 120, height / 2 + 25, 115, 20).build());
    }
    private boolean valid(String text){
        try{if(mapping)com.example.compiledcircuits.network.ChannelMapping.parse(text);else WireIds.normalize(text);error="";return true;}
        catch(IllegalArgumentException invalid){error=invalid.getMessage();return false;}
    }
    private void finish(boolean cancel) {
        if (finished || !cancel&&!valid(field.getValue())) return;
        String id = cancel ? "0" : mapping?com.example.compiledcircuits.network.ChannelMapping.parse(field.getValue()).text():WireIds.normalize(field.getValue()); finished = true;
        ModNetworking.CHANNEL.sendToServer(new BundleSettingsC2SPacket(token, id, cancel)); minecraft.setScreen(null);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER||key==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            if(field!=null&&!field.getValue().isEmpty())finish(false); return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void onClose() { finish(true); }
    @Override public void removed() {
        if (!finished) { finished = true; ModNetworking.CHANNEL.sendToServer(new BundleSettingsC2SPacket(token, "0", true)); }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
        renderBackground(graphics); super.render(graphics, x, y, tick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 38, 0xffffff);
        if(mapping)graphics.drawCenteredString(font, Component.translatable("mapping.compiledcircuits.example"),width/2,height/2-26,0xaaaaaa);
        if(!error.isEmpty())graphics.drawCenteredString(font,font.plainSubstrByWidth(error,Math.max(100,width-20)),width/2,height/2+55,0xff7777);
    }
}
