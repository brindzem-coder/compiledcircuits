package com.example.compiledcircuits.network;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class CreativeHotbarGameTests {
    static net.minecraftforge.common.util.FakePlayer player(GameTestHelper h){var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"extra-hotbar"));p.getAbilities().instabuild=true;return p;}
    @GameTest(template="empty",batch="creative_hotbar")
    public static void limitsAndHiddenSlots(GameTestHelper h){
        var p=player(h);var s=CreativeHotbar.state(p);h.assertTrue(s.size()==9,"vanilla minimum");
        for(int i=0;i<50;i++)s.resize(p,1);h.assertTrue(s.size()==38,"38 maximum");
        s.put(p,37,new ItemStack(Items.DIAMOND));s.select(p,37);h.assertTrue(p.getMainHandItem().is(Items.DIAMOND),"last slot is usable in hand");
        s.resize(p,-1);h.assertTrue(s.selected(p)<9&&p.getInventory().getItem(8).isEmpty(),"hiding active slot restores the ninth slot");
        s.resize(p,1);h.assertTrue(s.item(p,37).is(Items.DIAMOND),"hidden contents retained");
        for(int i=0;i<50;i++)s.resize(p,-1);h.assertTrue(s.size()==9,"cannot shrink below nine");h.succeed();
    }
    @GameTest(template="empty",batch="creative_hotbar")
    public static void swappingPreservesOriginalAndEditedNbt(GameTestHelper h){
        var p=player(h);var s=CreativeHotbar.state(p);s.resize(p,1);s.resize(p,1);
        p.getInventory().setItem(8,new ItemStack(Items.EMERALD,7));var item=new ItemStack(Items.STICK);item.getOrCreateTag().putString("ChannelMapping","0-7=8-15");s.put(p,9,item);s.put(p,10,new ItemStack(Items.REDSTONE));
        s.select(p,9);h.assertTrue(p.getMainHandItem().getTag().getString("ChannelMapping").equals("0-7=8-15"),"configured item reaches vanilla hand intact");
        p.getMainHandItem().getOrCreateTag().putString("ChannelMapping","1=2");s.select(p,10);
        h.assertTrue(s.item(p,9).getTag().getString("ChannelMapping").equals("1=2"),"editing active item is saved");
        h.assertTrue(s.item(p,8).is(Items.EMERALD)&&s.item(p,8).getCount()==7,"logical ninth slot preserved while proxy active");
        s.select(p,8);h.assertTrue(p.getMainHandItem().is(Items.EMERALD)&&p.getMainHandItem().getCount()==7,"number nine restores original stack");h.succeed();
    }
    @GameTest(template="empty",batch="creative_hotbar")
    public static void reloadAndLeavingCreative(GameTestHelper h){
        var p=player(h);var s=CreativeHotbar.state(p);s.resize(p,1);p.getInventory().setItem(8,new ItemStack(Items.EMERALD));s.put(p,9,new ItemStack(Items.DIAMOND));s.select(p,9);CreativeHotbar.persist(p);
        CreativeHotbar.forget(p);s=CreativeHotbar.state(p);h.assertTrue(s.selected(p)==9&&s.item(p,8).is(Items.EMERALD),"active proxy and original ninth survive load");
        p.getAbilities().instabuild=false;h.assertTrue(s.reconcile(p)&&p.getInventory().getItem(8).is(Items.EMERALD),"survival restores original inventory");
        h.assertTrue(s.item(p,9).is(Items.DIAMOND),"creative palette retained for return");
        var restored=CreativeHotbar.State.load(s.save());h.assertTrue(restored.size()==10&&restored.item(p,9).is(Items.DIAMOND),"inactive palette roundtrip");h.succeed();
    }
    @GameTest(template="empty",batch="creative_hotbar")
    public static void vanillaSelectionAndInvalidSlots(GameTestHelper h){
        var p=player(h);var s=CreativeHotbar.state(p);s.resize(p,1);p.getInventory().setItem(8,new ItemStack(Items.EMERALD));s.put(p,9,new ItemStack(Items.DIAMOND));s.select(p,9);
        p.getInventory().selected=2;s.reconcile(p);h.assertTrue(p.getInventory().selected==2&&p.getInventory().getItem(8).is(Items.EMERALD),"vanilla slot selection restores proxy without overriding choice");
        s.select(p,38);s.select(p,-1);s.put(p,8,new ItemStack(Items.DIRT));h.assertTrue(s.selected(p)==2&&p.getInventory().getItem(8).is(Items.EMERALD),"invalid operations cannot touch normal slots");
        var tag=new CompoundTag();tag.putInt("Size",1000000);tag.putInt("Active",1000000);h.assertTrue(CreativeHotbar.State.load(tag).size()==38,"persisted size is bounded");h.succeed();
    }
    @GameTest(template="empty",batch="creative_hotbar")
    public static void replacementPlayerRestoresProxy(GameTestHelper h){
        var old=player(h);var next=player(h);var s=CreativeHotbar.state(old);s.resize(old,1);
        old.getInventory().setItem(8,new ItemStack(Items.EMERALD,7));s.put(old,9,new ItemStack(Items.DIAMOND));s.select(old,9);
        old.getMainHandItem().getOrCreateTag().putString("ChannelId","123");next.getInventory().setItem(8,old.getMainHandItem().copy());
        CreativeHotbar.copy(old,next);var copied=CreativeHotbar.state(next);
        h.assertTrue(copied.size()==10&&next.getInventory().getItem(8).is(Items.EMERALD)&&next.getInventory().getItem(8).getCount()==7,"replacement player retains original ninth slot");
        h.assertTrue(copied.item(next,9).getTag().getString("ChannelId").equals("123"),"replacement player retains latest extra-slot edits");h.succeed();
    }

    @GameTest(template="empty",batch="creative_hotbar")
    public static void equalEntityIdsHaveIndependentState(GameTestHelper h){
        var clientLike=player(h);var serverLike=player(h);serverLike.setId(clientLike.getId());
        h.assertTrue(clientLike.equals(serverLike),"fixture reproduces matching entity IDs");
        CreativeHotbar.state(clientLike).resize(clientLike,1);
        h.assertTrue(CreativeHotbar.state(serverLike).size()==9,"entity equality must not share hotbar state across player instances");
        CreativeHotbar.state(clientLike).put(clientLike,9,new ItemStack(Items.DIAMOND));CreativeHotbar.forget(clientLike);
        h.assertTrue(CreativeHotbar.state(serverLike).size()==9,"forgetting one instance preserves the other");h.succeed();
    }

    @GameTest(template="empty",batch="creative_hotbar")
    public static void transferConservesStacksAndTags(GameTestHelper h){
        var stack=new ItemStack(Items.DIAMOND,17);stack.getOrCreateTag().putString("ChannelMapping","0-7");
        var placed=HotbarTransfer.click(ItemStack.EMPTY,stack,0);
        h.assertTrue(placed.cursor().isEmpty()&&placed.slot().getCount()==17&&stack.getCount()==17,"placing consumes cursor result without mutating source");
        var picked=HotbarTransfer.click(placed.slot(),placed.cursor(),0);
        h.assertTrue(picked.slot().isEmpty()&&picked.cursor().getCount()==17,"pickup removes slot contents");
        var half=HotbarTransfer.click(placed.slot(),ItemStack.EMPTY,1);
        h.assertTrue(half.slot().getCount()==8&&half.cursor().getCount()==9,"right-click takes rounded-up half");
        var one=HotbarTransfer.click(ItemStack.EMPTY,half.cursor(),1);
        h.assertTrue(one.slot().getCount()==1&&one.cursor().getCount()==8,"right-click places exactly one");
        var swapped=HotbarTransfer.click(new ItemStack(Items.EMERALD,3),picked.cursor(),0);
        h.assertTrue(swapped.cursor().is(Items.EMERALD)&&swapped.cursor().getCount()==3&&swapped.slot().getTag().getString("ChannelMapping").equals("0-7"),"different items swap with NBT preserved");
        var merged=HotbarTransfer.click(new ItemStack(Items.DIAMOND,60),new ItemStack(Items.DIAMOND,17),0);
        h.assertTrue(merged.slot().getCount()==64&&merged.cursor().getCount()==13,"merge respects stack limit and retains remainder");h.succeed();
    }
    @GameTest(template="empty",batch="creative_hotbar")
    public static void trashClearsVisibleAndHiddenSlots(GameTestHelper h){
        var p=player(h);var s=CreativeHotbar.state(p);for(int i=9;i<38;i++)s.resize(p,1);
        s.put(p,9,new ItemStack(Items.DIAMOND));s.put(p,37,new ItemStack(Items.EMERALD));s.select(p,37);s.resize(p,-1);
        s.clearExtra(p);s.resize(p,1);
        for(int i=9;i<38;i++)h.assertTrue(s.item(p,i).isEmpty(),"trash clears all extra slots including hidden ones");
        h.assertTrue(s.size()==38,"trash preserves configured capacity");h.succeed();
    }

}
