package com.example.compiledcircuits.client;
import net.minecraft.core.BlockPos;
import java.util.Set;

public final class CircuitVisualPriorityTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    public static void main(String[] args) {
        var a = BlockPos.ZERO;
        var b = new BlockPos(1,0,0);
        var c = new BlockPos(2,0,0);
        ClientBrokenElements.clear();
        ClientNetworkSelection.setSelection(Set.of(a), Set.of(b), Set.of(c));
        ClientBrokenElements.setBroken("overworld", Set.of(a,b));
        ClientBrokenElements.setFocused(Set.of(new ClientBrokenElements.FocusedBrokenPos("overworld", a),
                new ClientBrokenElements.FocusedBrokenPos("nether", c)));
        var exclusions = CircuitVisualPriority.capture("overworld");
        check(exclusions.isBrokenReserved(a));
        check(exclusions.isBrokenReserved(b)); // Hidden by focus, still reserved.
        check(!exclusions.isBrokenReserved(c));
        check(exclusions.isExplicitlySelected(a));
        check(exclusions.isExplicitlySelected(b));
        check(exclusions.isExplicitlySelected(c));
        check(exclusions.suppressesOutline(c));
        check(ClientBrokenElements.getRenderPositions("overworld").equals(Set.of(a)));
        check(CircuitVisualPriority.capture("nether").isBrokenReserved(c));
        check(!CircuitVisualPriority.capture("nether").isBrokenReserved(a));
        ClientBrokenElements.onGuiClosed();
        for (int i=0;i<20;i++) CircuitVisualPriority.capture("overworld");
        check(ClientBrokenElements.hasFocused());
        ClientBrokenElements.onGuiClosed();
        check(!ClientBrokenElements.hasFocused());
        check(CircuitVisualPriority.capture("overworld").isBrokenReserved(b));
        ClientNetworkSelection.clear();
        check(!CircuitVisualPriority.capture("overworld").isExplicitlySelected(c));
        ClientBrokenElements.setBroken("overworld", Set.of());
        check(!CircuitVisualPriority.capture("overworld").suppressesOutline(a));
        ClientBrokenElements.clear();
        System.out.println("Priority checks passed: " + checks);
    }
}
