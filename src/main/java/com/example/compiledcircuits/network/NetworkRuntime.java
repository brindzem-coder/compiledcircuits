package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.OutputEndpointBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class NetworkRuntime {

    private NetworkRuntime() {
    }

    public static void inputChanged(
            ServerLevel level,
            BlockPos inputPos
    ) {

        NetworkSavedData data =
                NetworkSavedData.get(
                        level.getServer()
                );

        CompiledNetwork network =
                data.findNetworkByInput(
                        level,
                        inputPos
                );

        if (network == null) {
            return;
        }

        boolean newPowered =
                calculatePowered(
                        level,
                        network
                );

        if (network.isPowered() == newPowered) {
            return;
        }

        network.setPowered(newPowered);

        /*
         * Зберігаємо новий стан.
         */
        data.setDirty();

        /*
         * Важлива частина:
         *
         * Ми НЕ оновлюємо wires.
         * Одразу повідомляємо тільки outputs.
         */
        notifyOutputs(
                level,
                network
        );
    }

    private static boolean calculatePowered(
            ServerLevel level,
            CompiledNetwork network
    ) {

        /*
         * Базова семантика:
         *
         * будь-який Input > 0
         *        ↓
         * Network = HIGH
         */
        for (BlockPos inputPos
                : network.getInputs()) {

            int signal =
                    level.getBestNeighborSignal(
                            inputPos
                    );

            if (signal > 0) {
                return true;
            }
        }

        return false;
    }

    private static void notifyOutputs(
            ServerLevel level,
            CompiledNetwork network
    ) {

        for (BlockPos outputPos
                : network.getOutputs()) {

            if (!level.hasChunkAt(outputPos)) {
                continue;
            }

            BlockState outputState =
                    level.getBlockState(
                            outputPos
                    );

            if (!(outputState.getBlock()
                    instanceof OutputEndpointBlock)) {
                continue;
            }

            /*
             * Повідомляємо vanilla blocks навколо Output,
             * що його redstone signal змінився.
             */
            level.updateNeighborsAt(
                    outputPos,
                    outputState.getBlock()
            );
        }
    }
}