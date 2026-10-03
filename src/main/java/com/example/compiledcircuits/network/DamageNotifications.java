package com.example.compiledcircuits.network;
import com.example.compiledcircuits.networking.*;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
/** Fixed-window physical transition summaries; the ledger owns wire data. */
public final class DamageNotifications {
    private static final Map<MinecraftServer,Pending> pending=new IdentityHashMap<>();
    private static final class Pending {
        long damaged,repaired;final int deadline;final Set<Integer> networks=new HashSet<>();boolean capped;
        Pending(int tick){deadline=tick+DamageProtocol.CHAT_TICKS;}
    }
    private DamageNotifications() {}
    static void changed(ServerLevel level,int network,boolean repaired){
        var p=pending.computeIfAbsent(level.getServer(),s->new Pending(s.getTickCount()));
        if(repaired)p.repaired++;else p.damaged++;
        if(p.networks.size()<262144)p.networks.add(network);else p.capped=true;
    }
    static void removed(MinecraftServer server,Collection<CompiledNetwork> networks){ }
    public static void flush(MinecraftServer server){
        var p=pending.get(server);
        if(p!=null && server.getTickCount()>=p.deadline){
            long started=ServerWorkBudget.begin(server,ServerWorkBudget.Lane.SYNC);
            if(started!=0){
                pending.remove(server);
                try{
                    server.getPlayerList().broadcastSystemMessage(Component.literal("[CompiledCircuits] Integrity transitions: damaged "+p.damaged
                            +", repaired "+p.repaired+", networks "+(p.capped?"at least ":"")+p.networks.size()+"."),false);
                    PerformanceDiagnostics.add("damage.chatSummaries",1);PerformanceDiagnostics.add("damage.chatDamagedTransitions",p.damaged);
                    PerformanceDiagnostics.add("damage.chatRepairedTransitions",p.repaired);
                }finally{ServerWorkBudget.end(server,ServerWorkBudget.Lane.SYNC,started);}
            }
        }
        DamageSync.flush(server);
    }
    public static void stop(MinecraftServer server){pending.remove(server);DamageSync.stop(server);}
}
