package com.example.compiledcircuits.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.example.compiledcircuits.network.NetworkIntegrityManager;

/** Structural blocks join only bodies of the same kind and gate ports. */
public class GateBodyBlock extends Block implements IWireConnectable {
    public enum Kind { AND, OR, NAND, NOR, XOR, XNOR }
    private final Kind kind;
    public GateBodyBlock(Properties properties, Kind kind) { super(properties); this.kind = kind; }
    public Kind kind() { return kind; }
    @Override public boolean canWireConnect(BlockState state, Direction side) { return false; }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server) NetworkIntegrityManager.scheduleCheck(server, pos);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (level instanceof ServerLevel server) NetworkIntegrityManager.scheduleCheck(server, pos);
        super.onRemove(state, level, pos, replacement, moving);
    }
}
