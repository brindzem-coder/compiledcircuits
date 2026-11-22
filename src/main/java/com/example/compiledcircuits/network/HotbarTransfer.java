package com.example.compiledcircuits.network;

import net.minecraft.world.item.ItemStack;

/** Ordinary pickup/place/merge/swap semantics; neither input is mutated or cloned into two destinations. */
public record HotbarTransfer(ItemStack slot,ItemStack cursor) {
    public static HotbarTransfer click(ItemStack slot,ItemStack cursor,int button){
        var target=slot.copy();var held=cursor.copy();
        if(button!=0&&button!=1)return new HotbarTransfer(target,held);
        if(held.isEmpty()){
            if(!target.isEmpty())held=target.split(button==0?target.getCount():(target.getCount()+1)/2);
        }else if(target.isEmpty()){
            target=held.split(button==0?Math.min(held.getCount(),held.getMaxStackSize()):1);
        }else if(ItemStack.isSameItemSameTags(target,held)){
            int count=Math.min(button==0?held.getCount():1,Math.max(0,target.getMaxStackSize()-target.getCount()));target.grow(count);held.shrink(count);
        }else if(held.getCount()<=held.getMaxStackSize()){
            var previous=target;target=held;held=previous;
        }
        return new HotbarTransfer(target,held);
    }
}
