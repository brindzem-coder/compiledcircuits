package com.example.compiledcircuits.network;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.KeyboardEntity;
import com.example.compiledcircuits.networking.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

@Mod.EventBusSubscriber(modid=CompiledCircuits.MOD_ID)
public final class KeyboardCameraSessions {
    private record Session(ServerPlayer player, KeyboardEntity keyboard, UUID token, long expires) {}
    private static final Map<UUID,Session> sessions=new HashMap<>();
    public static void open(ServerPlayer player,BlockPos pos){
        if(!player.mayBuild()||!NetworkOperations.canModify(player)||player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>64
                ||!player.serverLevel().mayInteract(player,pos)||!(player.serverLevel().getBlockEntity(pos) instanceof KeyboardEntity keyboard))return;
        var session=new Session(player,keyboard,UUID.randomUUID(),player.serverLevel().getGameTime()+6000);
        sessions.put(player.getUUID(),session);send(session,true);
    }
    private static boolean valid(Session s){
        var p=s.player();var pos=s.keyboard().getBlockPos();
        return p.isAlive()&&!p.isSpectator()&&p.mayBuild()&&NetworkOperations.canModify(p)
                &&s.keyboard().getLevel()==p.serverLevel()&&!s.keyboard().isRemoved()
                &&p.serverLevel().hasChunkAt(pos)&&p.serverLevel().getBlockEntity(pos)==s.keyboard()
                &&p.serverLevel().mayInteract(p,pos)&&p.serverLevel().getGameTime()<s.expires();
    }
    public static void finish(ServerPlayer player,UUID token,int action){
        var s=sessions.get(player.getUUID());if(s==null||!s.token().equals(token)||action<0||action>2)return;
        if(valid(s)&&action!=0){
            if(action==1&&player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atLowerCornerOf(s.keyboard().getBlockPos()))>256){
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("keyboard.compiledcircuits.camera_range"));return;
            }
            s.keyboard().configureCamera(player,action==2);
        }
        sessions.remove(player.getUUID());send(s,false);
    }
    private static void send(Session s,boolean open){ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(s::player),new KeyboardCameraS2CPacket(s.token(),open));}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        for(var s:List.copyOf(sessions.values()))if(!valid(s)){sessions.remove(s.player().getUUID());send(s,false);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){sessions.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){sessions.clear();}
}
