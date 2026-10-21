package com.example.compiledcircuits.item;

import com.example.compiledcircuits.block.GateBodyBlock;
import com.example.compiledcircuits.network.NetworkIntegrityManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** A sealed face occupies the same face slot as a port and survives adjacent bodies. */
public class GateSeparatorItem extends Item {
    public GateSeparatorItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel(); var pos = context.getClickedPos(); var side = context.getClickedFace();
        var state = level.getBlockState(pos); var player = context.getPlayer();
        if (!(state.getBlock() instanceof GateBodyBlock)) return InteractionResult.PASS;
        if (player == null || !player.mayBuild() || !player.mayUseItemAt(pos, side, context.getItemInHand()))
            return InteractionResult.FAIL;
        if (!level.isClientSide) {
            if (!level.setBlock(pos, GateBodyBlock.withPort(state, side, GateBodyBlock.port(state, side) == 3 ? 0 : 3), 3))
                return InteractionResult.FAIL;
            if (level instanceof ServerLevel server) {
                NetworkIntegrityManager.scheduleCheck(server, pos);
                NetworkIntegrityManager.scheduleCheck(server, pos.relative(side));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
