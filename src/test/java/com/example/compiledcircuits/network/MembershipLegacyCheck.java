package com.example.compiledcircuits.network;
import net.minecraft.nbt.*;
import java.nio.file.*;
/** Explicit copied-world migration probe; input files are read only and never replaced. */
public final class MembershipLegacyCheck {
    public static void main(String[] args)throws Exception{
        int checked=0;try(var paths=Files.list(Path.of(args[0]))){for(var file:paths.filter(p->p.toString().endsWith(".dat")).sorted().toList()){
            CompoundTag root;try(var input=Files.newInputStream(file)){root=NbtIo.readCompressed(input);}var tag=root.contains("data")?root.getCompound("data"):root;
            var normal=NetworkSavedData.load(tag);var over=NetworkSavedData.load(tag,new NetworkSavedData(1,1));
            if(normal.getNetworks().size()!=tag.getList("networks",10).size()||normal.getInvalidMembershipRecordCount()!=0)throw new AssertionError("Unexpected isolation in baseline "+file);
            long count=normal.getNetworks().stream().mapToLong(n->n.getElements().size()).sum();long preserved=over.getNetworks().stream().mapToLong(n->n.getElements().size()).sum();
            if(count!=preserved||normal.getNetworks().size()!=over.getNetworks().size())throw new AssertionError("Over-capacity load truncated data");
            var saved=over.save(new CompoundTag());var reload=NetworkSavedData.load(saved,new NetworkSavedData(1,1));
            if(!saved.equals(reload.save(new CompoundTag())))throw new AssertionError("NBT did not round trip");
            for(var n:over.getNetworks())if(over.capacity().count(n.getDimension())>1&&over.capacity().usage(n.getDimension()).state()!=MembershipCapacity.State.OVER_CAPACITY)throw new AssertionError("Missing capacity state");
            System.out.println("MEMBERSHIP_LEGACY file="+file.getFileName()+" networks="+normal.getNetworks().size()+" elements="+count+" isolated=0 overCapacityRoundTrip=PASS");checked++;
        }}if(checked==0)throw new AssertionError("No copied world NBT supplied");
    }
}
