package com.example.compiledcircuits.client;

import net.minecraft.core.BlockPos;
import java.util.HashSet;
import java.util.Set;

/** Read-only exclusions prepared once per renderer invocation. */
public final class CircuitVisualPriority {
    private CircuitVisualPriority() {}
    public record Exclusions(Set<BlockPos> broken, Set<BlockPos> wires,
                             Set<BlockPos> inputs, Set<BlockPos> outputs) {
        public boolean isBrokenReserved(BlockPos pos) { return broken.contains(pos); }
        public boolean isExplicitlySelected(BlockPos pos) {
            return wires.contains(pos) || inputs.contains(pos) || outputs.contains(pos);
        }
        public boolean suppressesOutline(BlockPos pos) {
            return isBrokenReserved(pos) || isExplicitlySelected(pos);
        }
    }
    public static Exclusions capture(String dimension) {
        Set<BlockPos> broken = new HashSet<>();
        if (dimension.equals(ClientBrokenElements.getDimension())) broken.addAll(ClientBrokenElements.getBroken());
        for (var focused : ClientBrokenElements.getFocused()) {
            if (dimension.equals(focused.dimension())) broken.add(focused.pos());
        }
        // Selection renderer is active whenever these sets are nonempty in the active level.
        return new Exclusions(Set.copyOf(broken), ClientNetworkSelection.getWires(),
                ClientNetworkSelection.getInputs(), ClientNetworkSelection.getOutputs());
    }
}
