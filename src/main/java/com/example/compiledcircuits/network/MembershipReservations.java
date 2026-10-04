package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Conservative reservations from raw evidence, separate from active ownership. */
final class MembershipReservations {
    record Key(String dimension, BlockPos pos) {}
    final Map<Key, Set<String>> claims = new HashMap<>();
    final Map<String, Map<Long, Set<BlockPos>>> chunks = new HashMap<>();
    final Set<Integer> networkIds = new HashSet<>();
    final Map<String,Long> capacity=new HashMap<>();
    final Map<String,Set<BlockPos>> byDimension=new HashMap<>();
    boolean unknown;

    void add(CompoundTag record){var builder=new RecordBuilder(record);while(builder.step()){};}
    RecordBuilder prepare(CompoundTag record){return new RecordBuilder(record);}
    /** The same conservative parser is used synchronously on load and incrementally for admin cleanup. */
    final class RecordBuilder {
        final CompoundTag raw;final String recordId,dimension;
        final Set<BlockPos> positions=new HashSet<>();
        Iterator<Tag> values=Collections.emptyIterator();Iterator<BlockPos> publish;
        int field;boolean complete,done;long legacyCount,elementCount;
        RecordBuilder(CompoundTag record){
            raw=record.getCompound("raw");recordId=record.getString("recordId");
            var dim=raw.contains("dimension",Tag.TAG_STRING)?ResourceLocation.tryParse(raw.getString("dimension")):null;
            dimension=dim==null?null:dim.toString();complete=dim!=null;
            if(raw.contains("id",Tag.TAG_INT)&&raw.getInt("id")>0)networkIds.add(raw.getInt("id"));
        }
        boolean step(){
            if(done)return false;
            if(values.hasNext()){
                var value=(CompoundTag)values.next();
                if(field==4){if(value.contains("pos",Tag.TAG_LONG))positions.add(BlockPos.of(value.getLong("pos")));else complete=false;}
                else if(value.contains("x",Tag.TAG_INT)&&value.contains("y",Tag.TAG_INT)&&value.contains("z",Tag.TAG_INT))positions.add(new BlockPos(value.getInt("x"),value.getInt("y"),value.getInt("z")));
                else complete=false;
                return true;
            }
            if(field<4){
                String name=List.of("wires","inputs","outputs","elements").get(field++);var value=raw.get(name);
                if(field==4&&value==null)return true;
                if(!(value instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND){complete=false;return true;}
                if(field==4)elementCount=list.size();else legacyCount=Math.addExact(legacyCount,list.size());
                values=list.iterator();return true;
            }
            if(publish==null){publish=positions.iterator();return true;}
            if(dimension!=null&&publish.hasNext()){
                var pos=publish.next();claims.computeIfAbsent(new Key(dimension,pos),k->new TreeSet<>()).add(recordId);
                chunks.computeIfAbsent(dimension,k->new HashMap<>()).computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(pos),k->new HashSet<>()).add(pos);
                byDimension.computeIfAbsent(dimension,k->new HashSet<>()).add(pos);return true;
            }
            if(dimension!=null)capacity.merge(dimension,Math.max(positions.size(),Math.max(legacyCount,elementCount)),Math::addExact);
            unknown|=!complete;done=true;return false;
        }
    }

    Set<String> at(String dimension, BlockPos pos) {
        return Collections.unmodifiableSet(claims.getOrDefault(new Key(dimension, pos), Set.of()));
    }
    Set<BlockPos> positions(String dimension) {
        Set<BlockPos> result = new TreeSet<>();
        for (Key key : claims.keySet()) if (key.dimension.equals(dimension)) result.add(key.pos);
        return Collections.unmodifiableSet(result);
    }
}
