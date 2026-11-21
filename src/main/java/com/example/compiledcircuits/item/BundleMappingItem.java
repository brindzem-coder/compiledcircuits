package com.example.compiledcircuits.item;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.block.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class BundleMappingItem extends Item {
    private final int kind;
    public BundleMappingItem(Properties properties,int kind){super(properties);this.kind=kind;}
    public int kind(){return kind;}
    public static String rules(ItemStack stack){
        try{return ChannelMapping.parse(stack.hasTag()?stack.getTag().getString("ChannelMapping"):"0-7").text();}
        catch(IllegalArgumentException invalid){return "0-7";}
    }
    @Override public InteractionResult useOn(UseOnContext c){
        if(c.getPlayer()!=null&&c.getPlayer().isShiftKeyDown()){
            if(c.getPlayer() instanceof ServerPlayer p)BundleToolService.open(p,c.getHand());
            return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
        }
        String rules=rules(c.getItemInHand());
        if(kind==9){
            var side=c.getClickedFace();var existing=BundleConnections.port(c.getLevel(),c.getClickedPos(),side);
            // A receiver is configured from the facing output. Existing receivers can be removed even if disconnected.
            if(existing!=null&&existing.kind()==9)rules=existing.id();
            else {
                var source=BundleConnections.port(c.getLevel(),c.getClickedPos().relative(side),side.getOpposite());
                if(source==null||source.kind()!=8){if(c.getPlayer() instanceof ServerPlayer p)p.sendSystemMessage(Component.translatable("mapping.compiledcircuits.receiver_needs_output"));return InteractionResult.FAIL;}
                rules=source.id();
            }
        }
        var result=BundlePortPlacement.apply(c,kind,rules);
        if(kind==9 && result.consumesAction() && !c.getLevel().isClientSide){
            c.getItemInHand().getOrCreateTag().putString("ChannelMapping",rules);
            if(c.getPlayer() instanceof ServerPlayer p)p.containerMenu.broadcastChanges();
        }
        return result;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(player instanceof ServerPlayer p)BundleToolService.open(p,hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        lines.add(Component.literal(rules(stack)));lines.add(Component.translatable(kind==9?"mapping.compiledcircuits.receiver_hint":"mapping.compiledcircuits.hint"));
    }
}
