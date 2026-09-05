package com.example.compiledcircuits.client;

import com.example.compiledcircuits.block.IWireConnectable;
import com.example.compiledcircuits.block.InputEndpointBlock;
import com.example.compiledcircuits.block.OutputEndpointBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public final class ClientNetworkSelection {

    // Захист від випадкового сканування надто великої мережі.
    private static final int MAX_SCAN_SIZE = 50_000;

    private static final Set<BlockPos> WIRES = new HashSet<>();
    private static final Set<BlockPos> INPUTS = new HashSet<>();
    private static final Set<BlockPos> OUTPUTS = new HashSet<>();

    private ClientNetworkSelection() {
    }

    public static boolean selectNetwork(
            ClientLevel level,
            BlockPos startPos
    ) {

        BlockState startState = level.getBlockState(startPos);

        if (!(startState.getBlock() instanceof IWireConnectable)) {
            return false;
        }

        clear();

        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(startPos.immutable());

        while (!queue.isEmpty()) {

            BlockPos currentPos = queue.remove();

            if (!visited.add(currentPos)) {
                continue;
            }

            if (visited.size() > MAX_SCAN_SIZE) {
                clear();
                return false;
            }

            BlockState currentState =
                    level.getBlockState(currentPos);

            if (!(currentState.getBlock()
                    instanceof IWireConnectable currentConnectable)) {
                continue;
            }

            classify(currentPos, currentState);

            for (Direction direction : Direction.values()) {

                BlockPos neighborPos =
                        currentPos.relative(direction);

                // Не змушуємо client підвантажувати нові chunks
                // тільки через selection.
                if (!level.hasChunkAt(neighborPos)) {
                    continue;
                }

                BlockState neighborState =
                        level.getBlockState(neighborPos);

                if (!(neighborState.getBlock()
                        instanceof IWireConnectable neighborConnectable)) {
                    continue;
                }

                boolean currentAllows =
                        currentConnectable.canWireConnect(
                                currentState,
                                direction
                        );

                boolean neighborAllows =
                        neighborConnectable.canWireConnect(
                                neighborState,
                                direction.getOpposite()
                        );

                if (currentAllows && neighborAllows) {
                    queue.add(neighborPos.immutable());
                }
            }
        }

        return !visited.isEmpty();
    }

    private static void classify(
            BlockPos pos,
            BlockState state
    ) {

        BlockPos immutablePos = pos.immutable();

        if (state.getBlock() instanceof InputEndpointBlock) {

            INPUTS.add(immutablePos);

        } else if (state.getBlock() instanceof OutputEndpointBlock) {

            OUTPUTS.add(immutablePos);

        } else {

            WIRES.add(immutablePos);
        }
    }

    public static void clear() {
        WIRES.clear();
        INPUTS.clear();
        OUTPUTS.clear();
    }

    public static Set<BlockPos> getWires() {
        return Collections.unmodifiableSet(WIRES);
    }

    public static Set<BlockPos> getInputs() {
        return Collections.unmodifiableSet(INPUTS);
    }

    public static Set<BlockPos> getOutputs() {
        return Collections.unmodifiableSet(OUTPUTS);
    }

    public static int getTotalSize() {
        return WIRES.size()
                + INPUTS.size()
                + OUTPUTS.size();
    }

    public static void setSelection(
            java.util.Collection<BlockPos> wires,
            java.util.Collection<BlockPos> inputs,
            java.util.Collection<BlockPos> outputs
    ) {
        clear();

        for (BlockPos pos : wires) {
            WIRES.add(pos.immutable());
        }

        for (BlockPos pos : inputs) {
            INPUTS.add(pos.immutable());
        }

        for (BlockPos pos : outputs) {
            OUTPUTS.add(pos.immutable());
        }
    }
}