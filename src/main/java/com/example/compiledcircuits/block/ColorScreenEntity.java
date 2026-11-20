package com.example.compiledcircuits.block;

import com.example.compiledcircuits.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Color is transient; load starts black and reconciles with the compiled graph. */
public final class ColorScreenEntity extends BlockEntity {
    private int color;
    public ColorScreenEntity(BlockPos pos,BlockState state){super(ModBlockEntities.COLOR_SCREEN.get(),pos,state);}
    public int color(){return color;}
    public void setColor(int value){value&=255;if(color==value)return;color=value;if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override public CompoundTag getUpdateTag(){var tag=new CompoundTag();tag.putInt("Color",color);return tag;}
    @Override public void load(CompoundTag tag){super.load(tag);color=level!=null&&level.isClientSide?tag.getInt("Color")&255:0;}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel server)server.getServer().execute(()->{if(!isRemoved()&&server.hasChunkAt(worldPosition))server.scheduleTick(worldPosition,getBlockState().getBlock(),1);});}
}
