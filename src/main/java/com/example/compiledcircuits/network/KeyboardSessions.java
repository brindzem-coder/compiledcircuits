package com.example.compiledcircuits.network;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.networking.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Server-authoritative sessions; held state is transient and never saved into a circuit. */
@Mod.EventBusSubscriber(modid=CompiledCircuits.MOD_ID)
public final class KeyboardSessions {
    private static final Map<UUID,Session> sessions=new HashMap<>();
    private static final class Session {
        final ServerPlayer player; final ServerLevel level; final BlockPos keyboard; final CompiledNetwork network;
        final UUID token=UUID.randomUUID(); final Map<BundleFace,KeyboardBinding> bindings=new HashMap<>();
        Map<BundleFace,Boolean> values=Map.of(); long lastPacket; int sequence=-1, packets; long packetTick=-1;
        final Vec3 origin;
        Session(ServerPlayer player,BlockPos keyboard,CompiledNetwork network){this.player=player;this.level=player.serverLevel();this.keyboard=keyboard.immutable();this.network=network;origin=player.position();lastPacket=level.getGameTime();}
    }
    public static Map<BundleFace,Boolean> values(CompiledNetwork network) {
        var result=new HashMap<BundleFace,Boolean>();
        for(var session:sessions.values())if(session.network==network)result.putAll(session.values);
        return result;
    }
    public static void open(ServerPlayer player,BlockPos pos) {
        var level=player.serverLevel();
        if(player.distanceToSqr(Vec3.atCenterOf(pos))>64||!level.hasChunkAt(pos)||player.isPassenger())return;
        var network=NetworkSavedData.get(level.getServer()).findNetworkByInput(level,pos);
        if(network==null||!network.directReady()) { message(player,"compile");return; }
        for(var s:sessions.values())if(s.level==level&&s.keyboard.equals(pos)&&s.player!=player){message(player,"busy");return;}
        close(player.getUUID(),true);
        Map<BundleFace,BlockPos> routes;
        try { routes=KeyboardTopology.resolve(network.tableStates(),network.bundleFaces(),Map.of()); }
        catch(IllegalArgumentException invalid){message(player,"compile");return;}
        if(routes.values().stream().noneMatch(pos::equals)){message(player,"no_ports");return;}
        if(level.getBlockEntity(pos) instanceof KeyboardEntity entity && entity.camera()!=null) {
            var destination=entity.camera();var feet=BlockPos.containing(destination);
            if(!NetworkTeleport.safe(level,player,feet,true)||!level.noCollision(player,player.getBoundingBox().move(destination.subtract(player.position())))){message(player,"unsafe_camera");return;}
            player.teleportTo(level,destination.x,destination.y,destination.z,entity.yaw(),entity.pitch());
            player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
        }
        var session=new Session(player,pos,network);var faces=network.bundleFaces();
        routes.forEach((face,keyboard)->{if(keyboard.equals(pos))session.bindings.put(face,KeyboardBinding.parse(faces.get(face).id()));});
        sessions.put(player.getUUID(),session);
        send(player,new KeyboardSessionS2CPacket(session.token,true));
    }
    private static void message(ServerPlayer player,String key){player.sendSystemMessage(Component.translatable("keyboard.compiledcircuits."+key));}
    private static void send(ServerPlayer player,KeyboardSessionS2CPacket packet){ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);}
    public static void input(ServerPlayer player,KeyboardInputC2SPacket packet) {
        var session=sessions.get(player.getUUID());
        if(session==null||!session.token.equals(packet.token())||packet.sequence()<=session.sequence)return;
        if(packet.close()){close(player.getUUID(),true);return;}
        if(!valid(session)){close(player.getUUID(),true);return;}
        long tick=session.level.getGameTime();if(session.packetTick!=tick){session.packetTick=tick;session.packets=0;}
        if(++session.packets>128)return;
        if(packet.pressed().size()>32||packet.pressed().stream().anyMatch(c->!KeyboardBinding.valid(c)||c==256))return;
        session.sequence=packet.sequence();session.lastPacket=tick;
        var values=new HashMap<BundleFace,Boolean>();
        session.bindings.forEach((face,binding)->values.put(face,binding.matches(packet.pressed())));
        if(!session.values.equals(values)){session.values=Map.copyOf(values);NetworkRuntime.inputChanged(session.level,session.keyboard);}
    }
    private static boolean valid(Session s) {
        return s.player.isAlive()&&!s.player.isSpectator()&&s.player.serverLevel()==s.level&&!s.player.isPassenger()
                &&s.player.position().distanceToSqr(s.origin)<=4&&s.level.hasChunkAt(s.keyboard)
                &&s.level.getBlockState(s.keyboard).getBlock() instanceof KeyboardBlock
                &&!s.network.isRetired()&&s.network.directReady()
                &&NetworkSavedData.get(s.level.getServer()).findNetworkByInput(s.level,s.keyboard)==s.network;
    }
    private static void close(UUID player,boolean notify) {
        var s=sessions.remove(player);if(s==null)return;
        NetworkRuntime.inputChanged(s.level,s.keyboard);
        if(notify)send(s.player,new KeyboardSessionS2CPacket(s.token,false));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(var s:List.copyOf(sessions.values()))if(!valid(s)||s.level.getGameTime()-s.lastPacket>60)close(s.player.getUUID(),true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){close(event.getEntity().getUUID(),false);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){sessions.clear();}
}
