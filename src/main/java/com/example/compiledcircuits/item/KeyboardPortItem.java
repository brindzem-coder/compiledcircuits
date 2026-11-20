package com.example.compiledcircuits.item;

import com.example.compiledcircuits.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

public final class KeyboardPortItem extends Item {
    public KeyboardPortItem(Properties properties) { super(properties); }
    public static KeyboardBinding binding(ItemStack stack) {
        try { return KeyboardBinding.parse(stack.hasTag() ? stack.getTag().getString("KeyboardBinding") : "87"); }
        catch (IllegalArgumentException ignored) { return KeyboardBinding.parse("87"); }
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            if (context.getPlayer() instanceof ServerPlayer player) BundleToolService.open(player,context.getHand());
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return BundlePortPlacement.apply(context,5,binding(context.getItemInHand()).encode());
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) BundleToolService.open(server,hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal(binding(stack).label()));
        lines.add(Component.translatable("keyboard.compiledcircuits.port_hint"));
    }
}
