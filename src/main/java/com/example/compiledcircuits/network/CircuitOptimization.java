package com.example.compiledcircuits.network;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CircuitOptimization {
    private CircuitOptimization() {}
    public static int optimize(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !NetworkOperations.canModify(player)) {
            source.sendFailure(Component.literal("You cannot modify circuits.")); return 0;
        }
        var data = NetworkSavedData.get(source.getServer()); var pos = NetworkSelectionData.get(player);
        var network = pos == null ? null : NetworkSelectionData.network(player);
        if (network == null) { source.sendFailure(Component.literal("Select a compiled network first.")); return 0; }
        try {
            String result = network.enableOptimization(); data.setDirty(); data.runtime().optimizationChanged(network);
            source.sendSuccess(() -> Component.literal("Network #" + network.getId() + " optimized. " + result
                    + " Compare /circuit benchmark baseline and /circuit benchmark."), false);
            return 1;
        } catch (IllegalStateException failure) {
            source.sendFailure(Component.literal("Cannot optimize invalid logic.")); return 0;
        }
    }
}
