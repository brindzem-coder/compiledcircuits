package com.example.compiledcircuits.client;

import net.minecraft.core.BlockPos;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ClientBrokenElements {
    private static Set<BlockPos> BROKEN = Set.of();
    private static boolean current=true;
    static void confirmed(boolean value){current=value;}
    static void publish(String dim,Set<BlockPos> prepared){dimension=dim;BROKEN=Collections.unmodifiableSet(prepared);current=true;}
    public record FocusedBrokenPos(String dimension, BlockPos pos) {
        public FocusedBrokenPos { pos = pos.immutable(); }
    }
    private static Set<FocusedBrokenPos> focused = Set.of();
    private static int guiCloseCount;
    public static void onGuiClosed() {
        if (hasFocused() && ++guiCloseCount >= 2) clearFocused();
    }
    public static void retainFocused(Set<FocusedBrokenPos> valid) {
        Set<FocusedBrokenPos> remaining = new LinkedHashSet<>(focused);
        remaining.retainAll(valid);
        focused = Collections.unmodifiableSet(remaining);
        if (focused.isEmpty()) guiCloseCount = 0;
    }
    public static void retainFocused(java.util.function.Predicate<FocusedBrokenPos> valid) {
        Set<FocusedBrokenPos> remaining = new LinkedHashSet<>(focused);
        remaining.removeIf(valid.negate());
        focused = Collections.unmodifiableSet(remaining);
        if (focused.isEmpty()) guiCloseCount = 0;
    }
    public static void setFocused(Set<FocusedBrokenPos> positions) {
        if (!focused.equals(positions)) guiCloseCount = 0;
        focused = Collections.unmodifiableSet(new LinkedHashSet<>(positions));
    }
    public static void clearFocused() { focused = Set.of(); guiCloseCount = 0; }
    public static boolean hasFocused() { return !focused.isEmpty(); }
    public static Set<FocusedBrokenPos> getFocused() { return current?focused:Set.of(); }

    public static Set<BlockPos> getRenderPositions(String currentDimension) {
        if(!current)return Set.of();
        if (!hasFocused()) return dimension.equals(currentDimension) ? getBroken() : Set.of();
        Set<BlockPos> result = new LinkedHashSet<>();
        for (FocusedBrokenPos entry : focused) {
            if (entry.dimension().equals(currentDimension)) result.add(entry.pos());
        }
        return result;
    }

    private static String dimension = "";
    private ClientBrokenElements() {}

    public static void setBroken(String dimensionId, Set<BlockPos> positions) {
        Set<BlockPos> copy = new LinkedHashSet<>();
        for (BlockPos pos : positions) copy.add(pos.immutable());
        dimension = dimensionId;
        BROKEN=Collections.unmodifiableSet(copy);current=true;
    }

    public static void clear() { BROKEN=Set.of(); dimension = ""; clearFocused(); }
    public static Set<BlockPos> getBroken() { return current?BROKEN:Set.of(); }
    public static String getDimension() { return dimension; }
    public static boolean isEmpty() { return BROKEN.isEmpty(); }
}
