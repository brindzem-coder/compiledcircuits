package com.example.compiledcircuits.gametest;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.networking.*;
import com.example.compiledcircuits.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import net.minecraftforge.network.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class PermissionsGameTests {
    private static FakePlayer player(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"permission-test"));
        player.setGameMode(GameType.SURVIVAL); player.getAbilities().mayBuild = true;
        return player;
    }
    private static CompiledNetwork network(int id, BlockPos pos) {
        return new CompiledNetwork(id,"original",0,"minecraft:overworld",Set.of(pos),Set.of(),Set.of());
    }
    private static NetworkOperations.Result call(ServerPlayer p,NetworkOperations.Action action,List<Integer> ids,int target,String value) {
        NetworkOperations.forget(p);
        return NetworkOperations.execute(p.createCommandSourceStack(),action,ids,target,value);
    }
    private static NetworkEvent.Context context(ServerPlayer player) throws Exception {
        return context(player,NetworkDirection.PLAY_TO_SERVER);
    }
    private static NetworkEvent.Context context(ServerPlayer player, NetworkDirection direction) throws Exception {
        var connection = new Connection(PacketFlow.SERVERBOUND); connection.setListener(player.connection);
        var constructor = NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(connection,direction,0);
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void permissionMatrix(GameTestHelper h) throws Exception {
        var level=h.getLevel(); var server=level.getServer(); var storage=server.overworld().getDataStorage();
        var original=NetworkSavedData.get(server); var data=new NetworkSavedData();
        var pos=h.absolutePos(new BlockPos(1,2,1)); data.addNetwork(network(1,pos));
        int folder=data.createFolder("folder",0);
        var p=player(h); var source=p.createCommandSourceStack();
        try {
            storage.set("compiledcircuits_networks",data); NetworkSelectionData.set(p,pos);
            for (int mode=0;mode<3;mode++) {
                p.setGameMode(mode==0?GameType.SURVIVAL:GameType.SPECTATOR);
                if(mode==0) p.getAbilities().mayBuild=false;
                var actor=mode==2?p.createCommandSourceStack().withPermission(4):p.createCommandSourceStack();
                var before=data.save(new CompoundTag()); data.setDirty(false);
                for (var action:NetworkOperations.Action.values()) {
                    NetworkOperations.forget(p);
                    var r=NetworkOperations.execute(actor,action,List.of(1),0,"name");
                    if (Set.of(NetworkOperations.Action.LIST,NetworkOperations.Action.GUI,NetworkOperations.Action.SELECTED,
                            NetworkOperations.Action.DEBUG,NetworkOperations.Action.HIGHLIGHT).contains(action)) {
                        h.assertTrue(r.success(),"read access for "+action);
                    } else if(action!=NetworkOperations.Action.LIST_CONFLICTS) h.assertTrue(r.code()==NetworkOperations.Code.FORBIDDEN,"mutation forbidden for "+action);
                }
                for(var action:NetworkActionC2SPacket.Action.values()) {
                    if(action==NetworkActionC2SPacket.Action.HIGHLIGHT) continue;
                    NetworkOperations.forget(p);
                    var packet=new NetworkActionC2SPacket(action,1,0,"name");
                    h.assertTrue(NetworkActionC2SPacket.execute(packet,p).code()==NetworkOperations.Code.FORBIDDEN,"single packet permission");
                }
                for(var action:NetworkBulkActionC2SPacket.BulkAction.values()) {
                    if(action==NetworkBulkActionC2SPacket.BulkAction.HIGHLIGHT_NETWORKS) continue;
                    NetworkOperations.forget(p);
                    h.assertTrue(NetworkBulkActionC2SPacket.execute(new NetworkBulkActionC2SPacket(action,List.of(1),0),p).code()==NetworkOperations.Code.FORBIDDEN,"bulk packet permission");
                }
                NetworkOperations.forget(p);
                var ctx=context(p);
                NetworkActionC2SPacket.handle(new NetworkActionC2SPacket(NetworkActionC2SPacket.Action.DECOMPILE,1,""),()->ctx);
                h.assertTrue(ctx.getPacketHandled(),"actual single packet handler executed");
                NetworkOperations.forget(p);
                var bulkCtx=context(p);
                NetworkBulkActionC2SPacket.handle(new NetworkBulkActionC2SPacket(NetworkBulkActionC2SPacket.BulkAction.DECOMPILE_NETWORKS,List.of(1),0),()->bulkCtx);
                NetworkOperations.forget(p);
                var compileCtx=context(p); CompileNamedC2SPacket.handle(new CompileNamedC2SPacket("denied"),()->compileCtx);
                for(String command:List.of("circuit decompile","circuit compile","circuit rename 1 denied","circuit folder 1 root")) {
                    NetworkOperations.forget(p);
                    h.assertTrue(server.getCommands().getDispatcher().execute(command,actor)==0,"command denied: "+command);
                }
                h.assertTrue(before.equals(data.save(new CompoundTag())) && !data.isDirty(),"denied operations leave data and dirty unchanged");
            }
            for(var mode:List.of(GameType.SURVIVAL,GameType.CREATIVE)) {
                p.setGameMode(mode); p.getAbilities().mayBuild=true;
                h.assertTrue(call(p,NetworkOperations.Action.RENAME_NETWORK,List.of(1),0," allowed ").success(),"normal player rename");
                h.assertTrue(call(p,NetworkOperations.Action.MOVE_NETWORKS,List.of(1),folder,"").success(),"normal move");
                h.assertTrue(call(p,NetworkOperations.Action.MOVE_NETWORKS,List.of(1),0,"").success(),"root destination valid");
                h.assertTrue(call(p,NetworkOperations.Action.RENAME_FOLDER,List.of(folder),0,"renamed").success(),"folder rename");
                h.assertTrue(call(p,NetworkOperations.Action.MOVE_FOLDERS,List.of(folder),0,"").success(),"folder move");
            }
            var wrong=context(p,NetworkDirection.PLAY_TO_CLIENT);
            NetworkActionC2SPacket.handle(new NetworkActionC2SPacket(NetworkActionC2SPacket.Action.DECOMPILE,1,""),()->wrong);
            NetworkBulkActionC2SPacket.handle(new NetworkBulkActionC2SPacket(NetworkBulkActionC2SPacket.BulkAction.DECOMPILE_NETWORKS,List.of(1),0),()->wrong);
            CompileNamedC2SPacket.handle(new CompileNamedC2SPacket("wrong-direction"),()->wrong);
            h.assertTrue(wrong.getPacketHandled() && data.getNetwork(1)!=null,"wrong-direction packets rejected before work");
            var before=data.save(new CompoundTag()); data.setDirty(false);
            h.assertTrue(!call(p,NetworkOperations.Action.DECOMPILE,List.of(1,999),0,"").success(),"mixed invalid batch rejected");
            h.assertTrue(!call(p,NetworkOperations.Action.REPAIR,List.of(1,999),0,"").success(),"mixed invalid repair rejected before work");
            h.assertTrue(!call(p,NetworkOperations.Action.MOVE_FOLDERS,List.of(folder),folder,"").success(),"folder cycle rejected");
            h.assertTrue(before.equals(data.save(new CompoundTag())) && !data.isDirty(),"atomic failures preserve state");
            h.assertTrue(!call(p,NetworkOperations.Action.CREATE_FOLDER,List.of(0),0,"bad/name").success(),"folder separator rejected");
            h.assertTrue(!call(p,NetworkOperations.Action.RENAME_NETWORK,List.of(1),0,"x".repeat(65)).success(),"name limit");
            h.assertTrue(call(p,NetworkOperations.Action.CREATE_FOLDER,List.of(0),0,"child").success(),"root parent create");
            h.assertTrue(call(p,NetworkOperations.Action.DELETE_FOLDERS,List.of(folder),0,"").success(),"folder delete");
            NetworkOperations.forget(server);
            h.assertTrue(server.getCommands().getDispatcher().execute("circuit rename 1 console",server.createCommandSourceStack())==1,"console metadata retained");
            h.assertTrue(server.getCommands().getDispatcher().execute("circuit compile",server.createCommandSourceStack())==0,"console player-context refusal");
            h.assertTrue(call(p,NetworkOperations.Action.DECOMPILE,List.of(1,1),0,"").success() && data.getNetworks().isEmpty(),"duplicate IDs decompile once");
        } finally {
            storage.set("compiledcircuits_networks",original); NetworkOperations.forget(p); NetworkOperations.forget(server); NetworkSelectionData.clear(p);
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void protectedRepair(GameTestHelper h) {
        var level=h.getLevel(); var server=level.getServer(); var storage=server.overworld().getDataStorage();
        var original=NetworkSavedData.get(server);var data=new NetworkSavedData();var p=player(h);
        var pos=h.absolutePos(new BlockPos(1,2,1));var old=level.getBlockState(pos);
        var state=ModBlocks.INPUT_ENDPOINT.get().defaultBlockState();
        boolean[] saw={false};
        Consumer<BlockEvent.EntityPlaceEvent> deny=event->{
            if(event.getEntity()==p && event.getPos().equals(pos)) {
                saw[0]=true;
                h.assertTrue(event.getBlockSnapshot().getReplacedBlock().isAir() && event.getPlacedBlock().is(ModBlocks.INPUT_ENDPOINT.get()),"Forge event observes old snapshot and tentative new state");
                event.setCanceled(true);
            }
        };
        try {
            storage.set("compiledcircuits_networks",data);
            level.setBlock(pos,state,3);
            var n=new CompiledNetwork(1,"repair",0,"minecraft:overworld",CompiledElementFactory.create(level,Set.of(),Set.of(pos),Set.of()));
            data.addNetwork(n);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);NetworkIntegrityManager.checkPosition(level,pos);
            var inventory=p.getInventory().save(new ListTag()); var before=data.save(new CompoundTag());data.setDirty(false);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,BlockEvent.EntityPlaceEvent.class,deny);
            var denied=NetworkRepairManager.repairNetwork(level,n,p);
            h.assertTrue(saw[0] && denied.repaired()==0 && denied.failed()==1,"canceled placement not counted as repaired");
            h.assertTrue(level.getBlockState(pos).isAir() && inventory.equals(p.getInventory().save(new ListTag())),"canceled block and inventory unchanged");
            h.assertTrue(before.equals(data.save(new CompoundTag())) && !data.isDirty(),"canceled repair leaves saved state unchanged");
            h.assertTrue(!level.captureBlockSnapshots && !level.restoringBlockSnapshots && level.capturedBlockSnapshots.isEmpty(),"Forge capture state restored");
            MinecraftForge.EVENT_BUS.unregister(deny);
            Consumer<BlockEvent.EntityPlaceEvent> revoke=event->{if(event.getEntity()==p)p.getAbilities().mayBuild=false;};
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,BlockEvent.EntityPlaceEvent.class,revoke);
            try {
                var stopped=NetworkRepairManager.repairNetwork(level,n,p);
                h.assertTrue(stopped.repaired()==0 && level.getBlockState(pos).isAir(),"permission loss during placement rolls it back");
            } finally {MinecraftForge.EVENT_BUS.unregister(revoke);p.getAbilities().mayBuild=true;}

            var allowed=NetworkRepairManager.repairNetwork(level,n,p);
            h.assertTrue(allowed.repaired()==1 && level.getBlockState(pos).equals(state),"normal player repair still works");
            NetworkIntegrityManager.processPending(server);h.assertTrue(!n.isDamaged(),"successful repair confirmed");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deny);NetworkIntegrityManager.clearPending();NetworkOperations.forget(p);
            storage.set("compiledcircuits_networks",original);level.setBlock(pos,old,3);
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void requestLimits(GameTestHelper h) throws Exception {
        var p=player(h);var source=p.createCommandSourceStack();
        for(int i=0;i<OperationLimits.REQUESTS_PER_WINDOW;i++)
            h.assertTrue(NetworkOperations.execute(source,NetworkOperations.Action.LIST,List.of(),0,"").success(),"request allowed at boundary");
        h.assertTrue(NetworkOperations.execute(source,NetworkOperations.Action.LIST,List.of(),0,"").code()==NetworkOperations.Code.RATE_LIMITED,"request over boundary rejected");
        h.assertTrue(h.getLevel().getServer().getCommands().getDispatcher().execute("circuit list",source)==0,"command shares packet/service quota");
        for(int size:new int[]{-1,0,OperationLimits.IDS+1,Integer.MAX_VALUE}) {
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeEnum(NetworkBulkActionC2SPacket.BulkAction.DECOMPILE_NETWORKS);buf.writeVarInt(size);
                try {NetworkBulkActionC2SPacket.decode(buf);throw new AssertionError("invalid raw size accepted");}catch(IllegalArgumentException expected){}
            } finally {buf.release();}
        }
        try {new NetworkBulkActionC2SPacket(NetworkBulkActionC2SPacket.BulkAction.DECOMPILE_NETWORKS,Collections.nCopies(OperationLimits.IDS+1,1),0);throw new AssertionError("duplicate raw budget bypass");}catch(IllegalArgumentException expected){}
        try {new NetworkActionC2SPacket(NetworkActionC2SPacket.Action.RENAME_NETWORK,1,"x".repeat(65));throw new AssertionError("oversize name");}catch(IllegalArgumentException expected){}
        h.runAfterDelay(OperationLimits.WINDOW_TICKS+1,()->{
            try {
                h.assertTrue(NetworkOperations.execute(source,NetworkOperations.Action.LIST,List.of(),0,"").success(),"window resets");
                NetworkOperations.forget(p);
                h.assertTrue(NetworkOperations.execute(source,NetworkOperations.Action.LIST,List.of(),0,"").success(),"session cleanup removes budget");
                h.succeed();
            } finally {NetworkOperations.forget(p);}
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void workAndPacketLimits(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer();var storage=server.overworld().getDataStorage();
        var original=NetworkSavedData.get(server);var data=new NetworkSavedData();var p=player(h);
        var commands=server.getCommands().getDispatcher();var console=server.createCommandSourceStack();
        try {
            storage.set("compiledcircuits_networks",data);
            NetworkSelectionData.set(p,h.absolutePos(new BlockPos(1,2,1)));
            commands.execute("ccperf reset",console);commands.execute("ccperf start",console);
            for(int i=0;i<4;i++) h.assertTrue(NetworkOperations.execute(p.createCommandSourceStack(),NetworkOperations.Action.COMPILE,List.of(),0,"empty").code()==NetworkOperations.Code.INVALID_START,"scan budget at boundary");
            h.assertTrue(NetworkOperations.execute(p.createCommandSourceStack(),NetworkOperations.Action.COMPILE,List.of(),0,"empty").code()==NetworkOperations.Code.RATE_LIMITED,"work quota rejects fifth full scan");
            commands.execute("ccperf stop",console);
            var last=com.example.compiledcircuits.diagnostics.PerformanceDiagnostics.class.getDeclaredField("last");last.setAccessible(true);
            var capture=last.get(null);var field=capture.getClass().getDeclaredField("counters");field.setAccessible(true);
            var metrics=(Map<?,?>)field.get(capture);
            h.assertTrue(Long.valueOf(4).equals(metrics.get("scan.calls")),"rate rejection runs no scan");
            var positions=new HashSet<BlockPos>();for(int i=0;i<OperationLimits.ELEMENTS;i++)positions.add(new BlockPos(i,80,900000));
            data.addNetwork(new CompiledNetwork(1,"boundary",0,"minecraft:overworld",positions,Set.of(),Set.of()));
            h.assertTrue(call(p,NetworkOperations.Action.HIGHLIGHT,List.of(1),0,"").success(),"element limit inclusive");
            positions.add(new BlockPos(OperationLimits.ELEMENTS,80,900000));
            data.replaceNetwork(new CompiledNetwork(1,"too-large",0,"minecraft:overworld",positions,Set.of(),Set.of()));
            var before=data.save(new CompoundTag());data.setDirty(false);
            h.assertTrue(call(p,NetworkOperations.Action.DECOMPILE,List.of(1),0,"").code()==NetworkOperations.Code.INVALID_ARGUMENT,"oversize work rejected");
            h.assertTrue(before.equals(data.save(new CompoundTag())) && !data.isDirty(),"oversize operation preserves state");
            h.assertTrue(call(p,NetworkOperations.Action.RENAME_NETWORK,List.of(1),0,"name").code()==NetworkOperations.Code.INVALID_ARGUMENT,"oversize metadata request rejected");
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeVarInt(999);
                try {NetworkActionC2SPacket.decode(buf);throw new AssertionError("invalid enum accepted");}catch(RuntimeException expected){}
            } finally {buf.release();}
            var full=new NetworkBulkActionC2SPacket(NetworkBulkActionC2SPacket.BulkAction.MOVE_NETWORKS,Collections.nCopies(OperationLimits.IDS,1),0);
            var encoded=new FriendlyByteBuf(Unpooled.buffer());
            try {NetworkBulkActionC2SPacket.encode(full,encoded);NetworkBulkActionC2SPacket.decode(encoded);}
            finally {encoded.release();}
        } finally {
            commands.execute("ccperf reset",console);NetworkOperations.forget(p);NetworkSelectionData.clear(p);
            storage.set("compiledcircuits_networks",original);
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void executionTimePermission(GameTestHelper h) throws Exception {
        var p=player(h);var data=NetworkSavedData.get(h.getLevel().getServer());int id=data.getNextNetworkId();
        var n=network(id,h.absolutePos(new BlockPos(1,2,1)));data.addNetwork(n);
        var ctx=context(p);var packet=new NetworkActionC2SPacket(NetworkActionC2SPacket.Action.RENAME_NETWORK,id,"should-not-apply");
        var thread=new Thread(()->NetworkActionC2SPacket.handle(packet,()->ctx));thread.start();thread.join(2000);
        h.assertTrue(!thread.isAlive(),"packet enqueued without blocking server");
        p.setGameMode(GameType.SPECTATOR);
        h.runAfterDelay(2,()->{
            try {h.assertTrue(n.getName().equals("original"),"permission rechecked after enqueue");h.succeed();}
            finally {data.removeNetwork(id);NetworkOperations.forget(p);}
        });
    }
}
