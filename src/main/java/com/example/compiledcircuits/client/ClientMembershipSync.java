package com.example.compiledcircuits.client;
import java.util.UUID;
import com.example.compiledcircuits.networking.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
/** Connection, player and level identity are all part of the reply lifetime. */
public final class ClientMembershipSync {
    private static Object connection,level,player;private static UUID token;
    private static long tick,requested=-1000,acked;private static String shown="";
    private static void refresh(){
        var mc=Minecraft.getInstance();var c=mc.getConnection()==null?null:mc.getConnection().getConnection();
        if(c==connection&&mc.level==level&&mc.player==player)return;
        connection=c;level=mc.level;player=mc.player;token=null;acked=0;shown="";requested=-1000;
        ClientCompiledElements.clear();ClientCompiledElements.onLevelChanged(level,mc.level==null?"":mc.level.dimension().location().toString());
        if(c!=null&&level!=null&&player!=null){token=UUID.randomUUID();ClientCompiledElements.context(token);}
    }
    private static void send(long ack){var mc=Minecraft.getInstance();ModNetworking.CHANNEL.sendToServer(new MembershipRequestC2SPacket(token,mc.level.dimension().location().toString(),ack));}
    public static void accept(CompiledElementPositionsS2CPacket packet,Connection sender){refresh();if(sender==connection&&token!=null)ClientCompiledElements.accept(packet,System.nanoTime());}
    public static void tick(){
        tick++;refresh();if(token==null)return;ClientCompiledElements.tick(System.nanoTime());var status=ClientCompiledElements.status();
        if(((status.equals("UNKNOWN")||status.equals("STALE"))&&tick-requested>=40)||(status.equals("UNKNOWN_STATUS")&&tick-requested>=200)){send(0);requested=tick;}
        long complete=ClientCompiledElements.completedSnapshot();if(complete>acked){acked=complete;send(complete);}
        if(!shown.equals(status)){
            shown=status;if(status.equals("OVER_CAPACITY")||status.equals("UNKNOWN_STATUS"))Minecraft.getInstance().player.displayClientMessage(Component.literal("Membership "+status+": "+ClientCompiledElements.reason()+"; use /circuit capacity and /circuit decompile <id>."),false);
        }
    }
    private ClientMembershipSync(){}
}
