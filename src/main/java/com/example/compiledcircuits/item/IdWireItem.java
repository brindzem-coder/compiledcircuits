package com.example.compiledcircuits.item;

import com.example.compiledcircuits.block.IdWireEntity;
import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public final class IdWireItem extends BlockItem {
    public IdWireItem(Properties properties) { super(ModBlocks.ID_WIRE.get(), properties); }
    public static String id(ItemStack stack) {
        try { return WireIds.normalize(stack.getOrCreateTag().getString("WireId")); }
        catch (IllegalArgumentException invalid) { return "0"; }
    }
    public static int color(ItemStack stack) { return id(stack).equals("0") ? WireIds.RED : stack.getOrCreateTag().getInt("WireColor") & 0xffffff; }
    public static void configure(ItemStack stack, String id, int color) {
        stack.getOrCreateTag().putString("WireId", id); stack.getOrCreateTag().putInt("WireColor", id.equals("0") ? WireIds.RED : color & 0xffffff);
    }
    @Override protected BlockState getPlacementState(BlockPlaceContext context) {
        if (!id(context.getItemInHand()).equals("0")) return super.getPlacementState(context);
        var state = ModBlocks.BASIC_WIRE.get().getStateForPlacement(context);
        return state != null && canPlace(context, state) ? state : null;
    }
    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        if (!super.placeBlock(context, state)) return false;
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof IdWireEntity wire)
            wire.configure(id(context.getItemInHand()), color(context.getItemInHand()));
        return true;
    }
    @Override protected boolean updateCustomBlockEntityTag(net.minecraft.core.BlockPos pos, Level level, Player player, ItemStack stack, BlockState state) {
        return false; // Identity comes only from the validated tool settings, never arbitrary BlockEntityTag.
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            if (context.getPlayer() instanceof ServerPlayer player) WireToolService.open(player, context.getHand());
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) WireToolService.open(server, hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("ID " + id(stack)));
        lines.add(Component.translatable("wire.compiledcircuits.hint"));
    }
}
