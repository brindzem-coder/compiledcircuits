package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.networking.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;

/** No Screen: vanilla movement and mouse look remain active throughout camera setup. */
@Mod.EventBusSubscriber(modid=CompiledCircuits.MOD_ID,value=Dist.CLIENT)
public final class KeyboardCameraClient {
    private static UUID token;
    public static void session(KeyboardCameraS2CPacket p){if(p.open())token=p.token();else if(p.token().equals(token))token=null;}
    public static boolean key(int key,int action){
        if(token==null)return false;
        int command=key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER?1:key==GLFW.GLFW_KEY_ESCAPE?0:key==GLFW.GLFW_KEY_DELETE?2:-1;
        if(command<0)return false;
        if(action==GLFW.GLFW_PRESS){
            var mc=Minecraft.getInstance();if(mc.player==null||mc.getConnection()==null){token=null;return true;}
            // Publish the current pose before confirmation on the same ordered connection.
            if(command==1)mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot(),mc.player.onGround()));
            ModNetworking.CHANNEL.sendToServer(new KeyboardCameraC2SPacket(token,command));
        }
        return true;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||token==null)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getConnection()==null){token=null;return;}
        if(mc.screen instanceof KeyboardControlScreen){ModNetworking.CHANNEL.sendToServer(new KeyboardCameraC2SPacket(token,0));token=null;}
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(token==null||mc.screen!=null)return;
        var g=event.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        g.fill(0,h-45,w,h-29,0x99000000);
        var label=Component.translatable("keyboard.compiledcircuits.camera_setup");
        float scale=Math.min(1,(w-12f)/Math.max(1,mc.font.width(label)));
        g.pose().pushPose();g.pose().translate(w/2f,h-41,0);g.pose().scale(scale,scale,1);
        g.drawCenteredString(mc.font,label,0,0,0xffffff);g.pose().popPose();
    }
}
