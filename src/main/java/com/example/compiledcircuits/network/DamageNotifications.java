package com.example.compiledcircuits.network;

import com.example.compiledcircuits.networking.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Minimal stage-03 prerequisite: coalesce existing snapshots without changing their protocol. */
public final class DamageNotifications {
    private static final Map<MinecraftServer, Pending> pending = new IdentityHashMap<>();
    private static final class Pending {
        boolean gui; long damaged, repaired;
        final LinkedHashSet<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> dimensions=new LinkedHashSet<>();
    }
    private DamageNotifications() {}
    static void changed(ServerLevel level, boolean repaired) {
        var p=pending.computeIfAbsent(level.getServer(),k->new Pending());p.gui=true;p.dimensions.add(level.dimension());
        if(repaired)p.repaired++;else p.damaged++;
    }
    static void removed(MinecraftServer server, Collection<CompiledNetwork> networks) {
        var p=pending.computeIfAbsent(server,k->new Pending());p.gui=true;
        for(var network:networks)p.dimensions.add(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(network.getDimension())));
    }
    public static void flush(MinecraftServer server) {
        var p=pending.get(server);if(p==null)return;
        if(p.gui) {
            long start=ServerWorkBudget.begin(server,ServerWorkBudget.Lane.SYNC);if(start==0)return;
            p.gui=false;long damaged=p.damaged,repaired=p.repaired;p.damaged=p.repaired=0;
            try {
                NetworkGuiSync.broadcastBrokenList(server);
                if(damaged!=0 || repaired!=0)server.getPlayerList().broadcastSystemMessage(Component.literal("[CompiledCircuits] Integrity updates: damaged "+damaged+", repaired "+repaired+"."),false);
            }finally{ServerWorkBudget.end(server,ServerWorkBudget.Lane.SYNC,start);}
        }
        if(!p.dimensions.isEmpty()) {
            long start=ServerWorkBudget.begin(server,ServerWorkBudget.Lane.SYNC);if(start==0)return;
            var iterator=p.dimensions.iterator();var dimension=iterator.next();iterator.remove();
            try{var level=server.getLevel(dimension);if(level!=null)BrokenElementSync.broadcastDimension(level);}
            finally{ServerWorkBudget.end(server,ServerWorkBudget.Lane.SYNC,start);}
        }
        if(!p.gui && p.dimensions.isEmpty())pending.remove(server);
    }
    public static void stop(MinecraftServer server) { pending.remove(server); }
}
