package com.example.compiledcircuits.block;

import net.minecraft.core.*;
import net.minecraft.world.level.LevelAccessor;

/** Ordinary conductor whose adjacent copies never connect directly. */
public final class IsolatedWireBlock extends NetworkWireBlock {
    public IsolatedWireBlock(Properties properties){super(properties);}
    @Override protected boolean canConnectTo(LevelAccessor level,BlockPos pos,Direction side){
        return !(level.getBlockState(pos.relative(side)).getBlock() instanceof IsolatedWireBlock)
                && super.canConnectTo(level,pos,side);
    }
}
