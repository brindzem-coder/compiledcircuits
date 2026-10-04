package com.example.compiledcircuits.client;

import com.example.compiledcircuits.networking.*;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.client.Minecraft;
import java.util.*;
import static com.example.compiledcircuits.networking.DamageProtocol.*;

/** Connection AND level/player identity gate replies, including returning to the same dimension. */
public final class ClientDamageSync {
    private static final DamageReplica replica=new DamageReplica();
    private static Object connection,level,player;private static UUID context;
    private static String dimension="";private static long tick,lastRequest=-1000,presented;
    private static DamageView projection;private static Map<Integer,String> paths=Map.of();
    private static void refresh(){
        var mc=Minecraft.getInstance();var c=mc.getConnection()==null?null:mc.getConnection().getConnection();
        if(c==connection&&mc.level==level&&mc.player==player)return;
        connection=c;level=mc.level;player=mc.player;dimension=mc.level==null?"":mc.level.dimension().location().toString();
        context=c==null||level==null||player==null?null:UUID.randomUUID();replica.reset(context);projection=null;presented=0;lastRequest=-1000;
        paths=Map.of();ClientBrokenElements.clear();ClientBrokenElements.confirmed(false);ClientBrokenElementList.clear();
        ClientPacketHandlers.managerDamageUpdated();
    }
    public static void accept(DamagePartS2CPacket packet,net.minecraft.network.Connection sender){
        refresh();if(sender!=connection||context==null)return;
        replica.accept(packet,tick);if(replica.status()!=DamageReplica.Status.READY)unconfirmed();
    }
    public static void tick(){
        tick++;refresh();if(context==null)return;
        replica.tick(tick,512);
        if(replica.status()!=DamageReplica.Status.READY)unconfirmed();
        if(replica.status()==DamageReplica.Status.READY&&replica.completedBatch()!=presented){
            if(projection==null)projection=new DamageView(replica,dimension);
            long deadline=System.nanoTime()+2_000_000;int work=1024;
            while(work-->0&&System.nanoTime()<deadline){
                if(!projection.step())continue;
                var completed=projection;projection=null;presented=completed.batch;paths=completed.paths();
                ClientBrokenElementList.publish(completed.entries());ClientBrokenElements.publish(dimension,completed.positions());
                ClientBrokenElements.retainFocused(p->completed.contains(p.dimension(),p.pos()));
                ClientPacketHandlers.managerDamageUpdated();
                ModNetworking.CHANNEL.sendToServer(new DamageAckC2SPacket(context,completed.batch,completed.revision));break;
            }
        }
        if(replica.status()!=DamageReplica.Status.READY&&!replica.assembling()&&tick-lastRequest>=RESYNC_TICKS){
            lastRequest=tick;ModNetworking.CHANNEL.sendToServer(new DamageResyncC2SPacket(context,dimension));
        }
        PerformanceDiagnostics.gauge("damage.clientStagingBytes",replica.stagingBytes());
    }
    public static String status(){return projection!=null?"SYNCING":replica.status().name();}
    private static void unconfirmed(){
        projection=null;ClientBrokenElements.confirmed(false);
        ClientPacketHandlers.managerDamageUnconfirmed();
    }
    public static boolean ready(){return projection==null&&replica.status()==DamageReplica.Status.READY;}
    public static String folderPath(int network){return paths.getOrDefault(network,"");}
    public static void clear(){connection=level=player=null;context=null;replica.reset(null);projection=null;paths=Map.of();ClientBrokenElementList.clear();ClientBrokenElements.clear();}
    private ClientDamageSync() {}
}
