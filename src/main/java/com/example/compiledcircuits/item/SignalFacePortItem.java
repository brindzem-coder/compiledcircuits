package com.example.compiledcircuits.item;

import com.example.compiledcircuits.block.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Reusable face editor: switch outputs and lamp inputs only. */
public final class SignalFacePortItem extends Item {
    private final boolean input;
    public SignalFacePortItem(Properties properties, boolean input) { super(properties); this.input = input; }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel(); var pos = context.getClickedPos(); var side = context.getClickedFace();
        var state = level.getBlockState(pos); var player = context.getPlayer();
        if (!(input ? state.getBlock() instanceof SignalLampBlock : state.getBlock() instanceof SignalSwitchBlock)) return InteractionResult.PASS;
        if (player == null || !player.mayBuild() || !player.mayUseItemAt(pos, side, context.getItemInHand())) return InteractionResult.FAIL;
        if (level instanceof net.minecraft.server.level.ServerLevel server && !server.mayInteract(player,pos)) return InteractionResult.FAIL;
        if (!level.isClientSide && !level.setBlock(pos, SignalPorts.with(state, side, !SignalPorts.has(state, side)), 3)) return InteractionResult.FAIL;
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
