package com.example.compiledcircuits.item;

import com.example.compiledcircuits.block.BundlePortEntity;
import com.example.compiledcircuits.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public final class BundlePortItem extends Item {
    private final int kind;
    private final Block legacyBlock;
    public BundlePortItem(Block block, Properties properties) {
        super(properties); legacyBlock = block;
        var port = (com.example.compiledcircuits.block.BundlePortBlock) block;
        kind = (port.isExternal() ? 3 : 1) + (port.isInput() ? 0 : 1);
    }
    @Override public String getDescriptionId() { return legacyBlock.getDescriptionId(); }
    public static String channel(ItemStack stack) {
        try { return WireIds.normalize(stack.getOrCreateTag().getString("ChannelId")); }
        catch (IllegalArgumentException invalid) { return "0"; }
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            if (context.getPlayer() instanceof ServerPlayer player) BundleToolService.open(player, context.getHand());
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return BundlePortPlacement.apply(context, kind, channel(context.getItemInHand()));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) BundleToolService.open(server, hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("bundle.compiledcircuits.channel", channel(stack)));
        lines.add(Component.translatable("bundle.compiledcircuits.hint"));
    }
}
