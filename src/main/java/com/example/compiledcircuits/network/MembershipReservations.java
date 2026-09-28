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
    boolean unknown;

    void add(CompoundTag record) {
        CompoundTag raw = record.getCompound("raw");
        String recordId = record.getString("recordId");
        if (raw.contains("id", Tag.TAG_INT) && raw.getInt("id") > 0) networkIds.add(raw.getInt("id"));
        ResourceLocation dimension = raw.contains("dimension", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(raw.getString("dimension")) : null;
        Set<BlockPos> positions = new HashSet<>();
        boolean complete = dimension != null;
        // Union both representations: disagreement must never free either claimed position.
        for (String field : List.of("wires", "inputs", "outputs")) {
            Tag value = raw.get(field);
            if (!(value instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
                complete = false; continue;
            }
            for (Tag tag : list) {
                CompoundTag position = (CompoundTag) tag;
                if (position.contains("x", Tag.TAG_INT) && position.contains("y", Tag.TAG_INT) && position.contains("z", Tag.TAG_INT))
                    positions.add(new BlockPos(position.getInt("x"), position.getInt("y"), position.getInt("z")));
                else complete = false;
            }
        }
        if (raw.contains("elements")) {
            Tag value = raw.get("elements");
            if (!(value instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) complete = false;
            else for (Tag tag : list) {
                CompoundTag element = (CompoundTag) tag;
                if (element.contains("pos", Tag.TAG_LONG)) positions.add(BlockPos.of(element.getLong("pos")));
                else complete = false;
            }
        }
        if (dimension != null) for (BlockPos pos : positions) {
            claims.computeIfAbsent(new Key(dimension.toString(), pos), key -> new TreeSet<>()).add(recordId);
            chunks.computeIfAbsent(dimension.toString(), key -> new HashMap<>())
                    .computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(pos), key -> new HashSet<>()).add(pos);
        }
        unknown |= !complete;
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
