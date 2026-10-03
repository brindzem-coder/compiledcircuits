package com.example.compiledcircuits.client;

import com.example.compiledcircuits.networking.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import java.util.*;
import static com.example.compiledcircuits.networking.HighlightProtocol.*;

/** Request IDs never reset within this client process; world/connection identity also gates replies. */
public final class ClientHighlightSync {
    private static final HighlightReplica replica=new HighlightReplica();
    private static Object connection,level,player,requestScreen;
    private static UUID token;private static long sequence,tick,presented;
    private static HighlightReplica.Status shown=HighlightReplica.Status.EMPTY;
    public static void refresh(){
        var mc=Minecraft.getInstance();var c=mc.getConnection()==null?null:mc.getConnection().getConnection();
        if(c==connection&&mc.level==level&&mc.player==player)return;
        reset();connection=c;level=mc.level;player=mc.player;
        if(c!=null&&level!=null&&player!=null)token=UUID.randomUUID();
    }
    public static void reset(){
        connection=level=player=requestScreen=null;token=null;replica.reset();presented=0;shown=HighlightReplica.Status.EMPTY;ClientNetworkSelection.clear();
    }
    private static Request next(){return new Request(token,++sequence,Minecraft.getInstance().level.dimension().location().toString());}
    public static void request(Collection<Integer> ids){
        refresh();if(token==null)return;
        ClientNetworkSelection.clear();var request=next();replica.expect(request,tick);presented=0;shown=HighlightReplica.Status.WAITING;
        requestScreen=Minecraft.getInstance().screen;
        Minecraft.getInstance().player.displayClientMessage(Component.literal("Preparing network highlight..."),true);
        ModNetworking.CHANNEL.sendToServer(new NetworkBulkActionC2SPacket(NetworkBulkActionC2SPacket.BulkAction.HIGHLIGHT_NETWORKS,ids,0,request));
    }
    public static void cancel(){
        refresh();replica.reset();presented=0;requestScreen=null;shown=HighlightReplica.Status.EMPTY;ClientNetworkSelection.clear();
        if(token!=null)ModNetworking.CHANNEL.sendToServer(new NetworkBulkActionC2SPacket(NetworkBulkActionC2SPacket.BulkAction.HIGHLIGHT_NETWORKS,List.of(),0,next()));
    }
    public static void accept(NetworkHighlightS2CPacket packet,Connection sender){
        refresh();if(sender!=connection||token==null)return;
        replica.accept(packet,tick);if(replica.status()==HighlightReplica.Status.ERROR)ClientNetworkSelection.clear();
    }
    public static void tick(){
        tick++;refresh();replica.tick(tick,CLIENT_STEPS);
        var mc=Minecraft.getInstance();var status=replica.status();
        if(status==HighlightReplica.Status.READY&&replica.batch()!=presented){
            presented=replica.batch();ClientNetworkSelection.publish(level,replica.selection());
            if(requestScreen!=null&&mc.screen==requestScreen)mc.setScreen(null);requestScreen=null;
        }
        if(status==HighlightReplica.Status.ERROR)ClientNetworkSelection.clear();
        if(status!=shown){shown=status;if(mc.player!=null&&!replica.message().isEmpty())mc.player.displayClientMessage(Component.literal(replica.message()),false);}
    }
    public static String status(){return replica.status().name();}
    private ClientHighlightSync() {}
}
