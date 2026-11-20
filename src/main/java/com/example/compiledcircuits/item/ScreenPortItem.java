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

public final class ScreenPortItem extends Item {
    public ScreenPortItem(Properties properties){super(properties);}
    public static String base(ItemStack stack){try{return ScreenColors.base(stack.hasTag()?stack.getTag().getString("ChannelId"):"0");}catch(IllegalArgumentException e){return "0";}}
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown()){
            if(context.getPlayer() instanceof ServerPlayer p)BundleToolService.open(p,context.getHand());return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return BundlePortPlacement.apply(context,6,base(context.getItemInHand()));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){if(player instanceof ServerPlayer p)BundleToolService.open(p,hand);return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){String id=base(stack);lines.add(Component.translatable("screen.compiledcircuits.channels",id,ScreenColors.channel(id,7)));lines.add(Component.translatable("screen.compiledcircuits.hint"));}
}
