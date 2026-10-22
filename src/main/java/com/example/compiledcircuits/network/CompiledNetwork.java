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

    public record Port(BlockPos pos, net.minecraft.core.Direction side, boolean input) {}
    private final java.util.List<Port> directPorts;
    public java.util.List<Port> directPorts(){return directPorts;}
    private static void collectPorts(CompiledCircuitElement element, java.util.List<Port> result) {
        if (!element.getBlockId().equals("compiledcircuits:network_wire")) return;
        var state=element.resolveState().state().orElse(null); if(state==null)return;
        for(var side:net.minecraft.core.Direction.values()) {
            int face=com.example.compiledcircuits.block.NetworkWireBlock.face(state,side);
            if(face>=2)result.add(new Port(element.getPos(),side,face==2));
        }
    }
    boolean directReady(){return runtimeReady&&inputsAvailable&&!isDamaged()&&!isIntegrityPending()&&!logicInvalid;}
    java.util.Map<String,Boolean> nodeMemory(){return logic==null?java.util.Map.of():logic.nodeMemory();}
    boolean applyDirect(java.util.Map<BlockPos,Boolean> values, java.util.Map<String,Boolean> memory, String error) {
        boolean changed=!outputSignals.equals(values)||!directError.equals(error);
        outputSignals=java.util.Map.copyOf(values);directError=error;powered=values.containsValue(true);
        if(logic!=null && memory!=null)logic.importNodeMemory(memory);
        return changed;
    }
    private String directError="";
    private boolean directGroupAvailable=true;
    void directAvailability(boolean ready){directGroupAvailable=ready;}
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
    private LogicProgram logic;
    private boolean logicPrepared, logicInvalid, hasGateElements;
    private CompoundTag savedLogicMemory = new CompoundTag();
    private LogicProgram.UnstableFeedback feedbackError;
    public String getLogicError() { return !directError.isEmpty()?directError:feedbackError == null ? "" : feedbackError.getMessage(); }
    public BlockPos getLogicErrorPosition() { return feedbackError == null ? null : feedbackError.position; }
    boolean logicMemoryChanged() { return logic != null && logic.memoryChanged(); }
    private Map<BlockPos, Boolean> outputSignals = Map.of();
    private NetworkTruthTable networkTable;
    private String tableStatus = "No saved table";
    NetworkTruthTable networkTable() { return networkTable; }
    public String tableStatus() { return tableStatus; }
    void tableStatus(String status) { tableStatus = status; }
    Map<BlockPos, net.minecraft.world.level.block.state.BlockState> tableStates() {
        var states = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (var e : elements.values()) states.put(e.getPos(), e.resolveState().state().orElseThrow());
        return states;
    }
    void setNetworkTable(NetworkTruthTable table) {
        var states = tableStates(); var replacement = LogicProgram.compile(states);
        if (table != null) {
            validateTable(table, replacement); replacement.installTable(table, states.keySet());
        }
        if (table == null && !LogicProgram.hasLogic(states.values())) replacement = null;
        if (replacement != null && optimized) replacement.optimize(states.keySet());
        if (replacement != null && logic != null) replacement.importNodeMemory(logic.nodeMemory());
        networkTable = table; logic = replacement; logicPrepared = true; logicInvalid = false;
        tableStatus = table == null ? "No saved table" : "Saved table active (local graph)";
    }
    private static void validateTable(NetworkTruthTable table, LogicProgram program) {
        if (!table.graph.equals(program.graphSignature()) || !table.inputs.equals(program.benchmarkInputs())
                || !table.outputs.equals(program.tableOutputs()) || program.hasStatefulMemory())
            throw new IllegalArgumentException("Saved table does not match network topology");
    }
    private boolean optimized;
    public boolean isOptimized() { return optimized; }
    public String enableOptimization() {
        restoreLogic();
        if (logicInvalid) throw new IllegalStateException("Invalid logic");
        optimized = true;
        if (logic == null) return "Passive network already uses its direct OR path.";
        logic.optimize(elementsByPosition.keySet());
        return "Local evaluation steps: " + logic.nodeCount() + " -> " + logic.evaluationNodeCount()
                + "; truth tables: " + logic.truthTableCount() + " (" + logic.truthTableBytes() + " bytes).";
    }
    void prepareLogic(Map<BlockPos, net.minecraft.world.level.block.state.BlockState> states) {
        logic = networkTable != null || LogicProgram.hasLogic(states.values()) ? LogicProgram.compile(states) : null;
        if (logic != null && networkTable != null) {
            try { validateTable(networkTable, logic); logic.installTable(networkTable, states.keySet()); tableStatus = "Saved table active (local graph)"; }
            catch (IllegalArgumentException invalid) { networkTable = null; tableStatus = "Invalid saved table discarded; using graph"; }
        }
        if (logic != null && optimized) logic.optimize(states.keySet());
        logicPrepared = true;
    }
    void restoreLogic() {
        if (logicPrepared) return;
        logicPrepared = true;
        if (!hasGateElements && networkTable == null) return;
        try {
            Map<BlockPos, net.minecraft.world.level.block.state.BlockState> states = new java.util.HashMap<>();
            for (var element : elements.values()) states.put(element.getPos(), element.resolveState().state().orElseThrow(
                    () -> new IllegalArgumentException("Unresolved logic element")));
            prepareLogic(states);
            if (logic != null) logic.restoreMemory(savedLogicMemory);
            savedLogicMemory = new CompoundTag();
        } catch (IllegalArgumentException invalid) {
            logicInvalid = true;
            org.slf4j.LoggerFactory.getLogger(CompiledNetwork.class).warn("Network {} logic unavailable: {}", id, invalid.getMessage());
        }
    }
    boolean hasLogic() { return logic != null || logicInvalid; }
    boolean evaluateLogic(Map<BlockPos, Boolean> values) {
        boolean hadError = feedbackError != null;
        Map<BlockPos, Boolean> next;
        try {
            next = logic == null || logicInvalid ? Map.of() : logic.evaluate(values);
            feedbackError = null;
        } catch (LogicProgram.UnstableFeedback unstable) {
            feedbackError = unstable;
            next = Map.of();
        }
        boolean changed = hadError != (feedbackError != null) || !next.equals(outputSignals); outputSignals = next;
        powered = next.containsValue(true);
        return changed;
    }
    public int getEffectiveSignal(BlockPos output) {
        if (!hasLogic()) return getEffectiveSignal();
        return runtimeReady && inputsAvailable && directGroupAvailable && directError.isEmpty() && !logicInvalid && feedbackError == null && !isIntegrityPending() && !isDamaged()
                && outputSignals.getOrDefault(output, false) ? 15 : 0;
    }
    public int getEffectiveSignal() { return runtimeReady && inputsAvailable && directGroupAvailable && directError.isEmpty() && !logicInvalid && feedbackError == null && !isIntegrityPending() && !isDamaged() && powered ? 15 : 0; }
    public String getRuntimeStatus() {
        if (isDamaged()) return "DAMAGED";
        if (isIntegrityPending()) return "INTEGRITY_PENDING";
        if (!runtimeReady) return "INITIALIZING";
        if (logicInvalid) return "INVALID_LOGIC";
        if (!directError.isEmpty() || feedbackError != null) return "UNSTABLE_FEEDBACK";
        return inputsAvailable && directGroupAvailable ? "READY" : "UNAVAILABLE";
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
        var collectedPorts=new java.util.ArrayList<Port>();
        Set<BlockPos> wirePositions = new HashSet<>(), inputPositions = new HashSet<>(), outputPositions = new HashSet<>();
        for (CompiledCircuitElement element : elements) {
            if (element == null || element.getId() <= 0 || element.getType() == null)
                throw new IllegalArgumentException("Invalid element in network " + id);
            if (this.elements.putIfAbsent(element.getId(), element) != null)
                throw new IllegalArgumentException("Duplicate compiled element ID: " + element.getId());
            CircuitElementType requiredRole = switch (element.getBlockId()) {
                case "compiledcircuits:basic_wire", "compiledcircuits:network_wire" -> CircuitElementType.WIRE;
                case "compiledcircuits:input_endpoint" -> CircuitElementType.INPUT;
                case "compiledcircuits:output_endpoint" -> CircuitElementType.OUTPUT;
                case "compiledcircuits:and_gate", "compiledcircuits:or_gate", "compiledcircuits:not_gate", "compiledcircuits:d_latch",
                        "compiledcircuits:nand_gate", "compiledcircuits:nor_gate", "compiledcircuits:xor_gate", "compiledcircuits:xnor_gate" -> CircuitElementType.GATE;
                case "compiledcircuits:gate_input" -> CircuitElementType.GATE_INPUT;
                case "compiledcircuits:gate_output" -> CircuitElementType.GATE_OUTPUT;
                default -> element.getType();
            };
            if (element.getType() != requiredRole)
                throw new IllegalArgumentException("Block/role mismatch for element " + element.getId());
            hasGateElements |= isLogicRole(element.getType()) || element.getBlockId().equals("compiledcircuits:network_wire");
            collectPorts(element,collectedPorts);
            BlockPos pos = element.getPos().immutable();
            if (elementsByPosition.putIfAbsent(pos, element) != null) throw new IllegalArgumentException("Duplicate compiled position: " + pos);
            switch (element.getType()) {
                case INPUT -> inputPositions.add(pos);
                case OUTPUT -> outputPositions.add(pos);
                default -> wirePositions.add(pos);
            }
        }
        this.directPorts=java.util.List.copyOf(collectedPorts);
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
        private final java.util.List<Port> ports=new java.util.ArrayList<>();
        private boolean sealed;
        private boolean hasGates;
        void add(CompiledCircuitElement element) {
            if (sealed || element.getId() != elements.size() + 1 || positions.containsKey(element.getPos()))
                throw new IllegalArgumentException("Invalid staged element");
            elements.put(element.getId(), element); positions.put(element.getPos(), element);
            hasGates |= isLogicRole(element.getType()) || element.getBlockId().equals("compiledcircuits:network_wire");
            collectPorts(element,ports);
            switch (element.getType()) {
                case INPUT -> inputs.add(element.getPos());
                case OUTPUT -> outputs.add(element.getPos());
                default -> wires.add(element.getPos());
            }
        }
        boolean inputsEmpty() { return inputs.isEmpty() && ports.stream().noneMatch(Port::input); }
        boolean outputsEmpty() { return outputs.isEmpty() && ports.stream().allMatch(Port::input); }
        CompiledNetwork seal(String name, String dimension) {
            if (sealed || inputsEmpty() || outputsEmpty()) throw new IllegalArgumentException("Missing endpoints");
            sealed = true; return new CompiledNetwork(this, name, dimension);
        }
    }
    private CompiledNetwork(Builder builder, String name, String dimension) {
        this.id = 0; this.published = false; this.name = name;
        this.dimension = new net.minecraft.resources.ResourceLocation(dimension).toString();
        this.elements = builder.elements; this.elementsByPosition = builder.positions;
        this.hasGateElements = builder.hasGates;
        this.directPorts=java.util.Collections.unmodifiableList(builder.ports);
        this.wires = Collections.unmodifiableSet(builder.wires);
        this.inputs = Collections.unmodifiableSet(builder.inputs);
        this.outputs = Collections.unmodifiableSet(builder.outputs);
    }

    private static Set<BlockPos> immutablePositions(Set<BlockPos> positions) {
        Set<BlockPos> copy = new HashSet<>();
        for (BlockPos pos : positions) copy.add(pos.immutable());
        return Collections.unmodifiableSet(copy);
    }

    private static boolean isLogicRole(CircuitElementType type) {
        return type == CircuitElementType.GATE || type == CircuitElementType.GATE_INPUT || type == CircuitElementType.GATE_OUTPUT;
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
        tag.putBoolean("optimized", optimized);
        if (networkTable != null) tag.put("networkTable", networkTable.save());
        CompoundTag memory = logic != null && !logicInvalid ? logic.saveMemory() : savedLogicMemory;
        if (!memory.isEmpty()) tag.put("logicMemory", memory.copy());

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
        network.savedLogicMemory = tag.getCompound("logicMemory").copy();
        network.optimized = tag.getBoolean("optimized");
        if (tag.contains("networkTable")) {
            try { network.networkTable = NetworkTruthTable.load(tag.getCompound("networkTable")); network.tableStatus = "Saved table awaiting graph"; }
            catch (IllegalArgumentException invalid) { network.tableStatus = "Invalid saved table discarded; using graph"; }
        }

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
