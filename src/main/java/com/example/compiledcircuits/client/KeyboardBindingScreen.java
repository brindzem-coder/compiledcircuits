package com.example.compiledcircuits.client;

import com.example.compiledcircuits.network.KeyboardBinding;
import com.example.compiledcircuits.networking.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class KeyboardBindingScreen extends Screen {
    private final UUID token;
    private KeyboardBinding binding;
    private final Set<Integer> held=new HashSet<>(), captured=new HashSet<>();
    private boolean recording,finished;
    private int settle;
    private double lastX,lastY;
    private boolean positionKnown;
    private String error="";
    public KeyboardBindingScreen(BundleEditorS2CPacket packet){super(Component.translatable("keyboard.compiledcircuits.bind"));token=packet.token();binding=KeyboardBinding.parse(packet.id());}
    @Override protected void init(){
        addRenderableWidget(Button.builder(Component.translatable("keyboard.compiledcircuits.record"),b->{recording=true;held.clear();captured.clear();positionKnown=false;settle=0;error="";})
                .bounds(width/2-120,height/2,240,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->finish(false)).bounds(width/2+5,height/2+30,115,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(width/2-120,height/2+30,115,20).build());
    }
    public boolean recording(){return recording;}
    public void rawKey(int key,int action){
        if(key==GLFW.GLFW_KEY_ESCAPE){if(action==GLFW.GLFW_PRESS){recording=false;held.clear();captured.clear();}return;}
        if(!KeyboardBinding.valid(key))return;
        if(action==GLFW.GLFW_RELEASE){held.remove(key);if(held.isEmpty()&&!captured.isEmpty())settle=5;}
        else if(action==GLFW.GLFW_PRESS){held.add(key);capture(key);}
    }
    private void capture(int code){
        if(captured.size()<KeyboardBinding.MAX_KEYS||captured.contains(code))captured.add(code);
        else error="Maximum 8 inputs";
        settle=5;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(recording){rawKey(key,GLFW.GLFW_PRESS);return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean keyReleased(int key,int scan,int modifiers){if(recording){rawKey(key,GLFW.GLFW_RELEASE);return true;}return super.keyReleased(key,scan,modifiers);}
    @Override public boolean mouseClicked(double x,double y,int button){if(recording){rawKey(KeyboardBinding.MOUSE+button,GLFW.GLFW_PRESS);return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseReleased(double x,double y,int button){if(recording){rawKey(KeyboardBinding.MOUSE+button,GLFW.GLFW_RELEASE);return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double amount){if(recording){capture(amount>0?KeyboardBinding.WHEEL_UP:KeyboardBinding.WHEEL_DOWN);return true;}return super.mouseScrolled(x,y,amount);}
    @Override public void mouseMoved(double x,double y){
        if(recording&&positionKnown){double dx=x-lastX,dy=y-lastY;
            if(Math.abs(dx)>4&&Math.abs(dx)>=Math.abs(dy))capture(dx<0?KeyboardBinding.LEFT:KeyboardBinding.RIGHT);
            else if(Math.abs(dy)>4)capture(dy<0?KeyboardBinding.UP:KeyboardBinding.DOWN);
        }
        lastX=x;lastY=y;positionKnown=true;
    }
    @Override public void tick(){
        if(recording&&held.isEmpty()&&settle>0&&--settle==0){
            try{binding=new KeyboardBinding(new ArrayList<>(captured));error="";}catch(IllegalArgumentException invalid){error=invalid.getMessage();}
            recording=false;
        }
    }
    private void finish(boolean cancel){if(finished||recording)return;finished=true;ModNetworking.CHANNEL.sendToServer(new BundleSettingsC2SPacket(token,binding.encode(),cancel));minecraft.setScreen(null);}
    @Override public void onClose(){recording=false;finish(true);}
    @Override public void removed(){if(!finished){finished=true;ModNetworking.CHANNEL.sendToServer(new BundleSettingsC2SPacket(token,binding.encode(),true));}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int x,int y,float tick){
        renderBackground(g);super.render(g,x,y,tick);g.drawCenteredString(font,title,width/2,height/2-65,0xffffff);
        g.drawCenteredString(font,binding.label(),width/2,height/2-45,0xff8888);
        g.drawCenteredString(font,Component.translatable(recording?"keyboard.compiledcircuits.recording":"keyboard.compiledcircuits.bind_hint"),width/2,height/2-25,0xffffff);
        if(!error.isEmpty())g.drawCenteredString(font,error,width/2,height/2+58,0xff5555);
    }
}
