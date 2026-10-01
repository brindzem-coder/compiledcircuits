package com.example.compiledcircuits.gametest;

import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.Set;
import java.util.stream.Collectors;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class DiagnosticsGameTests {
    @GameTest(template="empty", timeoutTicks=100)
    public static void diagnosticsCapture(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack();
        Path folder = server.getServerDirectory().toPath().resolve("debug/compiledcircuits-performance");
        Set<Path> before;
        if (Files.exists(folder)) {
            try (var paths = Files.list(folder)) { before = paths.collect(Collectors.toSet()); }
        } else before = Set.of();
        var encoder = PerformanceDiagnostics.<Integer>encoder("test", (value, buffer) -> buffer.writeInt(value));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            helper.assertTrue(dispatcher.execute("ccperf reset", source) == 1, "reset");
            encoder.accept(123, buffer);
            helper.assertTrue(buffer.readInt() == 123, "disabled encoder preserves bytes");
            helper.assertTrue(dispatcher.execute("ccperf start", source) == 1, "start");
            helper.assertTrue(dispatcher.execute("ccperf start", source) == 0, "double start rejected");
            PerformanceDiagnostics.add("test.counter", 3);
            PerformanceDiagnostics.add("test.counter", 2);
            PerformanceDiagnostics.max("test.peak", 7);
            PerformanceDiagnostics.max("test.peak", 4);
            encoder.accept(456, buffer);
            helper.assertTrue(buffer.readInt() == 456, "enabled encoder preserves bytes");
            // Exercise actual command paths, not a synthetic timer invocation.
            helper.assertTrue(dispatcher.execute("circuit compile", source) == 0, "console compilation rejected");
            var level = helper.getLevel();
            var player = new net.minecraftforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "diagnostics"));
            var selection = com.example.compiledcircuits.network.NetworkSelectionData.get(player);
            var data = com.example.compiledcircuits.network.NetworkSavedData.get(server);
            var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
            var neighbor = pos.east();
            var oldInput = level.getBlockState(pos);
            var oldOutput = level.getBlockState(neighbor);
            int createdId = -1;
            try {
                com.example.compiledcircuits.network.NetworkSelectionData.clear(player);
                var playerSource = player.createCommandSourceStack();
                helper.assertTrue(dispatcher.execute("circuit compile", playerSource) == 0, "missing selection rejected");
                level.setBlock(pos, com.example.compiledcircuits.registry.ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(), 3);
                level.setBlock(neighbor, com.example.compiledcircuits.registry.ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState(), 3);
                com.example.compiledcircuits.network.NetworkSelectionData.set(player, pos);
                helper.assertTrue(dispatcher.execute("circuit compile", playerSource) == 1, "command compile succeeds");
                var created = data.findNetworkContaining(level, pos);
                helper.assertTrue(created != null && created.getElements().size() == 2, "network committed");
                createdId = created.getId();
                helper.assertTrue(dispatcher.execute("circuit compile", playerSource) == 0, "duplicate rejected");
                helper.assertTrue(data.getNetwork(createdId) == created, "rejection preserves network");
            } finally {
                if (createdId >= 0) data.removeNetwork(createdId);
                level.setBlock(pos, oldInput, 3);
                level.setBlock(neighbor, oldOutput, 3);
                if (selection == null) com.example.compiledcircuits.network.NetworkSelectionData.clear(player);
                else com.example.compiledcircuits.network.NetworkSelectionData.set(player, selection);
            }
            helper.assertTrue(dispatcher.execute("ccperf stop", source) == 1, "stop");
            PerformanceDiagnostics.add("test.counter", 99);
            encoder.accept(789, buffer);
            helper.assertTrue(buffer.readInt() == 789, "stopped encoder preserves bytes");
            helper.assertTrue(dispatcher.execute("ccperf export", source) == 1, "export");
            Path report;
            try (var paths = Files.list(folder)) {
                report = paths.filter(path -> !before.contains(path)).findFirst().orElseThrow();
            }
            var json = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
            var counters = json.getAsJsonObject("counters");
            helper.assertTrue(counters.get("compile.command.calls").getAsLong() == 4, "all command attempts counted");
            helper.assertTrue(counters.get("compile.command.success").getAsLong() == 1, "successful compile counted");
            helper.assertTrue(counters.get("compile.command.rejected").getAsLong() == 3, "early and conflict rejections counted");
            helper.assertTrue(!counters.has("compile.command.exceptions"), "no unexpected compile exceptions");
            helper.assertTrue(counters.get("compile.command.nanos").getAsLong() >= counters.get("scan.nanos").getAsLong(), "full timer includes scan");
            helper.assertTrue(counters.get("test.counter").getAsLong() == 5, "stopped counters frozen");
            helper.assertTrue(counters.get("test.peak").getAsLong() == 7, "peak aggregation");
            helper.assertTrue(counters.get("encode.test.calls").getAsLong() == 1, "only active encodes counted");
            helper.assertTrue(counters.get("encode.test.payloadBytes").getAsLong() == 4, "actual encoded byte delta");
            helper.assertTrue(json.get("durationNanos").getAsLong() > 0, "capture duration");
            helper.assertTrue(dispatcher.execute("ccperf reset", source) == 1, "reset captured state");
            helper.assertTrue(dispatcher.execute("ccperf export", source) == 0, "reset removes export");
            helper.succeed();
        } finally {
            buffer.release(); dispatcher.execute("ccperf reset", source);
        }
    }
}
