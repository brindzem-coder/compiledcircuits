package com.example.compiledcircuits.client;

import net.minecraft.core.BlockPos;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ClientBrokenElements {
    private static final Set<BlockPos> BROKEN = new LinkedHashSet<>();
    private static String dimension = "";
    private ClientBrokenElements() {}

    public static void setBroken(String dimensionId, Set<BlockPos> positions) {
        Set<BlockPos> copy = new LinkedHashSet<>();
        for (BlockPos pos : positions) copy.add(pos.immutable());
        dimension = dimensionId;
        BROKEN.clear();
        BROKEN.addAll(copy);
    }

    public static void clear() { BROKEN.clear(); dimension = ""; }
    public static Set<BlockPos> getBroken() { return Collections.unmodifiableSet(BROKEN); }
    public static String getDimension() { return dimension; }
    public static boolean isEmpty() { return BROKEN.isEmpty(); }
}
