package com.example.compiledcircuits.client;

import net.minecraft.core.BlockPos;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ClientBrokenElements {
    private static final Set<BlockPos> BROKEN = new LinkedHashSet<>();
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
    public static void setFocused(Set<FocusedBrokenPos> positions) {
        if (!focused.equals(positions)) guiCloseCount = 0;
        focused = Collections.unmodifiableSet(new LinkedHashSet<>(positions));
    }
    public static void clearFocused() { focused = Set.of(); guiCloseCount = 0; }
    public static boolean hasFocused() { return !focused.isEmpty(); }
    public static Set<FocusedBrokenPos> getFocused() { return focused; }

    public static Set<BlockPos> getRenderPositions(String currentDimension) {
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
        BROKEN.clear();
        BROKEN.addAll(copy);
    }

    public static void clear() { BROKEN.clear(); dimension = ""; clearFocused(); }
    public static Set<BlockPos> getBroken() { return Collections.unmodifiableSet(BROKEN); }
    public static String getDimension() { return dimension; }
    public static boolean isEmpty() { return BROKEN.isEmpty(); }
}
