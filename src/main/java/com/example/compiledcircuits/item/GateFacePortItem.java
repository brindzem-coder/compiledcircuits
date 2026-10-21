package com.example.compiledcircuits.item;

import com.example.compiledcircuits.block.GateBodyBlock;
import com.example.compiledcircuits.network.NetworkIntegrityManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Reusable face editor; legacy port blocks remain readable in saved worlds. */
public class GateFacePortItem extends Item {
    private final int port;
    public GateFacePortItem(Properties properties, boolean input) { super(properties); port = input ? 1 : 2; }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel(); var pos = context.getClickedPos(); var side = context.getClickedFace();
        var state = level.getBlockState(pos); var player = context.getPlayer();
        if (!(state.getBlock() instanceof GateBodyBlock)) return InteractionResult.PASS;
        if (player == null || !player.mayBuild() || !player.mayUseItemAt(pos, side, context.getItemInHand())
                || level.getBlockState(pos.relative(side)).getBlock() instanceof GateBodyBlock) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            var replacement = GateBodyBlock.withPort(state, side, GateBodyBlock.port(state, side) == port ? 0 : port);
            if (!level.setBlock(pos, replacement, 3)) return InteractionResult.FAIL;
            if (level instanceof ServerLevel server) NetworkIntegrityManager.scheduleCheck(server, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
