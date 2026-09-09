package com.example.compiledcircuits.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.Set;

public final class ClientHoveredCircuitTest {
    private static int checks;
    private static final Object LEVEL = new Object();
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    private static ClientHoveredCircuit.Edges line(int size) {
        return (from, direction) -> {
            BlockPos to = from.relative(direction);
            return to.getY() == 0 && to.getZ() == 0 && to.getX() >= 0 && to.getX() < size;
        };
    }
    private static void tick(int size, long revision) {
        ClientHoveredCircuit.update(LEVEL, "test", BlockPos.ZERO, revision, 0, null, line(size));
        check(ClientHoveredCircuit.getBfsNodesThisTick() <= 1024);
    }
    private static void finish(int size) {
        for (int i = 0; i < 60 && ClientHoveredCircuit.getStatus() == ClientHoveredCircuit.Status.PENDING; i++) tick(size, 0);
    }
    public static void main(String[] args) {
        ClientHoveredCircuit.reset();
        tick(2500, 0);
        check(ClientHoveredCircuit.getStatus() == ClientHoveredCircuit.Status.PENDING);
        check(ClientHoveredCircuit.getPositions().isEmpty());
        check(ClientHoveredCircuit.getBfsNodesThisTick() == 1024);
        finish(2500);
        check(ClientHoveredCircuit.getPositions().size() == 2500);
        Set<BlockPos> result = ClientHoveredCircuit.getPositions();
        tick(2500, 0);
        check(ClientHoveredCircuit.getPositions() == result);
        check(ClientHoveredCircuit.getBfsStarted() == 1);
        check(ClientHoveredCircuit.getBfsNodesThisTick() == 0);
        try { result.clear(); throw new AssertionError("Mutable published result"); }
        catch (UnsupportedOperationException expected) { checks++; }
        tick(2500, 1);
        check(ClientHoveredCircuit.getBfsStarted() == 2 && ClientHoveredCircuit.getPositions().isEmpty());
        ClientHoveredCircuit.update(LEVEL, "test", BlockPos.ZERO, 1, 1, null, line(2500));
        check(ClientHoveredCircuit.getBfsStarted() == 3);
        ClientHoveredCircuit.update(LEVEL, "test", new BlockPos(1,0,0), 1, 1, null, line(2500));
        check(ClientHoveredCircuit.getBfsStarted() == 4);
        ClientHoveredCircuit.update(new Object(), "test", BlockPos.ZERO, 1, 1, null, line(2500));
        check(ClientHoveredCircuit.getBfsStarted() == 5);
        ClientHoveredCircuit.clear();
        check(ClientHoveredCircuit.getStatus() == ClientHoveredCircuit.Status.EMPTY && ClientHoveredCircuit.getPositions().isEmpty());
        Set<BlockPos> compiled = Set.of(BlockPos.ZERO, new BlockPos(100000,0,0));
        ClientHoveredCircuit.update(LEVEL, "test", BlockPos.ZERO, 2, 2, compiled, (p,d) -> { throw new AssertionError("Compiled BFS"); });
        check(ClientHoveredCircuit.getPositions() == compiled && ClientHoveredCircuit.getStatus() == ClientHoveredCircuit.Status.READY);
        check(ClientHoveredCircuit.getBfsStarted() == 5);
        ClientHoveredCircuit.reset();
        tick(50000, 0); finish(50000);
        check(ClientHoveredCircuit.getStatus() == ClientHoveredCircuit.Status.READY && ClientHoveredCircuit.getPositions().size() == 50000);
        ClientHoveredCircuit.reset();
        tick(50001, 0); finish(50001);
        check(ClientHoveredCircuit.getStatus() == ClientHoveredCircuit.Status.TOO_LARGE && ClientHoveredCircuit.getPositions().isEmpty());
        tick(50001, 0);
        check(ClientHoveredCircuit.getBfsStarted() == 1 && ClientHoveredCircuit.getBfsNodesThisTick() == 0);
        tick(1, 1);
        check(ClientHoveredCircuit.getStatus() == ClientHoveredCircuit.Status.READY && ClientHoveredCircuit.getPositions().size() == 1);
        ClientHoveredCircuit.reset();
        System.out.println("Hover checks passed: " + checks);
    }
}
