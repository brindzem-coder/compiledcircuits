package com.example.compiledcircuits.mixin.client;

import com.example.compiledcircuits.client.*;
import net.minecraft.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardCaptureMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void capture(long window,int key,int scan,int action,int modifiers,CallbackInfo ci){
        var mc=Minecraft.getInstance();if(window!=mc.getWindow().getWindow())return;
        if(mc.screen==null && KeyboardCameraClient.key(key,action)){ci.cancel();return;}
        if(mc.screen instanceof KeyboardControlScreen screen){screen.key(key,action);ci.cancel();}
        else if(mc.screen instanceof KeyboardBindingScreen screen&&screen.recording()){screen.rawKey(key,action);ci.cancel();}
    }
}
