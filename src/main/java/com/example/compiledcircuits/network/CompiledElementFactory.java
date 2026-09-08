package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CompiledElementFactory {

    private CompiledElementFactory() {
    }

    public static List<CompiledCircuitElement> create(
            ServerLevel level,
            Set<BlockPos> wires,
            Set<BlockPos> inputs,
            Set<BlockPos> outputs
    ) {
        List<ElementCandidate> candidates = new ArrayList<>();

        for (BlockPos pos : wires) {
            candidates.add(new ElementCandidate(pos, CircuitElementType.WIRE));
        }

        for (BlockPos pos : inputs) {
            candidates.add(new ElementCandidate(pos, CircuitElementType.INPUT));
        }

        for (BlockPos pos : outputs) {
            candidates.add(new ElementCandidate(pos, CircuitElementType.OUTPUT));
        }

        // Scanner bug guard: одна позиція не може мати два element types.
        Set<BlockPos> seen = new HashSet<>();

        for (ElementCandidate candidate : candidates) {
            if (!seen.add(candidate.pos())) {
                throw new IllegalStateException(
                        "Duplicate compiled circuit position: " + candidate.pos()
                );
            }
        }

        // HashSet iteration order unstable, тому ID призначаємо детерміновано.
        candidates.sort(
                Comparator
                        .comparingInt((ElementCandidate c) -> c.pos().getX())
                        .thenComparingInt(c -> c.pos().getY())
                        .thenComparingInt(c -> c.pos().getZ())
                        .thenComparing(c -> c.type().name())
        );

        List<CompiledCircuitElement> result =
                new ArrayList<>(candidates.size());

        int nextId = 1;

        for (ElementCandidate candidate : candidates) {
            BlockState state = level.getBlockState(candidate.pos());

            ResourceLocation key =
                    BuiltInRegistries.BLOCK.getKey(state.getBlock());

            result.add(
                    new CompiledCircuitElement(
                            nextId++,
                            candidate.pos(),
                            candidate.type(),
                            key.toString()
                    )
            );
        }

        return result;
    }

    private record ElementCandidate(
            BlockPos pos,
            CircuitElementType type
    ) {
    }
}
