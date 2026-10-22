package com.example.compiledcircuits.network;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class BlueprintStorage extends SavedData {
    private final Map<UUID,CircuitBlueprint> blueprints=new LinkedHashMap<>();
    public static BlueprintStorage get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(BlueprintStorage::load,BlueprintStorage::new,"compiledcircuits_blueprints");}
    public CircuitBlueprint get(UUID id){return blueprints.get(id);}
    public UUID put(CircuitBlueprint blueprint){
        var saved=blueprint.save();
        for(var entry:blueprints.entrySet())if(entry.getValue().save().equals(saved))return entry.getKey();
        if(blueprints.values().stream().mapToLong(CircuitBlueprint::estimatedBytes).sum()+blueprint.estimatedBytes()>64L*1024*1024)throw new IllegalArgumentException("World blueprint library memory limit reached (64 MiB)");
        if(blueprints.size()>=256)throw new IllegalArgumentException("World blueprint library is full (256)");
        UUID id=UUID.randomUUID();blueprints.put(id,blueprint);setDirty();return id;
    }
    @Override public CompoundTag save(CompoundTag tag){
        var list=new ListTag();blueprints.forEach((id,b)->{var entry=b.save();entry.putUUID("blueprintId",id);list.add(entry);});tag.put("blueprints",list);return tag;
    }
    public static BlueprintStorage load(CompoundTag tag){
        var result=new BlueprintStorage();for(var raw:tag.getList("blueprints",Tag.TAG_COMPOUND)){
            var entry=(CompoundTag)raw;result.blueprints.put(entry.getUUID("blueprintId"),new CircuitBlueprint(entry));
        }return result;
    }
}
