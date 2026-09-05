package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Set;

public class CompiledNetwork {

    private final int id;

    private String name;

    // Наприклад:
    // minecraft:overworld
    // minecraft:the_nether
    private final String dimension;

    private final Set<BlockPos> wires;
    private final Set<BlockPos> inputs;
    private final Set<BlockPos> outputs;

    // Runtime state мережі
    private boolean powered;

    private int folderId;

    public CompiledNetwork(
            int id,
            String name,
            int folderId,
            String dimension,
            Set<BlockPos> wires,
            Set<BlockPos> inputs,
            Set<BlockPos> outputs
    ) {

        this.id = id;
        this.name = name;
        this.folderId = folderId;
        this.dimension = dimension;

        this.wires = new HashSet<>(wires);
        this.inputs = new HashSet<>(inputs);
        this.outputs = new HashSet<>(outputs);

        this.powered = false;
    }

    public int getId() {
        return id;
    }

    public int getFolderId() {
        return folderId;
    }

    public void setFolderId(int folderId) {
        this.folderId = folderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDimension() {
        return dimension;
    }

    public Set<BlockPos> getWires() {
        return wires;
    }

    public Set<BlockPos> getInputs() {
        return inputs;
    }

    public Set<BlockPos> getOutputs() {
        return outputs;
    }

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public CompoundTag save() {

        CompoundTag tag = new CompoundTag();

        tag.putInt("id", id);
        tag.putString("name", name);
        tag.putInt("folderId", folderId);
        tag.putString("dimension", dimension);

        tag.putBoolean("powered", powered);

        tag.put("wires", savePositions(wires));
        tag.put("inputs", savePositions(inputs));
        tag.put("outputs", savePositions(outputs));

        return tag;
    }

    public static CompiledNetwork load(CompoundTag tag) {

        int id = tag.getInt("id");
        String name = tag.getString("name");

        String folder = tag.contains("folder")
                ? tag.getString("folder")
                : "";

        String dimension = tag.getString("dimension");

        Set<BlockPos> wires =
                loadPositions(
                        tag.getList(
                                "wires",
                                Tag.TAG_COMPOUND
                        )
                );

        Set<BlockPos> inputs =
                loadPositions(
                        tag.getList(
                                "inputs",
                                Tag.TAG_COMPOUND
                        )
                );

        Set<BlockPos> outputs =
                loadPositions(
                        tag.getList(
                                "outputs",
                                Tag.TAG_COMPOUND
                        )
                );

        CompiledNetwork network =
                new CompiledNetwork(
                        id,
                        name,
                        folder,
                        dimension,
                        wires,
                        inputs,
                        outputs
                );

        network.powered =
                tag.getBoolean("powered");

        int folderId =
                tag.contains("folderId")
                        ? tag.getInt("folderId")
                        : 0;

        return network;
    }

    private static ListTag savePositions(
            Set<BlockPos> positions
    ) {

        ListTag list = new ListTag();

        for (BlockPos pos : positions) {

            CompoundTag tag = new CompoundTag();

            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());

            list.add(tag);
        }

        return list;
    }

    private static Set<BlockPos> loadPositions(
            ListTag list
    ) {

        Set<BlockPos> result = new HashSet<>();

        for (int i = 0; i < list.size(); i++) {

            CompoundTag tag =
                    list.getCompound(i);

            result.add(
                    new BlockPos(
                            tag.getInt("x"),
                            tag.getInt("y"),
                            tag.getInt("z")
                    )
            );
        }

        return result;
    }
}