package com.example.compiledcircuits.mixin.client;

import com.example.compiledcircuits.client.KeyboardControlScreen;
import net.minecraft.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class KeyboardMouseMixin {
    @Unique private KeyboardControlScreen screen(long window){var mc=Minecraft.getInstance();return window==mc.getWindow().getWindow()&&mc.screen instanceof KeyboardControlScreen screen?screen:null;}
    @Inject(method="onPress",at=@At("HEAD"),cancellable=true)
    private void press(long window,int button,int action,int modifiers,CallbackInfo ci){var screen=screen(window);if(screen!=null){screen.mouse(button,action);ci.cancel();}}
    @Inject(method="onScroll",at=@At("HEAD"),cancellable=true)
    private void scroll(long window,double x,double y,CallbackInfo ci){var screen=screen(window);if(screen!=null){screen.scroll(y);ci.cancel();}}
    @Inject(method="onMove",at=@At("HEAD"),cancellable=true)
    private void move(long window,double x,double y,CallbackInfo ci){var screen=screen(window);if(screen!=null){screen.move(x,y);ci.cancel();}}
}
