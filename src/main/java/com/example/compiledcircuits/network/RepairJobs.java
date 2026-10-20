package com.example.compiledcircuits.network;

import com.example.compiledcircuits.config.ServerConfig;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Captures immutable damage roots, never copies a mutable broken list or extends targets later. */
public final class RepairJobs {
    private static final Map<MinecraftServer,Manager> managers=new IdentityHashMap<>();
    private record Target(CompiledNetwork network,Collection<BrokenCircuitElement> broken) {}
    private static final class Manager {
        final ArrayDeque<Job> jobs=new ArrayDeque<>();
        final Map<CompiledNetwork,Job> locks=new IdentityHashMap<>();
        final Map<ServerPlayer,Job> actors=new IdentityHashMap<>();
        int retained;
    }
    private static final class Job {
        final NetworkSavedData data;final CommandSourceStack source;final ServerPlayer player;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final List<Target> targets;final int total,createdTick;final long createdNanos=System.nanoTime();
        final int[] counts=new int[NetworkRepairManager.Outcome.values().length];
        int targetIndex,processed;Iterator<BrokenCircuitElement> cursor;
        String cancelled;
        Job(NetworkSavedData data,CommandSourceStack source,ServerPlayer player,List<Target> targets,int total) {
            this.data=data;this.source=source;this.player=player;this.targets=targets;this.total=total;
            dimension=player.serverLevel().dimension();createdTick=source.getServer().getTickCount();
        }
        boolean step() {
            var server=source.getServer();
            if(cancelled==null && (!NetworkOperations.canModify(player) || player.serverLevel().dimension()!=dimension
                    || NetworkSavedData.get(server)!=data))cancelled="actor/context changed";
            if(cancelled==null && (server.getTickCount()-createdTick>ServerConfig.REPAIR_LIFETIME_TICKS.get()
                    || System.nanoTime()-createdNanos>ServerConfig.REPAIR_LIFETIME_SECONDS.get()*1_000_000_000L))cancelled="timeout";
            if(cancelled!=null || targetIndex>=targets.size())return true;
            var target=targets.get(targetIndex);
            if(data.getNetwork(target.network.getId())!=target.network){cancelled="network removed or replaced";return true;}
            if(cursor==null)cursor=target.broken.iterator();
            if(!cursor.hasNext()){cursor=null;targetIndex++;return targetIndex>=targets.size();}
            var broken=cursor.next();
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(target.network.getDimension())));
            var result=level==null?NetworkRepairManager.Outcome.UNLOADED:NetworkRepairManager.attempt(level,target.network,broken,player);
            counts[result.ordinal()]++;processed++;
            PerformanceDiagnostics.add("repair.result."+result,1);PerformanceDiagnostics.add("repair.elements",1);
            return false;
        }
        String summary() {
            return "Repair "+(cancelled==null?"completed":"cancelled ("+cancelled+")")+": placed "+count(NetworkRepairManager.Outcome.PLACED)
                    +", already correct "+count(NetworkRepairManager.Outcome.CORRECT)+", occupied "+count(NetworkRepairManager.Outcome.OCCUPIED)
                    +", unloaded "+count(NetworkRepairManager.Outcome.UNLOADED)+", protected "+count(NetworkRepairManager.Outcome.PROTECTED)
                    +", unsupported "+count(NetworkRepairManager.Outcome.UNSUPPORTED)+", invalid "+count(NetworkRepairManager.Outcome.INVALID)
                    +", changed "+count(NetworkRepairManager.Outcome.CHANGED)+", failed "+count(NetworkRepairManager.Outcome.FAILED)
                    +", remaining "+(total-processed)+". Placements still require integrity confirmation.";
        }
        int count(NetworkRepairManager.Outcome result){return counts[result.ordinal()];}
    }
    private RepairJobs() {}
    public static NetworkOperations.Result submit(CommandSourceStack source,ServerPlayer player,NetworkSavedData data,List<CompiledNetwork> networks) {
        var m=managers.computeIfAbsent(source.getServer(),k->new Manager());
        if(m.actors.containsKey(player)||m.jobs.size()>=ServerConfig.MAX_REPAIR_JOBS.get())return result(NetworkOperations.Code.BUSY,"Repair queue full or player already repairing.");
        int size=0;
        for(var n:networks) {
            if(m.locks.containsKey(n))return result(NetworkOperations.Code.BUSY,"A selected network already has a repair job.");
            size+=n.getBrokenElements().size();
        }
        if(size==0)return result(NetworkOperations.Code.OK,"No broken elements to repair.");
        if(size>ServerConfig.MAX_REPAIR_TARGETS.get()-m.retained)return result(NetworkOperations.Code.BUSY,"Repair target memory budget is full.");
        var targets=new ArrayList<Target>();
        for(var n:networks)if(!n.getBrokenElements().isEmpty())targets.add(new Target(n,n.getBrokenElements()));
        var job=new Job(data,source,player,List.copyOf(targets),size);
        for(var t:targets)m.locks.put(t.network,job);
        m.jobs.addLast(job);m.actors.put(player,job);m.retained+=size;
        PerformanceDiagnostics.max("repair.jobsPeak",m.jobs.size());PerformanceDiagnostics.max("repair.retainedTargetsPeak",m.retained);
        return result(NetworkOperations.Code.QUEUED,"Repair queued: "+size+" captured targets. Completed placements remain if cancelled.");
    }
    private static NetworkOperations.Result result(NetworkOperations.Code code,String message){return new NetworkOperations.Result(code,message,List.of());}
    static void recordQueue(MinecraftServer server) {
        var m=managers.get(server);
        PerformanceDiagnostics.gauge("repair.jobsCurrent",m==null?0:m.jobs.size());
        PerformanceDiagnostics.gauge("repair.retainedTargetsCurrent",m==null?0:m.retained);
        if(m!=null)for(var job:m.jobs)PerformanceDiagnostics.max("repair.oldestAgeTicks",server.getTickCount()-job.createdTick);
    }
    static boolean hasWork(MinecraftServer server){var m=managers.get(server);return m!=null&&!m.jobs.isEmpty();}
    static boolean locked(MinecraftServer server,CompiledNetwork network){var m=managers.get(server);return m!=null&&m.locks.containsKey(network);}
    static void step(MinecraftServer server) {
        var m=managers.get(server);if(m==null||m.jobs.isEmpty())return;
        var job=m.jobs.removeFirst();boolean done;
        try{done=job.step();}catch(RuntimeException failure){job.cancelled="placement failed: "+failure.getClass().getSimpleName();done=true;}
        PerformanceDiagnostics.max("repair.oldestAgeTicks",server.getTickCount()-job.createdTick);
        if(!done){m.jobs.addLast(job);return;}
        // At most 1024 admitted target networks; no per-element completion traversal.
        for(var t:job.targets)m.locks.remove(t.network,job);
        m.actors.remove(job.player);m.retained-=job.total;
        PerformanceDiagnostics.add("repair.remainingAtCompletion",job.total-job.processed);
        PerformanceDiagnostics.add(job.cancelled==null?"repair.completedJobs":"repair.cancelledJobs",1);
        if(!job.player.isRemoved())job.source.sendSuccess(()->Component.literal(job.summary()),false);
    }
    public static void cancel(ServerPlayer player) {
        var m=managers.get(player.getServer());if(m!=null){var job=m.actors.get(player);if(job!=null)job.cancelled="player session ended";}
    }
    static void removed(MinecraftServer server,CompiledNetwork network) {
        var m=managers.get(server);if(m!=null){var job=m.locks.get(network);if(job!=null)job.cancelled="network removed or replaced";}
    }
    public static void stop(MinecraftServer server){managers.remove(server);}
    public static boolean isBusy(ServerPlayer player){var m=managers.get(player.getServer());return m!=null&&m.actors.containsKey(player);}
}
