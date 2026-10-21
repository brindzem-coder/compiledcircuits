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
    private final BlockPos[] positions;
    private boolean[] stable;
    private boolean memoryChanged;
    private int lastWork;
    private int[] aliases;
    public int nodeCount() { return operations.length; }
    public int evaluationNodeCount() {
        int count = 0; for (int i = 0; i < aliases.length; i++) if (aliases[i] == i) count++; return count;
    }
    private record Expression(Op op, List<Integer> parents) {}
    /** Keep original IDs/topology for saved memory and feedback; simplify only the execution plan. */
    void optimize(Set<BlockPos> allowed) {
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
    }
    int lastWork() { return lastWork; }
    boolean[] benchmarkState() { return stable.clone(); }
    void benchmarkReset(boolean[] state) { stable = state.clone(); }
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
        this.groups = groups; this.inputs = Map.copyOf(inputs); this.outputs = Map.copyOf(outputs);
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
        externalMemory=true;
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
        lastWork = 0;
        memoryChanged = false;
        boolean[] values = stable.clone(), seeds = new boolean[operations.length];
        inputs.forEach((pos, node) -> seeds[node] |= externalInputs.getOrDefault(pos, false));
        int[] work = {0};
        boolean[] queued = new boolean[operations.length];
        for (int[] group : groups) {
            if (group.length == 1) {
                int node = group[0]; if (aliases[node] != node) continue; charge(node, work);
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
        Map<BlockPos, Boolean> result = new HashMap<>();
        outputs.forEach((pos, node) -> result.put(pos, values[node]));
        return Map.copyOf(result);
    }
    private void charge(int node, int[] work) {
        work[0] += 1 + parents[node].length;
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
