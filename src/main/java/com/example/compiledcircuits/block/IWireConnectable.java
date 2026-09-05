package com.example.compiledcircuits.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public interface IWireConnectable {

    boolean canWireConnect(BlockState state, Direction side);
}