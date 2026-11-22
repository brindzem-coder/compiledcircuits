package com.example.compiledcircuits.mixin.client;
import com.example.compiledcircuits.client.CreativeHotbarClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeHotbarScreenMixin {
    @Shadow private Slot destroyItemSlot;
    @Inject(method="renderBg",at=@At("RETURN"))
    private void footer(GuiGraphics graphics,float tick,int mouseX,int mouseY,CallbackInfo ci){CreativeHotbarClient.footer((CreativeModeInventoryScreen)(Object)this,graphics);}
    @Inject(method="slotClicked",at=@At("HEAD"))
    private void trash(Slot slot,int slotId,int button,ClickType type,CallbackInfo ci){if(slot!=null&&slot==destroyItemSlot&&type==ClickType.QUICK_MOVE)CreativeHotbarClient.clearExtra();}
    @Inject(method="hasClickedOutside",at=@At("HEAD"),cancellable=true)
    private void inside(double x,double y,int left,int top,int button,CallbackInfoReturnable<Boolean> ci){if(CreativeHotbarClient.contains((CreativeModeInventoryScreen)(Object)this,x,y))ci.setReturnValue(false);}
}
