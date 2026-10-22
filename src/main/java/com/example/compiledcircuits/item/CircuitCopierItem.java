package com.example.compiledcircuits.item;
import com.example.compiledcircuits.network.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.List;
public final class CircuitCopierItem extends Item {
    public CircuitCopierItem(Properties properties){super(properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getPlayer() instanceof ServerPlayer player && player.isShiftKeyDown()){
            var network=NetworkSavedData.get(player.getServer()).findNetworkContaining(player.serverLevel(),context.getClickedPos());
            if(network!=null)BlueprintService.request(player,network.getId());
            else player.sendSystemMessage(Component.translatable("blueprint.compiledcircuits.no_network"));
        }else if(context.getLevel().isClientSide && context.getPlayer()!=null&&!context.getPlayer().isShiftKeyDown())
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.example.compiledcircuits.client.BlueprintClient.confirm());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,InteractionHand hand){
        if(level.isClientSide&&!player.isShiftKeyDown())net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.example.compiledcircuits.client.BlueprintClient.confirm());
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,List<Component> lines,TooltipFlag flag){
        if(stack.hasTag())lines.add(Component.literal(stack.getTag().getString("BlueprintName")));
        lines.add(Component.translatable("blueprint.compiledcircuits.hint"));
    }
}
