package com.example.compiledcircuits.networking;
import com.example.compiledcircuits.network.*;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
public final class CompiledElementMutationTest {
    private static int checks;
    private static void check(boolean v) { checks++; if (!v) throw new AssertionError("Check " + checks); }
    private static CompiledNetwork network(int id, String dimension) {
        return new CompiledNetwork(id, "test", 0, dimension, Set.of(), Set.of(), Set.of());
    }
    public static void main(String[] args) throws Exception {
        var field = CompiledElementSync.class.getDeclaredField("dirty");
        field.setAccessible(true);
        Set<?> dirty = (Set<?>) field.get(null);
        var data = new NetworkSavedData();
        CompiledElementSync.clear();
        data.addNetwork(network(1,"minecraft:overworld"));
        data.addNetwork(network(2,"minecraft:overworld"));
        check(dirty.equals(Set.of("minecraft:overworld")));
        CompiledElementSync.clear();
        check(data.moveNetwork(1,0)); check(dirty.isEmpty());
        check(!data.removeNetwork(999)); check(dirty.isEmpty());
        check(data.removeNetworks(List.of(1,999)).isEmpty());
        check(data.getNetwork(1)!=null && dirty.isEmpty());
        data.addNetwork(network(3,"minecraft:the_nether"));
        CompiledElementSync.clear();
        check(data.removeNetworks(List.of(1,2,3,3)).size()==3);
        check(dirty.equals(Set.of("minecraft:overworld","minecraft:the_nether")));
        CompiledElementSync.clear();
        data.addNetwork(network(4,"minecraft:overworld"));
        CompiledElementSync.clear();
        data.addNetwork(network(4,"minecraft:the_nether"));
        check(dirty.equals(Set.of("minecraft:overworld","minecraft:the_nether")));
        var saved = data.save(new CompoundTag());
        CompiledElementSync.clear();
        var loaded = NetworkSavedData.load(saved);
        check(loaded.getNetwork(4)!=null && dirty.isEmpty());
        check(loaded.removeNetwork(4));
        check(dirty.equals(Set.of("minecraft:the_nether")));
        check(CompiledElementSync.buildSnapshot(loaded,"minecraft:the_nether",1).get(0).entries().isEmpty());
        CompiledElementSync.clear();
        check(dirty.isEmpty());
        System.out.println("Mutation checks passed: " + checks);
    }
}
