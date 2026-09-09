package com.example.compiledcircuits.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.Map;
import java.util.TreeMap;
import java.util.Optional;

public final class CompiledBlockStateCodec {
    private CompiledBlockStateCodec() {}
    public enum Status { EXACT, LEGACY, UNRESOLVED }
    public record DecodeResult(Status status, Optional<BlockState> state, String reason) {}
    public static DecodeResult invalid(String reason) {
        return new DecodeResult(Status.UNRESOLVED, Optional.empty(), reason);
    }
    public static CompiledBlockStateSnapshot capture(BlockState state) {
        Map<String, String> values = new TreeMap<>();
        for (Property<?> property : state.getProperties()) values.put(property.getName(), value(state, property));
        var snapshot = new CompiledBlockStateSnapshot(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), values);
        var result = readAndResolve(write(snapshot), snapshot.blockId());
        if (result.status() != Status.EXACT) throw new IllegalArgumentException(result.reason());
        return snapshot;
    }
    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
    public static CompoundTag write(CompiledBlockStateSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stateDataVersion", 1);
        tag.putString("stateOrigin", "COMPILED");
        CompoundTag state = new CompoundTag(), properties = new CompoundTag();
        state.putString("Name", snapshot.blockId());
        snapshot.properties().forEach(properties::putString);
        state.put("Properties", properties);
        tag.put("compiledBlockState", state);
        return tag;
    }
    public static DecodeResult readAndResolve(CompoundTag tag, String expectedBlockId) {
        if (!tag.contains("stateDataVersion") && !tag.contains("stateOrigin") && !tag.contains("compiledBlockState")) {
            return new DecodeResult(Status.LEGACY, Optional.empty(), "Legacy element");
        }
        if (!tag.contains("stateDataVersion", Tag.TAG_INT) || tag.getInt("stateDataVersion") != 1
                || !tag.contains("stateOrigin", Tag.TAG_STRING)) return invalid("Invalid or unknown state version/origin");
        if (tag.getString("stateOrigin").equals("LEGACY") && !tag.contains("compiledBlockState")) {
            return new DecodeResult(Status.LEGACY, Optional.empty(), "Legacy element");
        }
        if (!tag.getString("stateOrigin").equals("COMPILED") || !tag.contains("compiledBlockState", Tag.TAG_COMPOUND)) {
            return invalid("Invalid state origin or missing snapshot");
        }
        CompoundTag snapshot = tag.getCompound("compiledBlockState");
        if (!snapshot.contains("Name", Tag.TAG_STRING) || !snapshot.contains("Properties", Tag.TAG_COMPOUND)) {
            return invalid("Invalid snapshot Name/Properties types");
        }
        String name = snapshot.getString("Name");
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (name.length() > 256 || !name.equals(expectedBlockId) || id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return invalid("Unknown or mismatched block ID: " + name);
        }
        BlockState state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        if (state.isAir()) return invalid("Air cannot be a compiled element");
        CompoundTag properties = snapshot.getCompound("Properties");
        if (properties.size() > 64 || properties.size() != state.getProperties().size()) return invalid("Incomplete property schema");
        for (String key : properties.getAllKeys()) {
            if (key.length() > 128 || !properties.contains(key, Tag.TAG_STRING) || properties.getString(key).length() > 128) {
                return invalid("Invalid property type/length: " + key);
            }
            Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
            if (property == null) return invalid("Unknown property: " + key);
            Optional<BlockState> applied = apply(state, property, properties.getString(key));
            if (applied.isEmpty()) return invalid("Invalid property value: " + key);
            state = applied.get();
        }
        return new DecodeResult(Status.EXACT, Optional.of(state), "");
    }
    private static <T extends Comparable<T>> Optional<BlockState> apply(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed));
    }
}
