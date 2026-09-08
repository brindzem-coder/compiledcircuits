package com.example.compiledcircuits.network;

import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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

        // Inputs remain current, but damaged outputs are already forced LOW.
        if (network.isDamaged()) {
            return;
        }

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

    public static void networkBecameDamaged(ServerLevel level, CompiledNetwork network) {
        notifyOutputs(level, network);
    }

    public static void networkBecameHealthy(ServerLevel level, CompiledNetwork network) {
        network.setPowered(calculatePowered(level, network));
        NetworkSavedData.get(level.getServer()).setDirty();

        // The effective output changed from forced LOW even if powered stayed true.
        notifyOutputs(level, network);
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

            /*
             * Повідомляємо vanilla blocks навколо Output,
             * що його redstone signal змінився.
             */
            level.updateNeighborsAt(
                    outputPos,
                    ModBlocks.OUTPUT_ENDPOINT.get()
            );
        }
    }
}
