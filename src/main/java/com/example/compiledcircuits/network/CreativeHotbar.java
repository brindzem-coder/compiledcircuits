package com.example.compiledcircuits.network;

import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Extra creative slots use vanilla slot 8 as a reversible hand proxy, preserving vanilla item use. */
public final class CreativeHotbar {
    public static final int MIN=9, MAX=38;
    private static final String TAG="CompiledCircuitsCreativeHotbar";
    // Entity equality uses its numeric ID: client/server players in an integrated server can compare equal.
    private static final Map<Player,State> STATES=Collections.synchronizedMap(new IdentityHashMap<>());
    public static final class State {
        private int size=MIN, active=-1;
        private long revision;
        public long revision(){return revision;}
        public void revision(long value){revision=value;}
        private ItemStack ninth=ItemStack.EMPTY;
        private final List<ItemStack> extra=new ArrayList<>(Collections.nCopies(MAX-MIN,ItemStack.EMPTY));
        public int size(){return size;}
        public boolean hasProxy(){return active>=MIN;}
        public int selected(Player player){return active>=MIN?active:player.getInventory().selected;}
        public ItemStack item(Player player,int slot){
            if(slot<0||slot>=MAX)return ItemStack.EMPTY;
            if(slot==active)return player.getInventory().getItem(8);
            if(slot==8&&active>=MIN)return ninth;
            return slot<MIN?player.getInventory().getItem(slot):extra.get(slot-MIN);
        }
        public void select(Player player,int slot){
            if(slot<0||slot>=size)return;
            if(slot==active&&player.getInventory().selected==8)return;
            restore(player);
            if(slot>=MIN){ninth=player.getInventory().getItem(8);player.getInventory().setItem(8,extra.get(slot-MIN).copy());active=slot;player.getInventory().selected=8;}
            else player.getInventory().selected=slot;
            player.getInventory().setChanged();
        }
        public void restore(Player player){
            if(active<MIN)return;
            extra.set(active-MIN,player.getInventory().getItem(8).copy());player.getInventory().setItem(8,ninth);ninth=ItemStack.EMPTY;active=-1;
            player.getInventory().setChanged();
        }
        public void resize(Player player,int delta){
            int next=Math.max(MIN,Math.min(MAX,size+Integer.signum(delta)));
            if(active>=next)restore(player);
            size=next;
        }
        public void put(Player player,int slot,ItemStack stack){
            if(slot<MIN||slot>=size)return;
            extra.set(slot-MIN,stack.copy());if(active==slot)player.getInventory().setItem(8,stack.copy());
        }
        public void clearExtra(Player player){
            restore(player);for(int i=0;i<extra.size();i++)extra.set(i,ItemStack.EMPTY);
        }
        public boolean reconcile(Player player){
            if(active>=MIN&&(!player.isCreative()||player.getInventory().selected!=8)){restore(player);return true;}
            if(active>=MIN&&!ItemStack.matches(extra.get(active-MIN),player.getInventory().getItem(8))){extra.set(active-MIN,player.getInventory().getItem(8).copy());return true;}
            return false;
        }
        public CompoundTag save(){
            var tag=new CompoundTag();tag.putInt("Size",size);tag.putInt("Active",active);tag.put("Ninth",ninth.save(new CompoundTag()));
            var items=new ListTag();for(int i=0;i<extra.size();i++)if(!extra.get(i).isEmpty()){var entry=extra.get(i).save(new CompoundTag());entry.putInt("Slot",i);items.add(entry);}tag.put("Items",items);return tag;
        }
        public static State load(CompoundTag tag){
            var state=new State();state.size=Math.max(MIN,Math.min(MAX,tag.getInt("Size")));
            state.active=tag.contains("Active")?tag.getInt("Active"):-1;if(state.active<MIN||state.active>=state.size)state.active=-1;
            state.ninth=ItemStack.of(tag.getCompound("Ninth"));
            var items=tag.getList("Items",10);for(int i=0;i<items.size();i++){var entry=items.getCompound(i);int slot=entry.getInt("Slot");if(slot>=0&&slot<MAX-MIN)state.extra.set(slot,ItemStack.of(entry));}return state;
        }
    }
    public static State state(Player player){return STATES.computeIfAbsent(player,p->State.load(p.getPersistentData().getCompound(TAG)));}
    public static void persist(Player player){player.getPersistentData().put(TAG,state(player).save());}
    public static void accept(Player player,CompoundTag tag){STATES.put(player,State.load(tag));}
    public static void copy(Player from,Player to){
        var previous=state(from);accept(to,previous.save());var next=state(to);
        // End respawns can copy a live proxy without a death event.
        if(previous.active>=MIN){next.extra.set(previous.active-MIN,from.getInventory().getItem(8).copy());to.getInventory().setItem(8,previous.ninth.copy());}
        next.active=-1;next.ninth=ItemStack.EMPTY;persist(to);
    }
    public static void forget(Player player){STATES.remove(player);}
    public static void clearServer(){synchronized(STATES){STATES.keySet().removeIf(p->!p.level().isClientSide);}}
}
