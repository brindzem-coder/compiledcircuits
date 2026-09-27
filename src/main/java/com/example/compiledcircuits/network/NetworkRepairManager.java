package com.example.compiledcircuits.network;

import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;

public final class NetworkRepairManager {
    private NetworkRepairManager() {}
    /** repaired counts placements; integrity checks confirm actual repairs later. */
    public record RepairResult(int repaired, int skippedOccupied, int skippedUnsupported, int failed, int alreadyCorrect, int skippedUnloaded, int invalidState) {}

    public static RepairResult repairNetwork(ServerLevel level, CompiledNetwork network, ServerPlayer player) {
        long diagnosticStart = PerformanceDiagnostics.begin();
        PerformanceDiagnostics.add("repair.calls", 1);
        try {
        int repaired = 0, occupied = 0, unsupported = 0, failed = 0, correct = 0, unloaded = 0, invalid = 0;
        if (!level.dimension().location().toString().equals(network.getDimension())) {
            return diagnosticResult(0, 0, 0, network.getBrokenElements().size(), 0, 0, 0);
        }
        for (var broken : new ArrayList<>(network.getBrokenElements())) {
            PerformanceDiagnostics.add("repair.elements", 1);
            var element = network.getElement(broken.getElementId());
            if (element == null) { failed++; continue; }
            var decoded = element.resolveState();
            if (decoded.status() == CompiledBlockStateCodec.Status.UNRESOLVED) { invalid++; continue; }
            ResourceLocation id = ResourceLocation.tryParse(element.getBlockId());
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) { failed++; continue; }
            Block expected = BuiltInRegistries.BLOCK.get(id);
            if (!isSupportedForAutoRepair(expected)) { unsupported++; continue; }
            var pos = element.getPos();
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.mayInteract(player, pos)) { failed++; continue; }
            if (!level.hasChunkAt(pos)) { unloaded++; continue; }
            var actual = level.getBlockState(pos);
            var match = CompiledBlockStateMatcher.match(element, actual, NetworkIntegrityManager.exactIntegrityEnabled());
            if (match == CompiledBlockStateMatcher.Match.MATCH) {
                correct++;
                NetworkIntegrityManager.scheduleCheck(level, pos);
                continue;
            }
            if (match == CompiledBlockStateMatcher.Match.STATE_MISMATCH) { occupied++; continue; }
            if (!(actual.isAir() || actual.canBeReplaced()) || actual.hasBlockEntity()) { occupied++; continue; }
            // Exact endpoints keep their snapshot; only legacy elements fall back to defaults.
            var restored = decoded.state().orElseGet(expected::defaultBlockState);
            if (expected == ModBlocks.BASIC_WIRE.get()) restored = Block.updateFromNeighbourShapes(restored, level, pos);
            if (!restored.is(expected)) { failed++; continue; }
            if (!level.setBlock(pos, restored, Block.UPDATE_ALL)) { failed++; continue; }
            repaired++;
            NetworkIntegrityManager.scheduleCheck(level, pos);
            for (var direction : net.minecraft.core.Direction.values()) NetworkIntegrityManager.scheduleCheck(level, pos.relative(direction));
        }
        return diagnosticResult(repaired, occupied, unsupported, failed, correct, unloaded, invalid);

        } finally { PerformanceDiagnostics.elapsed("repair", diagnosticStart); }
    }

    private static RepairResult diagnosticResult(int repaired, int occupied, int unsupported, int failed, int correct, int unloaded, int invalid) {
        PerformanceDiagnostics.add("repair.placements", repaired);
        PerformanceDiagnostics.add("repair.occupied", occupied);
        PerformanceDiagnostics.add("repair.unsupported", unsupported);
        PerformanceDiagnostics.add("repair.failed", failed);
        PerformanceDiagnostics.add("repair.alreadyCorrect", correct);
        PerformanceDiagnostics.add("repair.unloaded", unloaded);
        PerformanceDiagnostics.add("repair.invalid", invalid);
        return new RepairResult(repaired, occupied, unsupported, failed, correct, unloaded, invalid);
    }
    private static boolean isSupportedForAutoRepair(Block block) {
        return block == ModBlocks.BASIC_WIRE.get() || block == ModBlocks.INPUT_ENDPOINT.get()
                || block == ModBlocks.OUTPUT_ENDPOINT.get();
    }
}
