package com.example.compiledcircuits.network;

import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
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

    private boolean needsPersistenceUpgrade;
    public boolean needsPersistenceUpgrade() { return needsPersistenceUpgrade; }
    private int id;
    private boolean published = true, retired;
    boolean isRetired() { return retired; }
    void retire() { retired = true; published = false; runtimeReady = false; }
    void reactivate() { retired = false; published = true; }
    boolean isPublished() { return published; }
    void publish(int id) {
        if (published || id <= 0) throw new IllegalStateException("Invalid publication");
        this.id = id; if (name == null) name = "Network " + id; published = true;
    }

    private String name;

    // Наприклад:
    // minecraft:overworld
    // minecraft:the_nether
    private final String dimension;

    private final Set<BlockPos> wires;
    private final Set<BlockPos> inputs;
    private final Set<BlockPos> outputs;
    private final Map<Integer, CompiledCircuitElement> elements;
    private final Map<BlockPos, CompiledCircuitElement> elementsByPosition;
    DamageLedger damageLedger;
    PersistentIntMap<BrokenCircuitElement> damageRoot(){return brokenElements;}
    private PersistentIntMap<BrokenCircuitElement> brokenElements = new PersistentIntMap<>();
    private boolean savedIntegrityPending;
    boolean savedIntegrityPending() { return savedIntegrityPending; }
    void integrityConfirmed() { savedIntegrityPending = false; }
    private java.util.function.BooleanSupplier integrityGuard = () -> false;
    void integrityGuard(java.util.function.BooleanSupplier guard) { integrityGuard = guard; }
    public boolean isIntegrityPending() { return savedIntegrityPending || integrityGuard.getAsBoolean(); }

    // Runtime state мережі
    private boolean powered;
    // Never persisted: saved input OR is not an authorization to emit power.
    private boolean runtimeReady;
    private boolean inputsAvailable;
    public int getEffectiveSignal() { return runtimeReady && inputsAvailable && !isIntegrityPending() && !isDamaged() && powered ? 15 : 0; }
    public String getRuntimeStatus() {
        if (isDamaged()) return "DAMAGED";
        if (isIntegrityPending()) return "INTEGRITY_PENDING";
        if (!runtimeReady) return "INITIALIZING";
        return inputsAvailable ? "READY" : "UNAVAILABLE";
    }
    void runtimeState(boolean ready, boolean available) { runtimeReady = ready; inputsAvailable = available; }


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
        this(id, name, folderId, dimension, wires, inputs, outputs, migrateLegacyElements(wires, inputs, outputs));
    }

    /** Compatibility constructor: role sets must exactly describe the authoritative elements. */
    public CompiledNetwork(int id, String name, int folderId, String dimension,
                           Set<BlockPos> wires, Set<BlockPos> inputs, Set<BlockPos> outputs,
                           Collection<CompiledCircuitElement> elements) {
        this(id, name, folderId, dimension, elements);
        if (!this.wires.equals(wires) || !this.inputs.equals(inputs) || !this.outputs.equals(outputs)) {
            throw new IllegalArgumentException("Role sets disagree with elements in network " + id);
        }
    }

    public CompiledNetwork(int id, String name, int folderId, String dimension,
                           Collection<CompiledCircuitElement> elements) {
        if (id <= 0) throw new IllegalArgumentException("Invalid network ID: " + id);
        if (dimension == null || net.minecraft.resources.ResourceLocation.tryParse(dimension) == null)
            throw new IllegalArgumentException("Invalid network dimension: " + dimension);
        this.id = id;
        this.name = name;
        this.folderId = folderId;
        this.dimension = net.minecraft.resources.ResourceLocation.tryParse(dimension).toString();
        this.elements = new LinkedHashMap<>();
        this.elementsByPosition = new java.util.HashMap<>();
        Set<BlockPos> wirePositions = new HashSet<>(), inputPositions = new HashSet<>(), outputPositions = new HashSet<>();
        for (CompiledCircuitElement element : elements) {
            if (element == null || element.getId() <= 0 || element.getType() == null)
                throw new IllegalArgumentException("Invalid element in network " + id);
            if (this.elements.putIfAbsent(element.getId(), element) != null)
                throw new IllegalArgumentException("Duplicate compiled element ID: " + element.getId());
            CircuitElementType requiredRole = switch (element.getBlockId()) {
                case "compiledcircuits:basic_wire" -> CircuitElementType.WIRE;
                case "compiledcircuits:input_endpoint" -> CircuitElementType.INPUT;
                case "compiledcircuits:output_endpoint" -> CircuitElementType.OUTPUT;
                default -> element.getType();
            };
            if (element.getType() != requiredRole)
                throw new IllegalArgumentException("Block/role mismatch for element " + element.getId());
            BlockPos pos = element.getPos().immutable();
            if (elementsByPosition.putIfAbsent(pos, element) != null) throw new IllegalArgumentException("Duplicate compiled position: " + pos);
            switch (element.getType()) {
                case INPUT -> inputPositions.add(pos);
                case OUTPUT -> outputPositions.add(pos);
                default -> wirePositions.add(pos);
            }
        }
        this.wires = immutablePositions(wirePositions);
        this.inputs = immutablePositions(inputPositions);
        this.outputs = immutablePositions(outputPositions);
        this.powered = false;
    }

    /** Private job storage: ownership is transferred without an O(N) copy at publication. */
    static final class Builder {
        private final Map<Integer, CompiledCircuitElement> elements = new LinkedHashMap<>();
        private final Map<BlockPos, CompiledCircuitElement> positions = new java.util.HashMap<>();
        private final Set<BlockPos> wires = new HashSet<>(), inputs = new HashSet<>(), outputs = new HashSet<>();
        private boolean sealed;
        void add(CompiledCircuitElement element) {
            if (sealed || element.getId() != elements.size() + 1 || positions.containsKey(element.getPos()))
                throw new IllegalArgumentException("Invalid staged element");
            elements.put(element.getId(), element); positions.put(element.getPos(), element);
            switch (element.getType()) {
                case INPUT -> inputs.add(element.getPos());
                case OUTPUT -> outputs.add(element.getPos());
                default -> wires.add(element.getPos());
            }
        }
        boolean inputsEmpty() { return inputs.isEmpty(); }
        boolean outputsEmpty() { return outputs.isEmpty(); }
        CompiledNetwork seal(String name, String dimension) {
            if (sealed || inputs.isEmpty() || outputs.isEmpty()) throw new IllegalArgumentException("Missing endpoints");
            sealed = true; return new CompiledNetwork(this, name, dimension);
        }
    }
    private CompiledNetwork(Builder builder, String name, String dimension) {
        this.id = 0; this.published = false; this.name = name;
        this.dimension = new net.minecraft.resources.ResourceLocation(dimension).toString();
        this.elements = builder.elements; this.elementsByPosition = builder.positions;
        this.wires = Collections.unmodifiableSet(builder.wires);
        this.inputs = Collections.unmodifiableSet(builder.inputs);
        this.outputs = Collections.unmodifiableSet(builder.outputs);
    }

    private static Set<BlockPos> immutablePositions(Set<BlockPos> positions) {
        Set<BlockPos> copy = new HashSet<>();
        for (BlockPos pos : positions) copy.add(pos.immutable());
        return Collections.unmodifiableSet(copy);
    }

    public Collection<CompiledCircuitElement> getElements() {
        return Collections.unmodifiableCollection(elements.values());
    }

    public CompiledCircuitElement getElement(int elementId) {
        return elements.get(elementId);
    }

    public CompiledCircuitElement getElementAt(BlockPos pos) {
        PerformanceDiagnostics.add("lookup.localElement.indexProbes", 1);
        return elementsByPosition.get(pos);
    }

    public boolean hasElementAt(BlockPos pos) {
        return getElementAt(pos) != null;
    }

    public Collection<BrokenCircuitElement> getBrokenElements() {
        return Collections.unmodifiableCollection(brokenElements.values());
    }

    public BrokenCircuitElement getBrokenElement(int elementId) {
        return brokenElements.get(elementId);
    }

    public boolean isElementBroken(int elementId) {
        return brokenElements.get(elementId) != null;
    }

    public boolean isDamaged() {
        return !brokenElements.isEmpty();
    }

    public boolean markBroken(BrokenCircuitElement broken) {
        if (brokenElements.get(broken.getElementId()) != null) return false;
        brokenElements = brokenElements.put(broken.getElementId(), broken);
        if(damageLedger!=null)damageLedger.element(this,broken.getElementId());return true;
    }

    public boolean markRepaired(int elementId) {
        if (brokenElements.get(elementId) == null) return false;
        brokenElements = brokenElements.remove(elementId);
        if(damageLedger!=null)damageLedger.element(this,elementId);return true;
    }

    public boolean updateBrokenActual(int id,String actual) {
        var old=brokenElements.get(id);if(old==null||old.getActualBlockId().equals(actual))return false;
        brokenElements=brokenElements.put(id,old.withActual(actual));
        if(damageLedger!=null)damageLedger.element(this,id);return true;
    }
    public int getId() {
        return id;
    }

    public int getFolderId() {
        return folderId;
    }

    public void setFolderId(int folderId) {
        this.folderId = folderId;
        if(damageLedger!=null)damageLedger.metadata(this);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        if(damageLedger!=null)damageLedger.metadata(this);
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
        ListTag brokenList = new ListTag();
        for (BrokenCircuitElement broken : brokenElements.values()) brokenList.add(broken.save());
        tag.put("brokenElements", brokenList);
        tag.putBoolean("integrityUnverified", isIntegrityPending());
        return tag;
    }

    public static CompiledNetwork load(CompoundTag tag) {
        // Validate membership before lossy getters can substitute defaults or collapse duplicates.
        if (!tag.contains("id", Tag.TAG_INT) || !tag.contains("dimension", Tag.TAG_STRING))
            throw new IllegalArgumentException("Missing network ID or dimension");
        validatePositionList(tag, "wires");
        validatePositionList(tag, "inputs");
        validatePositionList(tag, "outputs");
        if (tag.contains("elements")) {
            ListTag raw = requireCompoundList(tag, "elements");
            for (int i = 0; i < raw.size(); i++) {
                CompoundTag element = raw.getCompound(i);
                if (!element.contains("id", Tag.TAG_INT) || !element.contains("pos", Tag.TAG_LONG)
                        || !element.contains("type", Tag.TAG_STRING) || !element.contains("blockId", Tag.TAG_STRING))
                    throw new IllegalArgumentException("Incomplete membership element " + i);
                try { CircuitElementType.valueOf(element.getString("type")); }
                catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Unknown membership role at element " + i); }
            }
        }


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

        network.savedIntegrityPending = tag.getBoolean("integrityUnverified");
        ListTag brokenList = tag.getList("brokenElements", Tag.TAG_COMPOUND);
        for (int i = 0; i < brokenList.size(); i++) {
            BrokenCircuitElement broken = BrokenCircuitElement.load(brokenList.getCompound(i));
            network.markBroken(broken);
        }
        network.needsPersistenceUpgrade = !tag.contains("elements", Tag.TAG_LIST)
                || elements.stream().anyMatch(CompiledCircuitElement::needsPersistenceUpgrade);
        for (CompiledCircuitElement element : elements) element.logUnresolved(id);
        return network;
    }

    private static ListTag requireCompoundList(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid membership list: " + key);
        return list;
    }

    private static void validatePositionList(CompoundTag tag, String key) {
        ListTag list = requireCompoundList(tag, key);
        Set<BlockPos> seen = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag pos = list.getCompound(i);
            if (!pos.contains("x", Tag.TAG_INT) || !pos.contains("y", Tag.TAG_INT) || !pos.contains("z", Tag.TAG_INT))
                throw new IllegalArgumentException("Incomplete position in " + key);
            if (!seen.add(new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"))))
                throw new IllegalArgumentException("Duplicate position in " + key);
        }
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