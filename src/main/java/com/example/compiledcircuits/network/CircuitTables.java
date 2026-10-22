package com.example.compiledcircuits.network;

import com.example.compiledcircuits.CompiledCircuits;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Build off the live graph, publish atomically, never rebuild saved rows during evaluation. */
@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID)
public final class CircuitTables {
    private record Job(CompiledNetwork network, UUID player, NetworkTruthTable.Builder builder, long started) {}
    private static final Map<MinecraftServer, Job> jobs = new WeakHashMap<>();
    public static int command(CommandSourceStack source, String action) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !NetworkOperations.canModify(player)) {
            source.sendFailure(Component.literal("You cannot modify circuits.")); return 0;
        }
        var data = NetworkSavedData.get(source.getServer()); var pos = NetworkSelectionData.get(player);
        var n = pos == null ? null : data.findNetworkContaining(player.serverLevel(), pos);
        if (n == null) { source.sendFailure(Component.literal("Select a compiled network first.")); return 0; }
        if (action.equals("status")) {
            var job = jobs.get(source.getServer());
            String progress = job != null && job.network == n ? " | building rows " + job.builder.next + "/" + job.builder.total : "";
            source.sendSuccess(() -> Component.literal(n.tableStatus() + progress), false); return 1;
        }
        if (action.equals("remove")) {
            var job = jobs.get(source.getServer()); if (job != null && job.network == n) jobs.remove(source.getServer());
            if (n.networkTable() != null) { n.setNetworkTable(null); data.setDirty(); data.runtime().optimizationChanged(n); }
            source.sendSuccess(() -> Component.literal("Table removed; construction cancelled. Previous graph optimization preserved."), false); return 1;
        }
        if (jobs.containsKey(source.getServer())) { source.sendFailure(Component.literal("A table is already being built on this server.")); return 0; }
        if (n.networkTable() != null) { source.sendFailure(Component.literal("Table already exists. Remove it before creating another.")); return 0; }
        try {
            var builder = new NetworkTruthTable.Builder(LogicProgram.compileFaces(n.tableStates(), n.wireIds(), n.bundleFaces()));
            jobs.put(source.getServer(), new Job(n, player.getUUID(), builder, System.nanoTime()));
            source.sendSuccess(() -> Component.literal("Building " + builder.total + " rows; " + builder.rows.length * 8 + " bytes. /circuit table status shows progress; remove cancels."), false);
            return 1;
        } catch (RuntimeException failure) { source.sendFailure(Component.literal("Cannot create table: " + failure.getMessage())); return 0; }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer(); var job = jobs.get(server); if (job == null) return;
        var player = server.getPlayerList().getPlayer(job.player);
        var data = NetworkSavedData.get(server);
        if (player == null || !NetworkOperations.canModify(player) || data.getNetwork(job.network.getId()) != job.network) { jobs.remove(server); return; }
        try {
            if (System.nanoTime() - job.started > 120_000_000_000L) throw new IllegalStateException("Construction time limit exceeded");
            long start = System.nanoTime();
            for (int i = 0; i < 128 && System.nanoTime() - start < 1_000_000; i++) {
                long unit = ServerWorkBudget.begin(server, ServerWorkBudget.Lane.SCAN); if (unit == 0) break;
                boolean complete;
                try { complete = job.builder.step(); } finally { ServerWorkBudget.end(server, ServerWorkBudget.Lane.SCAN, unit); }
                if (complete) {
                    job.network.setNetworkTable(job.builder.finish()); data.setDirty(); data.runtime().optimizationChanged(job.network); jobs.remove(server);
                    player.sendSystemMessage(Component.literal("Network #" + job.network.getId() + ": table created and saved. Use /circuit benchmark sequence; table status reports linked-cycle fallback.")); return;
                }
            }
        } catch (RuntimeException failure) {
            jobs.remove(server); player.sendSystemMessage(Component.literal("Table not created: " + failure.getMessage()));
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { jobs.remove(event.getServer()); }
}
