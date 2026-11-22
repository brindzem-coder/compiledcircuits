package com.example.compiledcircuits.client;

import com.example.compiledcircuits.network.CreativeHotbar;
import com.example.compiledcircuits.registry.ModItems;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in integration check in an isolated client world; never included in the production JAR. */
@Mod.EventBusSubscriber(modid="compiledcircuits",value=Dist.CLIENT)
public final class CreativeHotbarClientCheck {
    private static int ticks,phase,deadline;
    private static int drops;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void button(Minecraft mc,String label){var screen=(CreativeModeInventoryScreen)mc.screen;var b=screen.children().stream().filter(w->w instanceof Button button&&button.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow();b.onPress();}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("HOTBAR_SCREENSHOT: "+message.getString()));}
    private static void clickSlot(Minecraft mc,int index,int button,boolean releaseOutside){
        var screen=(CreativeModeInventoryScreen)mc.screen;var bar=CreativeHotbarClient.layout(screen.width,screen.height);double x=bar.x(index)+bar.cell()/2.0,y=bar.y(index)+bar.cell()/2.0;
        var press=new ScreenEvent.MouseButtonPressed.Pre(screen,x,y,button);MinecraftForge.EVENT_BUS.post(press);if(!press.isCanceled())screen.mouseClicked(x,y,button);
        var release=new ScreenEvent.MouseButtonReleased.Pre(screen,releaseOutside?1:x,releaseOutside?1:y,button);MinecraftForge.EVENT_BUS.post(release);if(!release.isCanceled())screen.mouseReleased(release.getMouseX(),release.getMouseY(),button);
        check(press.isCanceled()&&release.isCanceled(),"complete hotbar gesture must be handled without outside-drop");
    }
    private static int drops(Minecraft mc){return mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());return p.serverLevel().getEntities(net.minecraft.world.entity.EntityType.ITEM,p.getBoundingBox().inflate(128),item->true).size();}).join();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("compiledcircuits.hotbarClientCheck")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(++ticks>6000){System.out.println("HOTBAR_CLIENT_FAIL: timeout phase "+phase);mc.stop();return;}
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        try{
            if(phase==0){
                mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.setGameMode(GameType.CREATIVE);CreativeHotbar.state(p).clearExtra(p);for(int i=0;i<9;i++)p.getInventory().setItem(i,ItemStack.EMPTY);p.getInventory().setItem(8,new ItemStack(Items.EMERALD,7));p.inventoryMenu.broadcastChanges();});phase=1;deadline=ticks+60;
            }else if(phase==1&&ticks>deadline&&mc.player.isCreative()){
                mc.setScreen(new CreativeModeInventoryScreen(mc.player,mc.player.connection.enabledFeatures(),false));
                for(int i=0;i<29;i++)button(mc,"+");phase=2;deadline=ticks+60;
            }else if(phase==2&&ticks>deadline){
                check(CreativeHotbar.state(mc.player).size()==38,"client max size");
                check(mc.getSingleplayerServer().submit(()->CreativeHotbar.state(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())).size()==38).join(),"server max size");
                var screen=(CreativeModeInventoryScreen)mc.screen;var stack=new ItemStack(ModItems.BUNDLE_MAPPING.get());stack.getOrCreateTag().putString("ChannelMapping","0-7=8-15");screen.getMenu().setCarried(stack);
                drops=drops(mc);clickSlot(mc,37,0,false);
                check(screen.getMenu().getCarried().isEmpty(),"placement consumes cursor stack");
                clickSlot(mc,37,0,false);check(CreativeHotbar.state(mc.player).item(mc.player,37).isEmpty()&&screen.getMenu().getCarried().is(ModItems.BUNDLE_MAPPING.get()),"pickup removes item from extra slot");
                clickSlot(mc,36,0,true);check(screen.getMenu().getCarried().isEmpty(),"release outside does not duplicate placed item");
                clickSlot(mc,36,0,false);clickSlot(mc,37,0,false);
                phase=3;deadline=ticks+40;
            }else if(phase==3&&ticks>deadline){
                check(drops(mc)==drops,"moving items must not spawn item entities");
                check(CreativeHotbar.state(mc.player).item(mc.player,36).isEmpty(),"intermediate slot stays empty after move");
                check(CreativeHotbar.state(mc.player).item(mc.player,37).is(ModItems.BUNDLE_MAPPING.get()),"slot survives server synchronization");mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());System.out.println("HOTBAR_BEFORE_CLOSE: "+CreativeHotbar.state(p).save());}).join();shot(mc,"hotbar-38-inventory.png");
                mc.setScreen(null);CreativeHotbarClient.key(new InputEvent.Key(57,0,1,0));CreativeHotbarClient.wheel(new InputEvent.MouseScrollingEvent(-29,false,false,false,0,0));phase=4;deadline=ticks+40;
            }else if(phase==4&&ticks>deadline){
                check(CreativeHotbar.state(mc.player).selected(mc.player)==37,"scroll reaches slot 38");
                check(mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());System.out.println("HOTBAR_SERVER_HAND: selected="+CreativeHotbar.state(p).selected(p)+" vanilla="+p.getInventory().selected+" hand="+p.getMainHandItem().save(new net.minecraft.nbt.CompoundTag())+" stored="+CreativeHotbar.state(p).item(p,37).save(new net.minecraft.nbt.CompoundTag()));return CreativeHotbar.state(p).selected(p)==37&&p.getMainHandItem().is(ModItems.BUNDLE_MAPPING.get())&&p.getMainHandItem().getTag()!=null&&p.getMainHandItem().getTag().getString("ChannelMapping").equals("0-7=8-15");}).join(),"server hand contains configured selected item");
                shot(mc,"hotbar-38-hud.png");
                mc.player.getMainHandItem().getOrCreateTag().putString("ChannelMapping","2=3");
                mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).getMainHandItem().getOrCreateTag().putString("ChannelMapping","2=3"));
                phase=8;deadline=ticks+40;
            }else if(phase==8&&ticks>deadline){
                check(CreativeHotbar.state(mc.player).selected(mc.player)==37&&mc.player.getMainHandItem().getTag().getString("ChannelMapping").equals("2=3"),"editing selected item must not deselect extra slot");
                mc.setScreen(new CreativeModeInventoryScreen(mc.player,mc.player.connection.enabledFeatures(),false));button(mc,"−");phase=5;deadline=ticks+40;
            }else if(phase==5&&ticks>deadline){
                check(CreativeHotbar.state(mc.player).size()==37,"remove hides one slot");button(mc,"+");phase=6;deadline=ticks+40;
            }else if(phase==6&&ticks>deadline){
                check(CreativeHotbar.state(mc.player).item(mc.player,37).is(ModItems.BUNDLE_MAPPING.get()),"hidden slot restored");
                check(mc.player.getInventory().getItem(8).is(Items.EMERALD)&&mc.player.getInventory().getItem(8).getCount()==7,"ninth slot remains intact");
                var screen=(CreativeModeInventoryScreen)mc.screen;
                var select=CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab",net.minecraft.world.item.CreativeModeTab.class);select.setAccessible(true);
                select.invoke(screen,net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(net.minecraft.world.item.CreativeModeTabs.INVENTORY));phase=9;deadline=ticks+20;
            }else if(phase==9&&ticks>deadline){
                var screen=(CreativeModeInventoryScreen)mc.screen;var bar=CreativeHotbarClient.layout(screen.width,screen.height);
                check(screen.getGuiTop()>=28&&screen.getGuiTop()+screen.getYSize()+28<bar.top(),"tabs do not overlap unified hotbar");
                shot(mc,"hotbar-inventory-tab.png");button(mc,"−");
                var field=CreativeModeInventoryScreen.class.getDeclaredField("destroyItemSlot");field.setAccessible(true);var trash=(net.minecraft.world.inventory.Slot)field.get(screen);
                var clicked=CreativeModeInventoryScreen.class.getDeclaredMethod("slotClicked",net.minecraft.world.inventory.Slot.class,int.class,int.class,net.minecraft.world.inventory.ClickType.class);clicked.setAccessible(true);clicked.invoke(screen,trash,trash.index,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE);
                phase=10;deadline=ticks+40;
            }else if(phase==10&&ticks>deadline){
                check(CreativeHotbar.state(mc.player).size()==37,"trash keeps capacity");button(mc,"+");phase=11;deadline=ticks+40;
            }else if(phase==11&&ticks>deadline){
                for(int i=0;i<38;i++)check(CreativeHotbar.state(mc.player).item(mc.player,i).isEmpty(),"trash clears visible and hidden slot "+i);
                check(mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());for(int i=0;i<38;i++)if(!CreativeHotbar.state(p).item(p,i).isEmpty())return false;return true;}).join(),"server also clears all slots");
                check(drops(mc)==drops,"no gesture or clear action drops items");
                System.out.println("HOTBAR_CLIENT_PASS: full press/release gestures, move/pickup, no drops, unified layout, tabs, hidden-slot trash, NBT, server hand");mc.stop();phase=7;
            }
        }catch(Throwable failure){failure.printStackTrace();System.out.println("HOTBAR_CLIENT_FAIL: phase "+phase+" "+failure);mc.stop();phase=7;}
    }
}
