package com.example.compiledcircuits.client;

import com.example.compiledcircuits.network.KeyboardBinding;
import com.example.compiledcircuits.networking.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Transparent screen suppresses gameplay; raw callbacks retain all keys and unbounded mouse deltas. */
public final class KeyboardControlScreen extends Screen {
    private final UUID token;
    private final Set<Integer> held=new HashSet<>();
    private final Map<Integer,Integer> pulses=new HashMap<>();
    private int sequence,ticks;
    private boolean finished,positionKnown;
    private double lastX,lastY;
    private Set<Integer> sent=Set.of();
    private KeyboardControlScreen(UUID token){super(Component.translatable("keyboard.compiledcircuits.title"));this.token=token;}
    public static void session(KeyboardSessionS2CPacket packet){
        var mc=Minecraft.getInstance();
        if(packet.open())mc.setScreen(new KeyboardControlScreen(packet.token()));
        else if(mc.screen instanceof KeyboardControlScreen screen&&screen.token.equals(packet.token())){screen.finished=true;mc.setScreen(null);}
    }
    @Override protected void init(){
        KeyMapping.releaseAll();positionKnown=false;
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(),GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_DISABLED);
    }
    private boolean looking(){return held.contains(GLFW.GLFW_KEY_F1)&&(held.contains(GLFW.GLFW_KEY_LEFT_CONTROL)||held.contains(GLFW.GLFW_KEY_RIGHT_CONTROL));}
    public void key(int key,int action){
        if(key==GLFW.GLFW_KEY_ESCAPE&&action==GLFW.GLFW_PRESS){onClose();return;}
        if(!KeyboardBinding.valid(key)||key==256)return;
        if(action==GLFW.GLFW_RELEASE)held.remove(key);else if(action==GLFW.GLFW_PRESS&&held.size()<24)held.add(key);
        publish(false);
    }
    public void mouse(int button,int action){key(KeyboardBinding.MOUSE+button,action);}
    public void scroll(double amount){if(amount!=0)pulse(amount>0?KeyboardBinding.WHEEL_UP:KeyboardBinding.WHEEL_DOWN);}
    private void pulse(int code){pulses.put(code,ticks+2);publish(false);}
    public void move(double x,double y){
        if(!positionKnown){lastX=x;lastY=y;positionKnown=true;return;}
        double dx=x-lastX,dy=y-lastY;lastX=x;lastY=y;
        if(looking()){
            if(minecraft.player!=null){double f=minecraft.options.sensitivity().get()*.6+.2;double scale=f*f*f*8;
                minecraft.player.turn(dx*scale,dy*scale*(minecraft.options.invertYMouse().get()?-1:1));}
        } else {
            if(Math.abs(dx)>=1)pulses.put(dx<0?KeyboardBinding.LEFT:KeyboardBinding.RIGHT,ticks+2);
            if(Math.abs(dy)>=1)pulses.put(dy<0?KeyboardBinding.UP:KeyboardBinding.DOWN,ticks+2);
            publish(false);
        }
    }
    private void publish(boolean heartbeat){
        if(finished||minecraft.getConnection()==null)return;
        Set<Integer> state=new HashSet<>();if(!looking()){state.addAll(held);state.addAll(pulses.keySet());}
        if(heartbeat||!state.equals(sent)){sent=Set.copyOf(state);ModNetworking.CHANNEL.sendToServer(new KeyboardInputC2SPacket(token,sequence++,false,sent));}
    }
    @Override public void tick(){
        if(!minecraft.isWindowActive()||minecraft.player==null||!minecraft.player.isAlive()){onClose();return;}
        ticks++;pulses.entrySet().removeIf(e->e.getValue()<=ticks);publish(ticks%20==0);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){key(key,GLFW.GLFW_PRESS);return true;}
    @Override public boolean keyReleased(int key,int scan,int modifiers){key(key,GLFW.GLFW_RELEASE);return true;}
    @Override public boolean charTyped(char c,int modifiers){return true;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(null);}
    @Override public void removed(){
        if(!finished&&minecraft.getConnection()!=null)ModNetworking.CHANNEL.sendToServer(new KeyboardInputC2SPacket(token,sequence++,true,Set.of()));
        finished=true;held.clear();pulses.clear();KeyMapping.releaseAll();
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(),GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_NORMAL);
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float tick){
        graphics.fill(0,height-31,width,height,0x99000000);
        graphics.drawCenteredString(font,Component.translatable("keyboard.compiledcircuits.controls"),width/2,height-23,0xffffff);
        if(looking())graphics.drawCenteredString(font,Component.translatable("keyboard.compiledcircuits.looking"),width/2,height-12,0xffaaaa);
    }
}
