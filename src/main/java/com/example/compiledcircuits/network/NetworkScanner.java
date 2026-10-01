package com.example.compiledcircuits.network;

import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
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

    private static final int MAX_SCAN_SIZE = OperationLimits.ELEMENTS;

    private NetworkScanner() {
    }

    public static ScanResult scan(
            ServerLevel level,
            BlockPos startPos
    ) {
        long diagnosticStart = PerformanceDiagnostics.begin();
        PerformanceDiagnostics.add("scan.calls", 1);
        try {

        Set<BlockPos> wires = new HashSet<>();
        Set<BlockPos> inputs = new HashSet<>();
        Set<BlockPos> outputs = new HashSet<>();

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        if (!level.hasChunkAt(startPos)) return diagnosticResult(wires, inputs, outputs, false);
        BlockState startState =
                diagnosticRead(level, startPos);

        if (!(startState.getBlock()
                instanceof IWireConnectable)) {

            return diagnosticResult(
                    wires,
                    inputs,
                    outputs,
                    false
            );
        }

        queue.add(startPos.immutable());
        PerformanceDiagnostics.max("scan.queuePeak", queue.size());

        while (!queue.isEmpty()) {

            BlockPos currentPos =
                    queue.remove();

            PerformanceDiagnostics.add("scan.queuePops", 1);
            if (!visited.add(currentPos)) {
                continue;
            }

            PerformanceDiagnostics.add("scan.visited", 1);
            PerformanceDiagnostics.max("scan.queuePeak", queue.size());
            if (visited.size() > MAX_SCAN_SIZE) {

                return diagnosticResult(
                        wires,
                        inputs,
                        outputs,
                        false
                );
            }

            BlockState currentState =
                    diagnosticRead(level, currentPos);

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

                if (!level.hasChunkAt(neighborPos)) return diagnosticResult(wires, inputs, outputs, false);
                BlockState neighborState =
                        diagnosticRead(level, neighborPos);

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
                    PerformanceDiagnostics.max("scan.queuePeak", queue.size());
                }
            }
        }

        return diagnosticResult(
                wires,
                inputs,
                outputs,
                true
        );

        } finally { PerformanceDiagnostics.elapsed("scan", diagnosticStart); }
    }

    private static BlockState diagnosticRead(ServerLevel level, BlockPos pos) {
        PerformanceDiagnostics.add("scan.blockReads", 1);
        return level.getBlockState(pos);
    }
    private static ScanResult diagnosticResult(Set<BlockPos> wires, Set<BlockPos> inputs, Set<BlockPos> outputs, boolean success) {
        PerformanceDiagnostics.add(success ? "scan.success" : "scan.failure", 1);
        return new ScanResult(wires, inputs, outputs, success);
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
