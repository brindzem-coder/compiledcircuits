package com.example.compiledcircuits.item;

import com.example.compiledcircuits.block.DLatchBlock;
import com.example.compiledcircuits.block.EndpointBlock;
import com.example.compiledcircuits.network.NetworkIntegrityManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Reusable tool that moves the latch's enable port to the clicked side. */
public class WdePortItem extends Item {
    public WdePortItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DLatchBlock)) return InteractionResult.PASS;
        var side = context.getClickedFace();
        var player = context.getPlayer();
        if (player == null || !player.mayBuild()
                || !player.mayUseItemAt(pos, side, context.getItemInHand()))
            return InteractionResult.FAIL;
        if (side.getAxis() == state.getValue(EndpointBlock.FACING).getAxis())
            return InteractionResult.FAIL;
        var replacement = DLatchBlock.withEnableSide(state, side);
        if (!level.isClientSide && replacement != state) {
            if (!level.setBlock(pos, replacement, 3)) return InteractionResult.FAIL;
            // Same-block property edits do not always invoke placement/removal hooks.
            if (level instanceof ServerLevel server)
                NetworkIntegrityManager.scheduleCheck(server, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
