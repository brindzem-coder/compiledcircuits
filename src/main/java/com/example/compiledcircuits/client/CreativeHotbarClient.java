package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.network.CreativeHotbar;
import com.example.compiledcircuits.network.HotbarTransfer;
import com.example.compiledcircuits.mixin.client.*;
import com.example.compiledcircuits.networking.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=CompiledCircuits.MOD_ID,value=Dist.CLIENT)
public final class CreativeHotbarClient {
    private static Button plus,minus;
    private static int resume=-1;
    private static int capturedButton=-1;
    private static double scroll;
    private static net.minecraft.world.entity.player.Player owner;
    private static long requestedRevision;
    private static void session(){var p=Minecraft.getInstance().player;if(p!=owner){if(owner!=null)CreativeHotbar.forget(owner);owner=p;requestedRevision=0;resume=-1;scroll=0;}}
    private static boolean enabled(){var p=Minecraft.getInstance().player;return p!=null&&p.isCreative();}
    private static void send(int action,int value,ItemStack stack){session();ModNetworking.CHANNEL.sendToServer(new CreativeHotbarC2SPacket(action,value,stack,++requestedRevision));}
    private static void select(int index){var p=Minecraft.getInstance().player;if(p==null)return;CreativeHotbar.state(p).select(p,index);send(CreativeHotbarC2SPacket.SELECT,index,ItemStack.EMPTY);}
    public static void accept(CreativeHotbarS2CPacket packet){
        var p=Minecraft.getInstance().player;if(p==null||packet.state()==null)return;
        session();if(packet.revision()<requestedRevision)return;
        boolean hadProxy=CreativeHotbar.state(p).hasProxy();
        CreativeHotbar.accept(p,packet.state());
        // Ordinary inventory slots are synchronized by vanilla; an older palette reply must not restore a cleared stack.
        if(hadProxy||CreativeHotbar.state(p).hasProxy()){
            p.getInventory().setItem(8,packet.ninth());if(packet.selected()<9)p.getInventory().selected=packet.selected();
        }
    }
    @SubscribeEvent public static void opening(ScreenEvent.Opening e){
        if(!enabled()||!(e.getNewScreen() instanceof AbstractContainerScreen<?>))return;
        var p=Minecraft.getInstance().player;var state=CreativeHotbar.state(p);
        if(state.selected(p)>=9){resume=state.selected(p);state.restore(p);send(CreativeHotbarC2SPacket.RESTORE,0,ItemStack.EMPTY);}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;session();var mc=Minecraft.getInstance();
        if(mc.player==null){resume=-1;scroll=0;plus=null;minus=null;return;}
        if(!enabled()){CreativeHotbar.state(mc.player).reconcile(mc.player);resume=-1;return;}
        var state=CreativeHotbar.state(mc.player);
        if(state.hasProxy()&&mc.player.getInventory().selected!=8){
            state.restore(mc.player);send(CreativeHotbarC2SPacket.RESTORE,0,ItemStack.EMPTY);
        }
        if(resume>=9&&mc.screen==null){int target=resume;resume=-1;if(target<state.size())select(target);}
    }
    @SubscribeEvent public static void key(InputEvent.Key e){
        var mc=Minecraft.getInstance();if(!enabled()||mc.screen!=null||e.getAction()!=1)return;
        var state=CreativeHotbar.state(mc.player);if(state.size()==9)return;
        for(int i=0;i<9;i++)if(mc.options.keyHotbarSlots[i].matches(e.getKey(),e.getScanCode())){select(i);break;}
    }
    @SubscribeEvent public static void wheel(InputEvent.MouseScrollingEvent e){
        var mc=Minecraft.getInstance();if(!enabled()||mc.screen!=null||CreativeHotbar.state(mc.player).size()==9)return;
        e.setCanceled(true);scroll+=e.getScrollDelta();int steps=(int)scroll;if(steps==0)return;scroll-=steps;
        var state=CreativeHotbar.state(mc.player);select(Math.floorMod(state.selected(mc.player)-steps,state.size()));
    }
    public static CreativeHotbarLayout layout(int width,int height){return CreativeHotbarLayout.of(width,height,CreativeHotbar.state(Minecraft.getInstance().player).size());}
    public static boolean contains(CreativeModeInventoryScreen screen,double x,double y){return enabled()&&layout(screen.width,screen.height).hit(x,y)>=0;}
    private static void resize(int delta){var p=Minecraft.getInstance().player;CreativeHotbar.state(p).resize(p,delta);send(CreativeHotbarC2SPacket.RESIZE,delta,ItemStack.EMPTY);}
    public static void clearExtra(){if(!enabled())return;var p=Minecraft.getInstance().player;CreativeHotbar.state(p).clearExtra(p);send(CreativeHotbarC2SPacket.CLEAR,0,ItemStack.EMPTY);}
    private static void resetGesture(CreativeModeInventoryScreen screen){
        var access=(HotbarScreenAccessor)screen;access.compiledcircuits$setQuickCrafting(false);access.compiledcircuits$quickCraftSlots().clear();
        access.compiledcircuits$setSkipRelease(false);access.compiledcircuits$setDoubleClick(false);access.compiledcircuits$setLastClickSlot(null);
    }
    private static void arrange(CreativeModeInventoryScreen screen){
        var bar=layout(screen.width,screen.height);
        int top=Math.min((screen.height-screen.getYSize())/2,bar.top()-screen.getYSize()-32);
        int delta=top-screen.getGuiTop();
        if(delta!=0){((HotbarScreenAccessor)screen).compiledcircuits$setTop(top);for(var child:screen.children())if(child instanceof AbstractWidget widget)widget.setY(widget.getY()+delta);}
        // Forge page arrows normally sit 50px above the window; move them to its sides on compact layouts.
        for(var child:screen.children())if(child instanceof AbstractWidget widget&&widget!=plus&&widget!=minus&&widget.getY()<top-28){
            widget.setX(widget.getX()<screen.width/2?screen.getGuiLeft()-24:screen.getGuiLeft()+screen.getXSize()+2);widget.setY(top);
        }
        var slots=screen.getMenu().slots;
        int first=screen.isInventoryOpen()?36:slots.size()-9;
        for(int i=first;i<first+9&&i<slots.size();i++)if(i>=0){var slot=(HotbarSlotAccessor)slots.get(i);slot.compiledcircuits$setX(-1000);slot.compiledcircuits$setY(-1000);}
        if(plus!=null){plus.setX(screen.getGuiLeft()+9);plus.setY(top+screen.getYSize()-23);minus.setX(screen.getGuiLeft()+35);minus.setY(plus.getY());plus.active=bar.count()<38;minus.active=bar.count()>9;}
    }
    public static void footer(CreativeModeInventoryScreen screen,GuiGraphics g){
        if(!enabled())return;
        // Cover only the old nine-cell strip, leaving the trash slot and all tabs accessible.
        g.fill(screen.getGuiLeft()+7,screen.getGuiTop()+110,screen.getGuiLeft()+171,screen.getGuiTop()+132,0xffc6c6c6);
    }
    @SubscribeEvent public static void init(ScreenEvent.Init.Post e){
        if(!enabled()||!(e.getScreen() instanceof CreativeModeInventoryScreen screen))return;
        capturedButton=-1;
        plus=Button.builder(Component.literal("+"),b->resize(1)).bounds(0,0,22,20).tooltip(Tooltip.create(Component.translatable("hotbar.compiledcircuits.add"))).build();
        minus=Button.builder(Component.literal("−"),b->resize(-1)).bounds(0,0,22,20).tooltip(Tooltip.create(Component.translatable("hotbar.compiledcircuits.remove"))).build();
        e.addListener(plus);e.addListener(minus);arrange(screen);
    }
    @SubscribeEvent public static void beforeRender(ScreenEvent.Render.Pre e){if(enabled()&&e.getScreen() instanceof CreativeModeInventoryScreen screen)arrange(screen);}
    @SubscribeEvent public static void renderInventory(ScreenEvent.Render.Post e){
        if(!enabled()||!(e.getScreen() instanceof CreativeModeInventoryScreen screen))return;
        var mc=Minecraft.getInstance();var state=CreativeHotbar.state(mc.player);var bar=layout(screen.width,screen.height);var g=e.getGuiGraphics();
        g.drawString(mc.font,state.size()+" / 38",screen.getGuiLeft()+65,screen.getGuiTop()+screen.getYSize()-17,0xff303030,false);
        for(int i=0;i<state.size();i++)slot(g,bar.x(i),bar.y(i),bar.cell(),false,state.item(mc.player,i));
        int hovered=bar.hit(e.getMouseX(),e.getMouseY());
        if(hovered>=0){
            int x=bar.x(hovered),y=bar.y(hovered);g.fill(x+1,y+1,x+bar.cell()-1,y+bar.cell()-1,0x55ffffff);
            var stack=state.item(mc.player,hovered);
            if(screen.getMenu().getCarried().isEmpty()){
                if(stack.isEmpty())g.renderTooltip(mc.font,Component.translatable("hotbar.compiledcircuits.empty",hovered+1),e.getMouseX(),e.getMouseY());
                else g.renderTooltip(mc.font,stack,e.getMouseX(),e.getMouseY());
            }
        }
        var carried=screen.getMenu().getCarried();
        if(!carried.isEmpty()&&hovered>=0){
            g.pose().pushPose();g.pose().translate(0,0,400);g.renderItem(carried,e.getMouseX()-8,e.getMouseY()-8);g.renderItemDecorations(mc.font,carried,e.getMouseX()-8,e.getMouseY()-8);g.pose().popPose();
        }
    }
    private static void transfer(CreativeModeInventoryScreen screen,int index,int button){
        var p=Minecraft.getInstance().player;var state=CreativeHotbar.state(p);
        var result=HotbarTransfer.click(state.item(p,index),screen.getMenu().getCarried(),button);
        if(index<9)p.getInventory().setItem(index,result.slot());else state.put(p,index,result.slot());
        screen.getMenu().setCarried(result.cursor());
        send(CreativeHotbarC2SPacket.PUT,index,result.slot());
    }
    @SubscribeEvent public static void click(ScreenEvent.MouseButtonPressed.Pre e){
        if(!enabled()||!(e.getScreen() instanceof CreativeModeInventoryScreen screen)||e.getButton()>1)return;
        int index=layout(screen.width,screen.height).hit(e.getMouseX(),e.getMouseY());
        boolean add=plus!=null&&plus.isMouseOver(e.getMouseX(),e.getMouseY());boolean remove=minus!=null&&minus.isMouseOver(e.getMouseX(),e.getMouseY());
        if(index<0&&!add&&!remove)return;
        e.setCanceled(true);capturedButton=e.getButton();resetGesture(screen);
        if(index>=0)transfer(screen,index,e.getButton());
        else if(e.getButton()==0){if(add&&plus.active)resize(1);if(remove&&minus.active)resize(-1);}
    }
    @SubscribeEvent public static void release(ScreenEvent.MouseButtonReleased.Pre e){
        if(!enabled()||!(e.getScreen() instanceof CreativeModeInventoryScreen screen))return;
        int index=layout(screen.width,screen.height).hit(e.getMouseX(),e.getMouseY());
        if(capturedButton==e.getButton()){e.setCanceled(true);capturedButton=-1;resetGesture(screen);return;}
        if(index>=0&&e.getButton()<=1){e.setCanceled(true);resetGesture(screen);if(!screen.getMenu().getCarried().isEmpty())transfer(screen,index,e.getButton());}
    }
    @SubscribeEvent public static void drag(ScreenEvent.MouseDragged.Pre e){if(enabled()&&e.getScreen() instanceof CreativeModeInventoryScreen screen&&(capturedButton>=0||contains(screen,e.getMouseX(),e.getMouseY())))e.setCanceled(true);}
    private static void slot(GuiGraphics g,int x,int y,int size,boolean selected,ItemStack stack){
        int border=selected?0x80ffd566:0x80252525;
        // Keep border and interior separate so overlapping layers do not increase opacity.
        g.fill(x,y,x+size,y+1,border);g.fill(x,y+size-1,x+size,y+size,border);
        g.fill(x,y+1,x+1,y+size-1,border);g.fill(x+size-1,y+1,x+size,y+size-1,border);
        g.fill(x+1,y+1,x+size-1,y+size-1,0x80777777);
        float scale=Math.min(1,(size-2)/16f),inset=(size-16*scale)/2;
        g.pose().pushPose();g.pose().translate(x+inset,y+inset,0);g.pose().scale(scale,scale,1);
        g.renderItem(stack,0,0);g.renderItemDecorations(Minecraft.getInstance().font,stack,0,0);g.pose().popPose();
    }
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Pre e){
        var mc=Minecraft.getInstance();if(!enabled()||mc.options.hideGui)return;var state=CreativeHotbar.state(mc.player);
        if(e.getOverlay().id().equals(VanillaGuiOverlay.ITEM_NAME.id())){e.setCanceled(true);return;}
        if(!e.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id()))return;e.setCanceled(true);
        if(mc.screen instanceof CreativeModeInventoryScreen)return;
        int width=e.getWindow().getGuiScaledWidth(),height=e.getWindow().getGuiScaledHeight();var bar=layout(width,height);var g=e.getGuiGraphics();
        for(int i=0;i<state.size();i++)slot(g,bar.x(i),bar.y(i),bar.cell(),i==state.selected(mc.player),state.item(mc.player,i));
        var selected=state.item(mc.player,state.selected(mc.player));
        if(mc.screen==null&&!selected.isEmpty())g.drawCenteredString(mc.font,selected.getHoverName(),width/2,bar.top()-12,0xffffff);
        var offhand=mc.player.getOffhandItem();if(!offhand.isEmpty())slot(g,width-bar.cell()-2,bar.top()-bar.cell()-2,bar.cell(),false,offhand);
    }
}
