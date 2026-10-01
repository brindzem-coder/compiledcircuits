package com.example.compiledcircuits.gametest;

import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class ServerLookupGameTests {
    private static CompiledNetwork network(int id, Set<BlockPos> positions) {
        return new CompiledNetwork(id, "lookup", 0, "minecraft:overworld", positions, Set.of(), Set.of());
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Long> counters() throws Exception {
        var last = PerformanceDiagnostics.class.getDeclaredField("last"); last.setAccessible(true);
        var session = last.get(null);
        var field = session.getClass().getDeclaredField("counters"); field.setAccessible(true);
        return Map.copyOf((Map<String, Long>) field.get(session));
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void indexedEvents(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var server = level.getServer();
        var storage = server.overworld().getDataStorage(); var original = NetworkSavedData.get(server);
        var commands = server.getCommands().getDispatcher(); var source = server.createCommandSourceStack();
        NetworkIntegrityManager.clearPending();
        try {
            // Equal total membership, very different numbers of networks. No time threshold.
            for (int networkCount : new int[]{1, 500}) {
                var data = new NetworkSavedData();
                for (int i = 0; i < networkCount; i++) {
                    Set<BlockPos> positions = new HashSet<>();
                    for (int j = 0; j < 5000 / networkCount; j++) positions.add(new BlockPos(i * 5000 + j, 80, 100000));
                    data.addNetwork(network(i + 1, positions));
                }
                storage.set("compiledcircuits_networks", data);
                commands.execute("ccperf reset", source); commands.execute("ccperf start", source);
                for (int i = 0; i < 1000; i++) {
                    var absent = new BlockPos(i, 80, -100000);
                    NetworkIntegrityManager.scheduleCheck(level, absent);
                    helper.assertTrue(data.findNetworkContaining(level, absent) == null, "absent containing");
                    helper.assertTrue(data.findNetworkByInput(level, absent) == null, "absent input");
                    helper.assertTrue(data.findNetworkByOutput(level, absent) == null, "absent output");
                }
                NetworkIntegrityManager.processPending(server);
                commands.execute("ccperf stop", source);
                var metrics = counters();
                helper.assertTrue(metrics.getOrDefault("pending.unownedSkipped", 0L) == 1000, "all foreign positions filtered");
                helper.assertTrue(metrics.getOrDefault("pending.uniqueEnqueued", 0L) == 0
                        && metrics.getOrDefault("processPending.positions", 0L) == 0, "no foreign work queued");
                for (String name : List.of("findElementLocation", "findNetworkContaining", "findNetworkByInput", "findNetworkByOutput")) {
                    helper.assertTrue(metrics.getOrDefault("lookup." + name + ".indexProbes", 0L) == 1000, "one probe per request regardless of network count");
                    helper.assertTrue(metrics.getOrDefault("lookup." + name + ".networksVisited", 0L) == 0, "no network scan");
                }
                helper.assertTrue(metrics.getOrDefault("lookup.elementsVisited", 0L) == 0, "no element scan");
                System.out.println("LOOKUP networkCount=" + networkCount + " elements=5000 foreignPositions=1000 probes=4000 enqueued=0 scans=0");
            }
            var data = new NetworkSavedData(); storage.set("compiledcircuits_networks", data);
            var pos = helper.absolutePos(new BlockPos(1, 2, 1));
            var oldState = level.getBlockState(pos);
            try {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                var first = network(1, Set.of(pos)); data.addNetwork(first);
                NetworkIntegrityManager.scheduleCheck(level, pos);
                data.removeNetwork(1);
                NetworkIntegrityManager.processPending(server);
                helper.assertTrue(!first.isDamaged(), "removed queued owner is not mutated");
                data.addNetwork(first);
                NetworkIntegrityManager.scheduleCheck(level, pos);
                NetworkIntegrityManager.scheduleCheck(level, pos);
                var replacement = network(1, Set.of(pos)); data.replaceNetwork(replacement);
                NetworkIntegrityManager.processPending(server);
                helper.assertTrue(!first.isDamaged() && replacement.isDamaged(), "deferred check resolves replacement, not stale owner");
                helper.assertTrue(data.findElementLocation("minecraft:overworld", pos).network() == replacement, "damage retains membership");
                level.setBlock(pos, com.example.compiledcircuits.registry.ModBlocks.BASIC_WIRE.get().defaultBlockState(), 3);
                NetworkIntegrityManager.scheduleCheck(level, pos); NetworkIntegrityManager.processPending(server);
                helper.assertTrue(!replacement.isDamaged(), "repair retains current membership");
                var far = new BlockPos(15000000, 80, 15000000);
                helper.assertTrue(!level.hasChunkAt(far), "fixture chunk unloaded");
                data.addNetwork(network(2, Set.of(far)));
                NetworkIntegrityManager.scheduleCheck(level, far); NetworkIntegrityManager.processPending(server);
                helper.assertTrue(!level.hasChunkAt(far) && data.findElementLocation("minecraft:overworld", far) != null, "unloaded membership retained without chunk loading");
            } finally { level.setBlock(pos, oldState, 3); }
        } finally {
            NetworkIntegrityManager.clearPending();
            storage.set("compiledcircuits_networks", original);
            commands.execute("ccperf reset", source);
        }
        helper.succeed();
    }
}
