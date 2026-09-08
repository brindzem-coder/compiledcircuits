package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

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
    private final Map<Integer, CompiledCircuitElement> elements;

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
        this(id, name, folderId, dimension, wires, inputs, outputs, Collections.emptyList());
    }

    public CompiledNetwork(int id, String name, int folderId, String dimension,
                           Set<BlockPos> wires, Set<BlockPos> inputs, Set<BlockPos> outputs,
                           Collection<CompiledCircuitElement> elements) {
        this.elements = new LinkedHashMap<>();
        for (CompiledCircuitElement element : elements) {
            if (this.elements.putIfAbsent(element.getId(), element) != null) {
                throw new IllegalArgumentException("Duplicate compiled element ID: " + element.getId());
            }
        }
        this.id = id;
        this.name = name;
        this.folderId = folderId;
        this.dimension = dimension;

        this.wires = new HashSet<>(wires);
        this.inputs = new HashSet<>(inputs);
        this.outputs = new HashSet<>(outputs);

        this.powered = false;
    }

    public Collection<CompiledCircuitElement> getElements() {
        return Collections.unmodifiableCollection(elements.values());
    }

    public CompiledCircuitElement getElement(int elementId) {
        return elements.get(elementId);
    }

    public CompiledCircuitElement getElementAt(BlockPos pos) {
        for (CompiledCircuitElement element : elements.values()) {
            if (element.getPos().equals(pos)) return element;
        }
        return null;
    }

    public boolean hasElementAt(BlockPos pos) {
        return getElementAt(pos) != null;
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

        ListTag elementList = new ListTag();
        for (CompiledCircuitElement element : elements.values()) elementList.add(element.save());
        tag.put("elements", elementList);
        return tag;
    }

    public static CompiledNetwork load(CompoundTag tag) {

        int id = tag.getInt("id");
        String name = tag.getString("name");

        int folderId = tag.contains("folderId") ? tag.getInt("folderId") : 0;

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

        List<CompiledCircuitElement> elements = new ArrayList<>();
        if (tag.contains("elements", Tag.TAG_LIST)) {
            ListTag elementList = tag.getList("elements", Tag.TAG_COMPOUND);
            for (int i = 0; i < elementList.size(); i++) {
                elements.add(CompiledCircuitElement.load(elementList.getCompound(i)));
            }
        } else {
            elements = migrateLegacyElements(wires, inputs, outputs);
        }

        CompiledNetwork network =
                new CompiledNetwork(
                        id,
                        name,
                        folderId,
                        dimension,
                        wires,
                        inputs,
                        outputs,
                        elements
                );

        network.powered =
                tag.getBoolean("powered");

        return network;
    }

    private static List<CompiledCircuitElement> migrateLegacyElements(
            Set<BlockPos> wires,
            Set<BlockPos> inputs,
            Set<BlockPos> outputs
    ) {
        List<LegacyCandidate> candidates = new ArrayList<>();

        for (BlockPos pos : wires) {
            candidates.add(
                    new LegacyCandidate(
                            pos,
                            CircuitElementType.WIRE,
                            "compiledcircuits:basic_wire"
                    )
            );
        }

        for (BlockPos pos : inputs) {
            candidates.add(
                    new LegacyCandidate(
                            pos,
                            CircuitElementType.INPUT,
                            "compiledcircuits:input_endpoint"
                    )
            );
        }

        for (BlockPos pos : outputs) {
            candidates.add(
                    new LegacyCandidate(
                            pos,
                            CircuitElementType.OUTPUT,
                            "compiledcircuits:output_endpoint"
                    )
            );
        }

        candidates.sort(
                Comparator
                        .comparingInt((LegacyCandidate c) -> c.pos().getX())
                        .thenComparingInt(c -> c.pos().getY())
                        .thenComparingInt(c -> c.pos().getZ())
                        .thenComparing(c -> c.type().name())
        );

        List<CompiledCircuitElement> result = new ArrayList<>();

        int nextId = 1;

        for (LegacyCandidate candidate : candidates) {
            result.add(
                    new CompiledCircuitElement(
                            nextId++,
                            candidate.pos(),
                            candidate.type(),
                            candidate.blockId()
                    )
            );
        }

        return result;
    }

    private record LegacyCandidate(
            BlockPos pos,
            CircuitElementType type,
            String blockId
    ) {
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