package com.example.compiledcircuits.block;

import com.example.compiledcircuits.network.NetworkSavedData;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

public final class ColorScreenBlock extends OutputEndpointBlock implements EntityBlock {
    public ColorScreenBlock(Properties properties){super(properties);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ColorScreenEntity(pos,state);}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getNearestLookingDirection().getOpposite());}
    @Override public boolean canWireConnect(BlockState state,Direction side){return side==state.getValue(FACING).getOpposite();}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.block();}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.block();}
    @Override public boolean isSignalSource(BlockState state){return false;}
    @Override public int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return 0;}
    @Override public int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return 0;}
    public static void refresh(ServerLevel level,BlockPos pos){
        if(!(level.getBlockEntity(pos) instanceof ColorScreenEntity screen))return;
        var network=NetworkSavedData.get(level.getServer()).findNetworkByOutput(level,pos);
        screen.setColor(network==null?0:network.screenColor(pos));
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){super.onPlace(state,level,pos,old,moving);if(!state.is(old.getBlock())&&!level.isClientSide)level.scheduleTick(pos,this,1);}
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random){refresh(level,pos);}
}
