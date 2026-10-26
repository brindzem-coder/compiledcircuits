package com.example.compiledcircuits.item;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
public final class CrossableWireItem extends BlockItem {
    public CrossableWireItem(Block block,Properties properties){super(block,properties);}
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,net.minecraft.world.level.Level level,java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag){lines.add(net.minecraft.network.chat.Component.translatable("wire.compiledcircuits.crossing_hint"));}
}
