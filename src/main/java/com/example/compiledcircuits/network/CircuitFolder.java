package com.example.compiledcircuits.network;

import net.minecraft.nbt.CompoundTag;

public class CircuitFolder {

    private final int id;

    private String name;
    private int parentId;

    public CircuitFolder(
            int id,
            String name,
            int parentId
    ) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getParentId() {
        return parentId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }

    public CompoundTag save() {

        CompoundTag tag =
                new CompoundTag();

        tag.putInt("id", id);
        tag.putString("name", name);
        tag.putInt("parentId", parentId);

        return tag;
    }

    public static CircuitFolder load(
            CompoundTag tag
    ) {

        return new CircuitFolder(
                tag.getInt("id"),
                tag.getString("name"),
                tag.getInt("parentId")
        );
    }
}