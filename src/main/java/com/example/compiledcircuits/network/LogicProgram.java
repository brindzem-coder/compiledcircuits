package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/**
 * Immutable combinational graph. Structural discovery is not electrical connectivity:
 * passive components are buses; each port is an independent directed node.
 * Evaluation reads no world state and publishes only a complete result.
 */
public final class LogicProgram {
    // A single atomic evaluation must stay bounded, even with deeply nested gates.
    public static final int MAX_ELEMENTS = 8192;
    private enum Op { OR, AND, NOT }
    private static final class Node {
        final Op op;
        final BlockPos position;
        final Set<Integer> parents = new LinkedHashSet<>(), children = new LinkedHashSet<>();
        Node(Op op, BlockPos position) { this.op = op; this.position = position; }
    }
    private final Op[] operations;
    private final int[][] parents;
    private final int[] order;
    private final Map<BlockPos, Integer> inputs, outputs;

    private LogicProgram(List<Node> nodes, int[] order, Map<BlockPos, Integer> inputs, Map<BlockPos, Integer> outputs) {
        this.order = order; this.inputs = Map.copyOf(inputs); this.outputs = Map.copyOf(outputs);
        operations = new Op[nodes.size()]; parents = new int[nodes.size()][];
        for (int i = 0; i < nodes.size(); i++) {
            operations[i] = nodes.get(i).op;
            parents[i] = nodes.get(i).parents.stream().mapToInt(Integer::intValue).toArray();
        }
    }
    public Map<BlockPos, Boolean> evaluate(Map<BlockPos, Boolean> externalInputs) {
        boolean[] values = new boolean[operations.length];
        inputs.forEach((pos, node) -> values[node] |= externalInputs.getOrDefault(pos, false));
        for (int node : order) {
            boolean value = operations[node] == Op.AND || values[node];
            for (int parent : parents[node]) {
                if (operations[node] == Op.AND) value &= values[parent];
                else value |= values[parent];
            }
            values[node] = operations[node] == Op.NOT ? !value : value;
        }
        Map<BlockPos, Boolean> result = new HashMap<>();
        outputs.forEach((pos, node) -> result.put(pos, values[node]));
        return Map.copyOf(result);
    }
    public static boolean hasLogic(Collection<BlockState> states) {
        return states.stream().anyMatch(s -> s.getBlock() instanceof GateBodyBlock || CircuitConnections.isTerminal(s));
    }
    public static LogicProgram compile(Map<BlockPos, BlockState> states) {
        if (states.size() > MAX_ELEMENTS)
            throw new IllegalArgumentException("Logic circuits support at most " + MAX_ELEMENTS + " elements.");
        List<Node> nodes = new ArrayList<>();
        Map<BlockPos, Integer> component = new HashMap<>(), source = new HashMap<>(), sink = new HashMap<>();
        Map<BlockPos, Integer> inputs = new HashMap<>(), outputs = new HashMap<>();
        Set<Integer> bodies = new HashSet<>(), bodiesWithOutputs = new HashSet<>();
        // Flood-fill only homogeneous bodies or passive bus elements.
        for (var item : states.entrySet()) {
            BlockPos pos = item.getKey(); BlockState state = item.getValue();
            if (component.containsKey(pos) || CircuitConnections.isTerminal(state)) continue;
            boolean body = state.getBlock() instanceof GateBodyBlock;
            if (!body && !(state.getBlock() instanceof IWireConnectable))
                throw error("Unsupported circuit element", pos);
            Op op = body && ((GateBodyBlock) state.getBlock()).kind() == GateBodyBlock.Kind.AND ? Op.AND : Op.OR;
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
                    if (body != otherBody || !CircuitConnections.connects(currentState, other, direction)) continue;
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
                    if (id != null && bodies.contains(id)) owners.add(id);
                }
                if (owners.size() != 1)
                    throw error(owners.isEmpty() ? "Port must touch a gate body" : "Port touches more than one gate", pos);
                int owner = owners.iterator().next(), id = add(nodes, Op.OR, pos);
                if (port.isInput()) { sink.put(pos, id); edge(nodes, id, owner); }
                else { source.put(pos, id); edge(nodes, owner, id); bodiesWithOutputs.add(owner); }
            } else if (state.getBlock() instanceof NotGateBlock) {
                int input = add(nodes, Op.OR, pos), output = add(nodes, Op.NOT, pos);
                sink.put(pos, input); source.put(pos, output); edge(nodes, input, output);
            } else if (!(state.getBlock() instanceof GateBodyBlock)) {
                int id = component.get(pos);
                source.put(pos, id); sink.put(pos, id);
                if (state.getBlock() instanceof InputEndpointBlock) inputs.put(pos, id);
                if (state.getBlock() instanceof OutputEndpointBlock) outputs.put(pos, id);
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
                if (other == null || to == null || from.equals(to)
                        || !CircuitConnections.connects(state, other, direction)) continue;
                if (CircuitConnections.isTerminal(state) && !CircuitConnections.sends(state, direction)) continue;
                if (CircuitConnections.isTerminal(other) && !CircuitConnections.receives(other, direction.getOpposite())) continue;
                edge(nodes, from, to);
            }
        }
        int[] degrees = new int[nodes.size()], order = new int[nodes.size()];
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int i = 0; i < nodes.size(); i++) {
            degrees[i] = nodes.get(i).parents.size();
            if (degrees[i] == 0) ready.add(i);
        }
        int count = 0;
        while (!ready.isEmpty()) {
            int id = ready.removeFirst(); order[count++] = id;
            for (int child : nodes.get(id).children) if (--degrees[child] == 0) ready.add(child);
        }
        if (count != nodes.size()) {
            // Follow unresolved predecessors to report an actual cycle node, not a downstream output.
            int id = 0;
            while (degrees[id] == 0) id++;
            Set<Integer> visited = new HashSet<>();
            while (visited.add(id)) {
                for (int parent : nodes.get(id).parents) if (degrees[parent] != 0) { id = parent; break; }
            }
            throw error("Feedback cycle", nodes.get(id).position);
        }
        return new LogicProgram(nodes, order, inputs, outputs);
    }
    private static int add(List<Node> nodes, Op op, BlockPos pos) {
        int id = nodes.size(); nodes.add(new Node(op, pos)); return id;
    }
    private static void edge(List<Node> nodes, int from, int to) {
        nodes.get(to).parents.add(from); nodes.get(from).children.add(to);
    }
    private static IllegalArgumentException error(String message, BlockPos pos) {
        return new IllegalArgumentException(message + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }
}
