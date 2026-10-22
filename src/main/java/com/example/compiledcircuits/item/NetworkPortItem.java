package com.example.compiledcircuits.item;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.network.NetworkIntegrityManager;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;

public class NetworkPortItem extends Item {
    private final int kind;
    public NetworkPortItem(Properties properties,boolean input){super(properties);kind=input?2:3;}
    @Override public InteractionResult useOn(UseOnContext c){
        var level=c.getLevel();var pos=c.getClickedPos();var old=level.getBlockState(pos);var player=c.getPlayer();
        if(old.getBlock() instanceof com.example.compiledcircuits.block.BundledWireBlock || !(old.getBlock() instanceof BasicWireBlock))return InteractionResult.PASS;
        if(player==null||!player.mayBuild()||!player.mayUseItemAt(pos,c.getClickedFace(),c.getItemInHand()))return InteractionResult.FAIL;
        if(!level.isClientSide){
            var state=old.getBlock() instanceof NetworkWireBlock?old:ModBlocks.NETWORK_WIRE.get().defaultBlockState();
            state=NetworkWireBlock.withFace(state,c.getClickedFace(),NetworkWireBlock.face(state,c.getClickedFace())==kind?0:kind);
            state=Block.updateFromNeighbourShapes(state,level,pos);
            if(!level.setBlock(pos,state,3))return InteractionResult.FAIL;
            if(level instanceof ServerLevel server)NetworkIntegrityManager.scheduleCheck(server,pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
