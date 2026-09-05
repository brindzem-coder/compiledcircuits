package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.IWireConnectable;
import com.example.compiledcircuits.block.InputEndpointBlock;
import com.example.compiledcircuits.block.OutputEndpointBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public final class NetworkScanner {

    private static final int MAX_SCAN_SIZE = 50_000;

    private NetworkScanner() {
    }

    public static ScanResult scan(
            ServerLevel level,
            BlockPos startPos
    ) {

        Set<BlockPos> wires = new HashSet<>();
        Set<BlockPos> inputs = new HashSet<>();
        Set<BlockPos> outputs = new HashSet<>();

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        BlockState startState =
                level.getBlockState(startPos);

        if (!(startState.getBlock()
                instanceof IWireConnectable)) {

            return new ScanResult(
                    wires,
                    inputs,
                    outputs,
                    false
            );
        }

        queue.add(startPos.immutable());

        while (!queue.isEmpty()) {

            BlockPos currentPos =
                    queue.remove();

            if (!visited.add(currentPos)) {
                continue;
            }

            if (visited.size() > MAX_SCAN_SIZE) {

                return new ScanResult(
                        wires,
                        inputs,
                        outputs,
                        false
                );
            }

            BlockState currentState =
                    level.getBlockState(currentPos);

            if (!(currentState.getBlock()
                    instanceof IWireConnectable currentConnectable)) {

                continue;
            }

            if (currentState.getBlock()
                    instanceof InputEndpointBlock) {

                inputs.add(currentPos.immutable());

            } else if (currentState.getBlock()
                    instanceof OutputEndpointBlock) {

                outputs.add(currentPos.immutable());

            } else {

                wires.add(currentPos.immutable());
            }

            for (Direction direction
                    : Direction.values()) {

                BlockPos neighborPos =
                        currentPos.relative(direction);

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

                    queue.add(
                            neighborPos.immutable()
                    );
                }
            }
        }

        return new ScanResult(
                wires,
                inputs,
                outputs,
                true
        );
    }

    public record ScanResult(
            Set<BlockPos> wires,
            Set<BlockPos> inputs,
            Set<BlockPos> outputs,
            boolean success
    ) {

        public int totalSize() {
            return wires.size()
                    + inputs.size()
                    + outputs.size();
        }
    }
}