package com.example.compiledcircuits.network;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.item.IdWireItem;
import com.example.compiledcircuits.registry.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

public final class CrossingPlacement {
    private CrossingPlacement(){}
    public static InteractionResult place(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();var old=level.getBlockState(pos);var stack=context.getItemInHand();
        if(context.getPlayer()==null||context.getPlayer().isShiftKeyDown())return InteractionResult.PASS;
        if(!(old.getBlock() instanceof BasicWireBlock||old.getBlock() instanceof CrossingWireBlock))return InteractionResult.PASS;
        int kind=stack.is(ModItems.BUNDLED_WIRE.get())?3:stack.is(ModItems.ID_WIRE.get())&&!IdWireItem.id(stack).equals("0")?2:1;
        var axis=context.getClickedFace().getAxis();var state=old;
        if(old.getBlock() instanceof CrossingWireBlock){
            if(CrossingWireBlock.kind(old,axis)!=0)return InteractionResult.PASS;
            if(Integer.bitCount(CrossingWireBlock.mask(old))>=2)return error(context,"Only two wires fit in one block.");
        }else{
            if(old.getBlock() instanceof NetworkWireBlock)for(var side:Direction.values())if(NetworkWireBlock.face(old,side)>=2)return error(context,"Remove the ports before adding a crossing.");
            if(level.getBlockEntity(pos) instanceof BundleWireEntity bundle&&!bundle.ports().isEmpty())return error(context,"Remove the ports before adding a crossing.");
            Direction.Axis existing=null;
            for(var side:Direction.values()){
                var next=pos.relative(side);if(!level.hasChunkAt(next))return error(context,"Load the surrounding area first.");
                if(CircuitConnections.connects(level,pos,old,level.getBlockState(next),side)){
                    if(existing!=null&&existing!=side.getAxis())return error(context,"Crossings require a straight wire without branches.");existing=side.getAxis();
                }
            }
            if(existing==axis)return InteractionResult.PASS;
            if(existing==null)for(var a:Direction.Axis.values())if(a!=axis&&a!=Direction.Axis.Y){existing=a;break;}
            if(existing==null)existing=Direction.Axis.Y;
            int originalKind=old.getBlock() instanceof BundledWireBlock?3:old.getBlock() instanceof IdWireBlock?2:1;
            state=ModBlocks.CROSSING_WIRE.get().defaultBlockState().setValue(CrossingWireBlock.LANES[existing.ordinal()],originalKind);
        }
        state=state.setValue(CrossingWireBlock.LANES[axis.ordinal()],kind);
        if(level.isClientSide)return InteractionResult.SUCCESS;
        var player=(ServerPlayer)context.getPlayer();var server=player.serverLevel();var data=NetworkSavedData.get(player.getServer());
        if(!NetworkOperations.canModify(player)||!server.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack))return InteractionResult.FAIL;
        if(!(old.getBlock() instanceof CrossingWireBlock)&&!Crossings.free(data,server.dimension().location().toString(),pos,7))return error(context,"Decompile this wire before converting it to a crossing.");
        if(!Crossings.free(data,server.dimension().location().toString(),pos,1<<axis.ordinal()))return error(context,"This lane belongs to a compilation.");
        var lanes=new net.minecraft.nbt.CompoundTag();
        if(level.getBlockEntity(pos) instanceof CrossingWireEntity wire)lanes=wire.snapshot(7);
        else for(var a:Direction.Axis.values())if(CrossingWireBlock.kind(state,a)!=0&&a!=axis){
            var tag=new net.minecraft.nbt.CompoundTag();tag.putString("id",old.getBlock() instanceof IdWireBlock?((IdWireEntity)level.getBlockEntity(pos)).wireId():"0");
            tag.putInt("color",old.getBlock() instanceof IdWireBlock?((IdWireEntity)level.getBlockEntity(pos)).color():old.getBlock() instanceof BundledWireBlock?0x555555:WireIds.RED);lanes.put(a.getName(),tag);
        }
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString("id",kind==2?IdWireItem.id(stack):"0");tag.putInt("color",kind==2?IdWireItem.color(stack):kind==3?0x555555:WireIds.RED);lanes.put(axis.getName(),tag);
        var saved=lanes;var placed=state;
        if(server.captureBlockSnapshots){if(!server.setBlock(pos,placed,3))return InteractionResult.FAIL;((CrossingWireEntity)server.getBlockEntity(pos)).restore(saved);}
        else if(!ProtectedRepairPlacement.place(server,pos,placed,player,()->NetworkOperations.canModify(player),()->((CrossingWireEntity)server.getBlockEntity(pos)).restore(saved)))return InteractionResult.FAIL;
        if(!player.getAbilities().instabuild)stack.shrink(1);
        NetworkIntegrityManager.scheduleCheck(server,pos);return InteractionResult.CONSUME;
    }
    public static InteractionResult convertSingle(UseOnContext context,boolean bundled){
        var level=context.getLevel();var pos=context.getClickedPos();var state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof CrossingWireBlock))return InteractionResult.PASS;
        if(Integer.bitCount(CrossingWireBlock.mask(state))!=1)return error(context,"Ports require a block containing only one wire.");
        var axis=java.util.Arrays.stream(Direction.Axis.values()).filter(a->CrossingWireBlock.kind(state,a)!=0).findFirst().orElseThrow();
        if((CrossingWireBlock.kind(state,axis)==3)!=bundled)return InteractionResult.FAIL;
        var player=context.getPlayer();if(player==null||!player.mayBuild()||!player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()))return InteractionResult.FAIL;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        var server=((ServerPlayer)player).serverLevel();if(!server.mayInteract(player,pos))return InteractionResult.FAIL;
        if(!(level.getBlockEntity(pos) instanceof CrossingWireEntity wire))return InteractionResult.FAIL;
        String id=wire.id(axis);int color=wire.color(axis);var replacement=Crossings.ordinary(state,axis);
        Runnable initialize=()->{if(level.getBlockEntity(pos) instanceof IdWireEntity identified)identified.configure(id,color);};
        if(server.captureBlockSnapshots){if(!server.setBlock(pos,replacement,3))return InteractionResult.FAIL;initialize.run();}
        else if(!ProtectedRepairPlacement.place(server,pos,replacement,(ServerPlayer)player,()->NetworkOperations.canModify((ServerPlayer)player),initialize))return InteractionResult.FAIL;
        return InteractionResult.CONSUME;
    }
    private static InteractionResult error(UseOnContext c,String message){if(!c.getLevel().isClientSide)c.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal(message),true);return InteractionResult.FAIL;}
}
