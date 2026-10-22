package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** An electrical terminal is a face, not merely a block position. */
public record BundleFace(BlockPos pos, Direction side) {
    public BundleFace { pos = pos.immutable(); }
    public record Link(BundleFace from, BundleFace to) {}
}
