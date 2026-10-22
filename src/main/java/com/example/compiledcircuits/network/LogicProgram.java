package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/**
 * Compiled signal graph with bounded feedback settling. Structural discovery is not electrical connectivity:
 * passive components are buses; each port is an independent directed node.
 * Evaluation reads no world state and publishes only a complete result.
 */
public final class LogicProgram {
    // A single atomic evaluation must stay bounded, even with deeply nested gates.
    public static final int MAX_ELEMENTS = 8192;
    private enum Op { OR, AND, NOT, NAND, NOR, XOR, XNOR, LATCH }
    private static final class Node {
        final Op op;
        final BlockPos position;
        final Set<Integer> parents = new LinkedHashSet<>(), children = new LinkedHashSet<>();
        int data = -1, enable = -1;
        Node(Op op, BlockPos position) { this.op = op; this.position = position; }
    }
    private final Op[] operations;
    private final int[][] parents;
    private final int[][] children;
    private final int[] component;
    private final int[] dataSources, enableSources;
    private final int[][] groups;
    private int[][] executionGroups;
    private record WholeTable(NetworkTruthTable table, int[] nodes, int[] inputNodes, int[][] incoming, int[] outputNodes) {}
    private final Map<Integer, WholeTable> wholeTables = new HashMap<>();
    private final List<WholeTable> installedTables = new ArrayList<>();
    private Set<BlockPos> optimizationPositions = Set.of();
    String graphSignature() { return signature; }
    List<BlockPos> tableOutputs() { return outputs.keySet().stream().sorted().toList(); }
    long fullWork() { long work = 0; for (int[] p : parents) work += 1 + p.length; return work; }
    /** Contract complete network regions, then sort execution units without changing original IDs. */
    void installTable(NetworkTruthTable table, Set<BlockPos> region) {
        var members = new HashSet<Integer>();
        for (int i = 0; i < positions.length; i++) if (region.contains(positions[i])) members.add(i);
        if (members.isEmpty()) throw new IllegalArgumentException("Empty table region");
        for (int id : members) if (operations[id] == Op.LATCH || groups[component[id]].length > 1)
            throw new IllegalArgumentException("Table suspended: feedback crosses this network");
        int[] ins = table.inputs.stream().mapToInt(p -> Objects.requireNonNull(inputs.get(p), "Missing table input")).toArray();
        int[] outs = table.outputs.stream().mapToInt(p -> Objects.requireNonNull(outputs.get(p), "Missing table output")).toArray();
        int[][] incoming = new int[ins.length][];
        for (int i = 0; i < ins.length; i++) incoming[i] = Arrays.stream(parents[ins[i]]).filter(p -> !members.contains(p)).toArray();
        var unit = new WholeTable(table, members.stream().mapToInt(Integer::intValue).sorted().toArray(), ins, incoming, outs);
        var proposed = new ArrayList<>(installedTables); proposed.add(unit);
        var units = new ArrayList<int[]>(); var macros = new HashMap<Integer, WholeTable>();
        int[] owner = new int[operations.length]; Arrays.fill(owner, -1);
        for (var t : proposed) {
            int index = units.size(); units.add(t.nodes); macros.put(t.nodes[0], t);
            for (int id : t.nodes) { if (owner[id] != -1) throw new IllegalArgumentException("Overlapping tables"); owner[id] = index; }
        }
        for (int[] group : groups) if (owner[group[0]] == -1) {
            int index = units.size(); units.add(group); for (int id : group) owner[id] = index;
        }
        List<Set<Integer>> edges = new ArrayList<>(); for (int i = 0; i < units.size(); i++) edges.add(new TreeSet<>());
        int[] degree = new int[units.size()];
        for (int i = 0; i < parents.length; i++) for (int p : parents[i])
            if (owner[p] != owner[i] && edges.get(owner[p]).add(owner[i])) degree[owner[i]]++;
        var ready = new PriorityQueue<Integer>(); for (int i = 0; i < degree.length; i++) if (degree[i] == 0) ready.add(i);
        var ordered = new ArrayList<int[]>();
        while (!ready.isEmpty()) { int id = ready.remove(); ordered.add(units.get(id)); for (int child : edges.get(id)) if (--degree[child] == 0) ready.add(child); }
        if (ordered.size() != units.size()) throw new IllegalArgumentException("Table suspended: inter-network dependency cycle");
        executionGroups = ordered.toArray(int[][]::new); installedTables.add(unit); wholeTables.clear(); wholeTables.putAll(macros);
        buildIncrementalPlan(optimizationPositions);
    }
    private void evaluateTable(WholeTable unit, boolean[] values, boolean[] seeds, int[] work) {
        int key = 0;
        for (int i = 0; i < unit.inputNodes.length; i++) {
            boolean signal = seeds[unit.inputNodes[i]];
            for (int parent : unit.incoming[i]) signal |= values[aliases[parent]];
            chargeWork(1 + unit.incoming[i].length, unit.nodes[0], work);
            if (signal) key |= 1 << i;
        }
        chargeWork(unit.table.words + unit.outputNodes.length, unit.nodes[0], work);
        for (int i = 0; i < unit.outputNodes.length; i++) values[unit.outputNodes[i]] = unit.table.output(key, i);
    }
    private final BlockPos[] positions;
    private boolean[] stable;
    private boolean memoryChanged;
    private int lastWork;
    private int[] aliases;
    public static final int MAX_TABLE_INPUTS = 8, MAX_TABLE_NODES = 64,
            MAX_TABLE_BYTES = 65536, MAX_TABLE_BUILD_WORK = 262144;
    private record TruthTable(int[] inputs, int[] nodes, long[] rows, int work) {}
    private TruthTable[] tableStarts;
    private boolean[] tableMembers;
    private int tableCount, tableBytes, tableNodes;
    // Boundaries belong to execution units: a node, a complete table, or a complete SCC.
    private int[][] incrementalBoundary, incrementalSeeds;
    private boolean[] incrementalUnits, previousSeeds;
    private boolean incrementalValid;
    private int lastSkipped, lastChecks;
    int lastChecks() { return lastChecks; }
    int lastSkipped() { return lastSkipped; }
    private void buildIncrementalPlan(Set<BlockPos> allowed) {
        incrementalBoundary = new int[executionGroups.length][];
        incrementalUnits = new boolean[executionGroups.length];
        incrementalSeeds = new int[executionGroups.length][];
        var seedNodes = new HashSet<>(inputs.values());
        for (int g = 0; g < executionGroups.length; g++) {
            int[] group = executionGroups[g]; int first = group[0];
            var table = tableStarts[first];
            if (group.length == 1 && (aliases[first] != first || (tableMembers[first] && table == null))) continue;
            int[] nodes = table == null ? group : table.nodes;
            boolean eligible = true;
            Set<Integer> members = new HashSet<>(), boundary = new TreeSet<>();
            for (int node : nodes) { members.add(node); eligible &= allowed.contains(positions[node]); }
            for (int node : nodes) for (int parent : parents[node])
                if (!members.contains(aliases[parent])) boundary.add(aliases[parent]);
            incrementalSeeds[g] = Arrays.stream(nodes).filter(seedNodes::contains).toArray();
            incrementalUnits[g] = eligible || wholeTables.containsKey(first);
            incrementalBoundary[g] = boundary.stream().mapToInt(Integer::intValue).toArray();
        }
        incrementalValid = false;
    }
    private boolean canSkip(int g, boolean[] values, boolean[] seeds) {
        if (!incrementalValid || incrementalUnits == null || !incrementalUnits[g]) return false;
        for (int node : incrementalSeeds[g]) { lastChecks++; if (seeds[node] != previousSeeds[node]) return false; }
        for (int parent : incrementalBoundary[g]) { lastChecks++; if (values[parent] != stable[parent]) return false; }
        return true;
    }
    public int truthTableCount() { return tableCount + installedTables.size(); }
    public int truthTableBytes() { return tableBytes + installedTables.stream().mapToInt(t -> t.table.bytes()).sum(); }
    public int truthTableNodes() { return tableNodes + installedTables.stream().mapToInt(t -> t.nodes.length).sum(); }
    public int nodeCount() { return operations.length; }
    public int evaluationNodeCount() {
        int count = 0; for (int i = 0; i < aliases.length; i++)
            if (aliases[i] == i && (!tableMembers[i] || tableStarts[i] != null)) count++;
        for (var t : installedTables) count -= t.nodes.length - 1;
        return count;
    }
    private record Expression(Op op, List<Integer> parents) {}
    /** Keep original IDs/topology for saved memory and feedback; simplify only the execution plan. */
    void optimize(Set<BlockPos> allowed) {
        optimize(allowed, false);
    }
    void optimize(Set<BlockPos> allowed, boolean useTables) {
        allowed = new HashSet<>(allowed);
        for (var t : installedTables) for (int node : t.nodes) allowed.remove(positions[node]);
        optimizationPositions = Set.copyOf(allowed);
        Arrays.fill(tableStarts, null); Arrays.fill(tableMembers, false);
        tableCount = tableBytes = tableNodes = 0;
        for (int i = 0; i < aliases.length; i++) aliases[i] = i;
        Set<Integer> seeded = new HashSet<>(inputs.values());
        Map<Expression, Integer> common = new HashMap<>();
        for (int[] group : groups) {
            if (group.length != 1) continue;
            int id = group[0]; Op op = operations[id];
            if (!allowed.contains(positions[id]) || seeded.contains(id) || op == Op.LATCH) continue;
            int[] sources = Arrays.stream(parents[id]).map(p -> aliases[p]).sorted().toArray();
            if (op == Op.OR && sources.length == 1) { aliases[id] = sources[0]; continue; }
            if (op == Op.NOT && sources.length == 1) {
                int parent = sources[0];
                if (operations[parent] == Op.NOT && groups[component[parent]].length == 1 && parents[parent].length == 1) {
                    aliases[id] = aliases[parents[parent][0]]; continue;
                }
            }
            // Preserve duplicate inputs: XOR(A,A) must stay false, not become A.
            var key = new Expression(op, Arrays.stream(sources).boxed().toList());
            Integer earlier = common.putIfAbsent(key, id);
            if (earlier != null) aliases[id] = earlier;
        }
        if (useTables) buildTruthTables(allowed, seeded);
        buildIncrementalPlan(allowed);
    }
    /** Contiguous topological runs cannot move an evaluation across a latch or feedback component. */
    private void buildTruthTables(Set<BlockPos> allowed, Set<Integer> seeded) {
        var run = new ArrayList<Integer>(); var members = new HashSet<Integer>();
        var boundary = new TreeSet<Integer>(); int[] budget = {MAX_TABLE_BUILD_WORK};
        boolean[] values = new boolean[operations.length], seeds = new boolean[operations.length];
        for (int[] group : groups) {
            int id = group[0];
            boolean barrier = group.length != 1 || operations[id] == Op.LATCH
                    || seeded.contains(id) || !allowed.contains(positions[id]);
            if (barrier) {
                buildTruthTable(run, boundary, budget, values, seeds);
                run.clear(); members.clear(); boundary.clear(); continue;
            }
            if (aliases[id] != id) continue;
            var nextBoundary = new TreeSet<>(boundary);
            for (int parent : parents[id]) if (!members.contains(aliases[parent])) nextBoundary.add(aliases[parent]);
            if (run.size() == MAX_TABLE_NODES || nextBoundary.size() > MAX_TABLE_INPUTS) {
                buildTruthTable(run, boundary, budget, values, seeds);
                run.clear(); members.clear(); boundary.clear(); nextBoundary.clear();
                for (int parent : parents[id]) nextBoundary.add(aliases[parent]);
            }
            if (nextBoundary.size() > MAX_TABLE_INPUTS) continue;
            run.add(id); members.add(id); boundary = nextBoundary;
        }
        buildTruthTable(run, boundary, budget, values, seeds);
    }
    private void buildTruthTable(List<Integer> run, Set<Integer> boundary, int[] budget, boolean[] values, boolean[] seeds) {
        if (run.isEmpty()) return;
        int normalWork = 0; for (int id : run) normalWork += 1 + parents[id].length;
        int lookupWork = 1 + boundary.size() + run.size(); // Include reading the key and restoring every original node.
        int rowCount = 1 << boundary.size(), bytes = rowCount * Long.BYTES;
        long buildWork = (long) rowCount * (normalWork + boundary.size());
        // Tiny gates are cheaper to calculate. Keep only a substantial reduction in modeled work.
        if (normalWork < 2 * lookupWork || buildWork > budget[0] || tableBytes + bytes > MAX_TABLE_BYTES) return;
        int[] inputs = boundary.stream().mapToInt(Integer::intValue).toArray();
        int[] nodes = run.stream().mapToInt(Integer::intValue).toArray(); long[] rows = new long[rowCount];
        for (int row = 0; row < rowCount; row++) {
            for (int i = 0; i < inputs.length; i++) values[inputs[i]] = (row & (1 << i)) != 0;
            long bits = 0;
            for (int i = 0; i < nodes.length; i++) {
                int id = nodes[i]; values[id] = value(id, values, seeds);
                if (values[id]) bits |= 1L << i;
            }
            rows[row] = bits;
        }
        budget[0] -= (int) buildWork;
        var table = new TruthTable(inputs, nodes, rows, lookupWork); tableStarts[nodes[0]] = table;
        for (int id : nodes) tableMembers[id] = true;
        tableCount++; tableBytes += bytes; tableNodes += nodes.length;
    }
    int lastWork() { return lastWork; }
    boolean[] benchmarkState() { return stable.clone(); }
    void benchmarkReset(boolean[] state) { stable = state.clone(); incrementalValid = false; }
    List<BlockPos> benchmarkInputs() {
        var byNode = new java.util.TreeMap<Integer, BlockPos>();
        inputs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> byNode.putIfAbsent(e.getValue(), e.getKey()));
        return List.copyOf(byNode.values());
    }
    private boolean externalMemory;
    private final boolean feedback;
    private final boolean hasMemory;
    private final String signature;
    public static final int MAX_SETTLE_SWEEPS = 256, MAX_EVALUATION_WORK = 262144;
    private final Map<BlockPos, Integer> inputs, outputs;

    private LogicProgram(List<Node> nodes, int[][] groups, Map<BlockPos, Integer> inputs, Map<BlockPos, Integer> outputs) {
        this.groups = groups; this.executionGroups = groups; this.inputs = Map.copyOf(inputs); this.outputs = Map.copyOf(outputs);
        operations = new Op[nodes.size()]; parents = new int[nodes.size()][];
        children = new int[nodes.size()][]; component = new int[nodes.size()];
        dataSources = new int[nodes.size()]; enableSources = new int[nodes.size()];
        for (int g = 0; g < groups.length; g++) for (int id : groups[g]) component[id] = g;
        positions = new BlockPos[nodes.size()]; stable = new boolean[nodes.size()];
        StringBuilder topology = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            operations[i] = nodes.get(i).op; positions[i] = nodes.get(i).position;
            dataSources[i] = nodes.get(i).data; enableSources[i] = nodes.get(i).enable;
            parents[i] = nodes.get(i).parents.stream().mapToInt(Integer::intValue).sorted().toArray();
            children[i] = nodes.get(i).children.stream().mapToInt(Integer::intValue).sorted().toArray();
            topology.append(positions[i].asLong()).append(':').append(operations[i]).append(Arrays.toString(parents[i])).append(';');
            if (operations[i] == Op.LATCH) topology.append("D=").append(dataSources[i]).append(",WDE=").append(enableSources[i]).append(';');
        }
        signature = UUID.nameUUIDFromBytes(topology.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        feedback = Arrays.stream(groups).anyMatch(g -> g.length > 1);
        hasMemory = feedback || Arrays.asList(operations).contains(Op.LATCH);
        aliases = new int[nodes.size()]; for (int i = 0; i < aliases.length; i++) aliases[i] = i;
        tableStarts = new TruthTable[nodes.size()]; tableMembers = new boolean[nodes.size()];
    }
    public static final class UnstableFeedback extends IllegalStateException {
        public final BlockPos position;
        private UnstableFeedback(String reason, BlockPos position) {
            super(reason + " at " + position.getX() + ", " + position.getY() + ", " + position.getZ());
            this.position = position;
        }
    }
    java.util.Map<String,Boolean> nodeMemory() {
        var result=new java.util.HashMap<String,Boolean>();var ordinal=new java.util.HashMap<BlockPos,Integer>();
        for(int i=0;i<stable.length;i++)result.put(positions[i].asLong()+":"+operations[i]+":"+ordinal.merge(positions[i],1,Integer::sum),stable[i]);
        return result;
    }
    void importNodeMemory(java.util.Map<String,Boolean> memory) {
        externalMemory=true; incrementalValid=false;
        var ordinal=new java.util.HashMap<BlockPos,Integer>();
        for(int i=0;i<stable.length;i++)stable[i]=memory.getOrDefault(positions[i].asLong()+":"+operations[i]+":"+ordinal.merge(positions[i],1,Integer::sum),false);
    }
    public boolean memoryChanged() { return memoryChanged; }
    public boolean hasFeedback() { return feedback; }
    boolean hasStatefulMemory(){return hasMemory;}
    public net.minecraft.nbt.CompoundTag saveMemory() {
        var tag = new net.minecraft.nbt.CompoundTag();
        if (!hasMemory && !externalMemory) return tag;
        tag.putInt("version", 1); tag.putString("graph", signature);
        BitSet bits = new BitSet(stable.length);
        for (int i = 0; i < stable.length; i++) if (stable[i]) bits.set(i);
        tag.putLongArray("values", bits.toLongArray());
        return tag;
    }
    public void restoreMemory(net.minecraft.nbt.CompoundTag tag) {
        if (tag.isEmpty()) return;
        if (tag.getInt("version") != 1 || !signature.equals(tag.getString("graph"))
                || !tag.contains("values", net.minecraft.nbt.Tag.TAG_LONG_ARRAY))
            throw new IllegalArgumentException("Invalid feedback memory");
        long[] words = tag.getLongArray("values");
        if (words.length > (stable.length + 63) / 64) throw new IllegalArgumentException("Oversized feedback memory");
        BitSet bits = BitSet.valueOf(words);
        if (bits.length() > stable.length) throw new IllegalArgumentException("Invalid feedback memory size");
        for (int i = 0; i < stable.length; i++) stable[i] = bits.get(i);
        incrementalValid = false;
    }
    private boolean value(int node, boolean[] values, boolean[] seeds) {
        Op op = operations[node];
        if (op == Op.LATCH) return values[aliases[enableSources[node]]] ? values[aliases[dataSources[node]]] : values[node];
        boolean value = op == Op.AND || op == Op.NAND || seeds[node];
        for (int parent : parents[node]) {
            if (op == Op.AND || op == Op.NAND) value &= values[aliases[parent]];
            else if (op == Op.XOR || op == Op.XNOR) value ^= values[aliases[parent]];
            else value |= values[aliases[parent]];
        }
        return switch (op) { case NOT, NAND, NOR, XNOR -> !value; default -> value; };
    }
    public Map<BlockPos, Boolean> evaluate(Map<BlockPos, Boolean> externalInputs) {
        lastWork = 0; lastSkipped = 0; lastChecks = 0;
        memoryChanged = false;
        boolean[] values = stable.clone(), seeds = new boolean[operations.length];
        inputs.forEach((pos, node) -> seeds[node] |= externalInputs.getOrDefault(pos, false));
        int[] work = {0};
        boolean[] queued = new boolean[operations.length];
        for (int g = 0; g < executionGroups.length; g++) {
            int[] group = executionGroups[g];
            if (canSkip(g, values, seeds)) { lastSkipped++; continue; }
            var whole = wholeTables.get(group[0]);
            if (whole != null) { evaluateTable(whole, values, seeds, work); continue; }
            if (group.length == 1) {
                int node = group[0]; if (aliases[node] != node) continue;
                var table = tableStarts[node];
                if (table != null) {
                    chargeWork(table.work, node, work); int key = 0;
                    for (int i = 0; i < table.inputs.length; i++) if (values[table.inputs[i]]) key |= 1 << i;
                    long bits = table.rows[key];
                    for (int i = 0; i < table.nodes.length; i++) values[table.nodes[i]] = (bits & (1L << i)) != 0;
                    continue;
                }
                if (tableMembers[node]) continue;
                charge(node, work);
                values[node] = value(node, values, seeds);
                continue;
            }
            Set<BitSet> seen = new HashSet<>();
            seen.add(snapshot(group, values));
            boolean settled = false;
            for (int sweep = 0; sweep < MAX_SETTLE_SWEEPS; sweep++) {
                // Propagate transparent buses/ports before each gate. Otherwise stale port
                // values can create an artificial synchronous oscillation in a NOR latch.
                boolean changed = settleOr(group, component[group[0]], values, seeds, work, queued);
                for (int node : group) {
                    if (operations[node] == Op.OR) continue;
                    charge(node, work);
                    boolean next = value(node, values, seeds);
                    if (next != values[node]) {
                        changed = true; values[node] = next;
                        settleOr(children[node], component[node], values, seeds, work, queued);
                    }
                }
                if (!changed) { settled = true; break; }
                if (!seen.add(snapshot(group, values)))
                    throw new UnstableFeedback("Oscillating feedback", positions[group[0]]);
            }
            if (!settled) throw new UnstableFeedback("Feedback did not settle within the sweep limit", positions[group[0]]);
        }
        // Never commit partial updates from a failed cycle or downstream component.
        for (int i = 0; i < aliases.length; i++) if (aliases[i] != i) values[i] = values[aliases[i]];
        memoryChanged = hasMemory && !Arrays.equals(stable, values);
        stable = values;
        previousSeeds = seeds; incrementalValid = true;
        Map<BlockPos, Boolean> result = new HashMap<>();
        outputs.forEach((pos, node) -> result.put(pos, values[node]));
        return Map.copyOf(result);
    }
    private void charge(int node, int[] work) {
        chargeWork(1 + parents[node].length, node, work);
    }
    private void chargeWork(int amount, int node, int[] work) {
        work[0] += amount;
        lastWork = work[0];
        if (work[0] > MAX_EVALUATION_WORK)
            throw new UnstableFeedback("Feedback settling work limit exceeded", positions[node]);
    }
    private boolean settleOr(int[] initial, int group, boolean[] values, boolean[] seeds, int[] work, boolean[] queued) {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int node : initial) if (component[node] == group && operations[node] == Op.OR && !queued[node]) {
            queued[node] = true; queue.add(node);
        }
        boolean changed = false;
        while (!queue.isEmpty()) {
            int node = queue.removeFirst(); queued[node] = false; charge(node, work);
            boolean next = value(node, values, seeds);
            if (values[node] == next) continue;
            changed = true; values[node] = next;
            for (int child : children[node])
                if (component[child] == group && operations[child] == Op.OR && !queued[child]) {
                    queued[child] = true; queue.add(child);
                }
        }
        return changed;
    }
    private static BitSet snapshot(int[] group, boolean[] values) {
        BitSet bits = new BitSet(group.length);
        for (int i = 0; i < group.length; i++) if (values[group[i]]) bits.set(i);
        return bits;
    }
    public static boolean hasLogic(Collection<BlockState> states) {
        return states.stream().anyMatch(s -> s.getBlock() instanceof GateBodyBlock || s.getBlock() instanceof NetworkWireBlock || CircuitConnections.isTerminal(s));
    }
    public static LogicProgram compile(Map<BlockPos, BlockState> states) {return compile(states,java.util.Map.of(),java.util.Map.of());}
    static LogicProgram compile(Map<BlockPos,BlockState> states,Map<BlockPos,Set<BlockPos>> links,Map<BlockPos,Integer> ownership) {
        if (states.size() > MAX_ELEMENTS)
            throw new IllegalArgumentException("Logic circuits support at most " + MAX_ELEMENTS + " elements.");
        // Stable node IDs are required for repeatable startup and saved feedback state.
        Map<BlockPos, BlockState> sorted = new TreeMap<>(Comparator.comparingInt((BlockPos p) -> p.getX())
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        sorted.putAll(states); states = sorted;
        List<Node> nodes = new ArrayList<>();
        Map<BlockPos, Integer> component = new HashMap<>(), source = new HashMap<>(), sink = new HashMap<>();
        Map<BlockPos, Integer> enableSink = new HashMap<>();
        Map<BlockPos, Integer> inputs = new HashMap<>(), outputs = new HashMap<>();
        Set<Integer> bodies = new HashSet<>(), bodiesWithOutputs = new HashSet<>();
        // Flood-fill only homogeneous bodies or passive bus elements.
        for (var item : states.entrySet()) {
            BlockPos pos = item.getKey(); BlockState state = item.getValue();
            if (component.containsKey(pos) || CircuitConnections.isTerminal(state)) continue;
            boolean body = state.getBlock() instanceof GateBodyBlock;
            if (!body && !(state.getBlock() instanceof IWireConnectable))
                throw error("Unsupported circuit element", pos);
            Op op = body ? switch (((GateBodyBlock) state.getBlock()).kind()) {
                case AND -> Op.AND;
                case OR -> Op.OR;
                case NAND -> Op.NAND;
                case NOR -> Op.NOR;
                case XOR -> Op.XOR;
                case XNOR -> Op.XNOR;
            } : Op.OR;
            int id = add(nodes, op, pos);
            if (body) bodies.add(id);
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            component.put(pos, id); queue.add(pos);
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst(); BlockState currentState = states.get(current);
                for (Direction direction : Direction.values()) {
                    BlockPos next = current.relative(direction); BlockState other = states.get(next);
                    if (other == null || component.containsKey(next) || CircuitConnections.isTerminal(other)) continue;
                    boolean otherBody = other.getBlock() instanceof GateBodyBlock;
                    if (body != otherBody || !java.util.Objects.equals(ownership.get(current),ownership.get(next)) || !CircuitConnections.connects(currentState, other, direction)) continue;
                    component.put(next, id); queue.add(next);
                }
            }
        }
        for (var item : states.entrySet()) {
            BlockPos pos = item.getKey(); BlockState state = item.getValue();
            if (state.getBlock() instanceof GatePortBlock port) {
                Set<Integer> owners = new HashSet<>();
                for (Direction direction : Direction.values()) {
                    Integer id = component.get(pos.relative(direction));
                    if (id != null && bodies.contains(id) && java.util.Objects.equals(ownership.get(pos),ownership.get(pos.relative(direction)))) owners.add(id);
                }
                if (owners.size() != 1)
                    throw error(owners.isEmpty() ? "Port must touch a gate body" : "Port touches more than one gate", pos);
                int owner = owners.iterator().next(), id = add(nodes, Op.OR, pos);
                if (port.isInput()) { sink.put(pos, id); edge(nodes, id, owner); }
                else { source.put(pos, id); edge(nodes, owner, id); bodiesWithOutputs.add(owner); }
            } else if (state.getBlock() instanceof DLatchBlock) {
                int input = add(nodes, Op.OR, pos), enable = add(nodes, Op.OR, pos), output = add(nodes, Op.LATCH, pos);
                sink.put(pos, input); enableSink.put(pos, enable); source.put(pos, output);
                nodes.get(output).data = input; nodes.get(output).enable = enable;
                edge(nodes, input, output); edge(nodes, enable, output);
            } else if (state.getBlock() instanceof NotGateBlock) {
                int input = add(nodes, Op.OR, pos), output = add(nodes, Op.NOT, pos);
                sink.put(pos, input); source.put(pos, output); edge(nodes, input, output);
            } else if (!(state.getBlock() instanceof GateBodyBlock)) {
                int id = component.get(pos);
                source.put(pos, id); sink.put(pos, id);
                if (state.getBlock() instanceof InputEndpointBlock) inputs.put(pos, id);
                if (state.getBlock() instanceof OutputEndpointBlock) outputs.put(pos, id);
                if(state.getBlock() instanceof NetworkWireBlock) {
                    if(NetworkWireBlock.hasPort(state,2))inputs.put(pos,id);
                    if(NetworkWireBlock.hasPort(state,3))outputs.put(pos,id);
                }
            }
        }
        // Each surface input is independent, even when several faces share one body block.
        for (var item : states.entrySet()) {
            BlockPos pos = item.getKey(); BlockState state = item.getValue();
            if (!(state.getBlock() instanceof GateBodyBlock)) continue;
            int owner = component.get(pos);
            for (Direction side : Direction.values()) {
                int port = GateBodyBlock.port(state, side);
                if (port == 0 || port == 3) continue;
                BlockPos next = pos.relative(side); BlockState neighbor = states.get(next);
                if (neighbor != null && neighbor.getBlock() instanceof GateBodyBlock) continue;
                int node = add(nodes, Op.OR, pos);
                if (port == 1) {
                    edge(nodes, node, owner);
                    if (neighbor != null && neighbor.getBlock() instanceof BasicWireBlock && java.util.Objects.equals(ownership.get(pos),ownership.get(next)) && CircuitConnections.connects(state,neighbor,side))
                        edge(nodes, component.get(next), node);
                } else {
                    edge(nodes, owner, node); bodiesWithOutputs.add(owner);
                    if (neighbor != null && neighbor.getBlock() instanceof BasicWireBlock && java.util.Objects.equals(ownership.get(pos),ownership.get(next)) && CircuitConnections.connects(state,neighbor,side))
                        edge(nodes, node, component.get(next));
                }
            }
        }
        for (int body : bodies) {
            if (nodes.get(body).parents.isEmpty() || !bodiesWithOutputs.contains(body))
                throw error("Gate needs at least one input port and one output port", nodes.get(body).position);
        }
        for (var item : states.entrySet()) {
            BlockPos pos = item.getKey(); BlockState state = item.getValue();
            Integer from = source.get(pos);
            if (from == null) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction); BlockState other = states.get(next);
                Integer to = sink.get(next);
                if (other != null && other.getBlock() instanceof DLatchBlock
                        && DLatchBlock.enableSide(other) == direction.getOpposite()) to = enableSink.get(next);
                if (other == null || to == null || from.equals(to)
                        || !java.util.Objects.equals(ownership.get(pos),ownership.get(next))
                        || !CircuitConnections.connects(state, other, direction)) continue;
                if (CircuitConnections.isTerminal(state) && !CircuitConnections.sends(state, direction)) continue;
                if (CircuitConnections.isTerminal(other) && !CircuitConnections.receives(other, direction.getOpposite())) continue;
                edge(nodes, from, to);
            }
        }
        links.forEach((from,targets)->targets.forEach(to->{
            Integer a=source.get(from),b=sink.get(to);if(a==null||b==null)throw error("Invalid direct link",from);
            if(!a.equals(b))edge(nodes,a,b);
        }));
        int[][] groups = components(nodes);
        Set<Integer> seededNodes = new HashSet<>(inputs.values());
        for (int[] group : groups) {
            if (group.length < 2) continue;
            // A closed single-driver ring with odd inversion has no Boolean solution.
            boolean ring = true; int inversions = 0;
            for (int id : group) {
                ring &= nodes.get(id).parents.size() == 1 && !seededNodes.contains(id);
                if (switch (nodes.get(id).op) { case NOT, NAND, NOR, XNOR -> true; default -> false; }) inversions++;
            }
            if (ring && (inversions & 1) != 0)
                throw error("Feedback cycle has no stable state", nodes.get(group[0]).position);
        }
        return new LogicProgram(nodes, groups, inputs, outputs);
    }
    /** Iterative Kosaraju: stack-safe even for long chains, components in dependency order. */
    private static int[][] components(List<Node> nodes) {
        record Frame(int id, Iterator<Integer> children) {}
        boolean[] visited = new boolean[nodes.size()];
        List<Integer> finished = new ArrayList<>();
        ArrayDeque<Frame> stack = new ArrayDeque<>();
        for (int root = 0; root < nodes.size(); root++) {
            if (visited[root]) continue;
            visited[root] = true; stack.push(new Frame(root, nodes.get(root).children.iterator()));
            while (!stack.isEmpty()) {
                Frame frame = stack.peek();
                if (frame.children.hasNext()) {
                    int child = frame.children.next();
                    if (!visited[child]) { visited[child] = true; stack.push(new Frame(child, nodes.get(child).children.iterator())); }
                } else { finished.add(frame.id); stack.pop(); }
            }
        }
        Arrays.fill(visited, false);
        List<int[]> groups = new ArrayList<>(); ArrayDeque<Integer> pending = new ArrayDeque<>();
        for (int i = finished.size() - 1; i >= 0; i--) {
            int root = finished.get(i); if (visited[root]) continue;
            List<Integer> group = new ArrayList<>(); visited[root] = true; pending.push(root);
            while (!pending.isEmpty()) {
                int id = pending.pop(); group.add(id);
                for (int parent : nodes.get(id).parents) if (!visited[parent]) { visited[parent] = true; pending.push(parent); }
            }
            groups.add(group.stream().mapToInt(Integer::intValue).sorted().toArray());
        }
        return groups.toArray(int[][]::new);
    }    private static int add(List<Node> nodes, Op op, BlockPos pos) {
        int id = nodes.size(); nodes.add(new Node(op, pos)); return id;
    }
    private static void edge(List<Node> nodes, int from, int to) {
        nodes.get(to).parents.add(from); nodes.get(from).children.add(to);
    }
    private static IllegalArgumentException error(String message, BlockPos pos) {
        return new IllegalArgumentException(message + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }
}
