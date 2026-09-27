package com.example.compiledcircuits.diagnostics;

import com.example.compiledcircuits.CompiledCircuits;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

/** Opt-in bounded diagnostics. Does not change world data or packet formats. */
@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID)
public final class PerformanceDiagnostics {
    private static volatile Session active;
    private static Session last;
    private static final int MAX_TICKS = 72000;
    private PerformanceDiagnostics() {}
    private static final class Session {
        final long started = System.nanoTime();
        final String id = UUID.randomUUID().toString();
        final Thread owner = Thread.currentThread();
        final Map<String, Long> counters = new TreeMap<>();
        final long[] ticks = new long[MAX_TICKS];
        final long heapStart = usedHeap();
        long heapEnd, ended, tickStart, dropped;
        int size;
    }
    private static long usedHeap() { return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }
    public static long begin() { return active == null ? 0 : System.nanoTime(); }
    public static void elapsed(String metric, long start) {
        if (start != 0) add(metric + ".nanos", System.nanoTime() - start);
    }
    public static void add(String metric, long value) {
        Session s = active;
        if (s == null || Thread.currentThread() != s.owner) return;
        synchronized (s) { if (active == s) s.counters.merge(metric, value, Long::sum); }
    }
    public static void max(String metric, long value) {
        Session s = active;
        if (s == null || Thread.currentThread() != s.owner) return;
        synchronized (s) { if (active == s) s.counters.merge(metric, value, Math::max); }
    }
    /** Counts real encoder invocations, NOT recipients or bytes on the wire. */
    public static <T> BiConsumer<T, FriendlyByteBuf> encoder(String type, BiConsumer<T, FriendlyByteBuf> delegate) {
        return (message, buffer) -> {
            Session s = active;
            if (s == null) { delegate.accept(message, buffer); return; }
            int before = buffer.writerIndex();
            delegate.accept(message, buffer);
            synchronized (s) {
                if (active != s) return;
                s.counters.merge("encode." + type + ".calls", 1L, Long::sum);
                s.counters.merge("encode." + type + ".payloadBytes", (long) buffer.writerIndex() - before, Long::sum);
            }
        };
    }
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) { register(event.getDispatcher()); }
    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ccperf").requires(source -> source.hasPermission(2))
            .then(Commands.literal("start").executes(ctx -> {
                if (active != null) { ctx.getSource().sendFailure(Component.literal("Diagnostics already running; stop first.")); return 0; }
                active = new Session(); last = null;
                ctx.getSource().sendSuccess(() -> Component.literal("Diagnostics started (max 72000 tick samples)."), false); return 1;
            }))
            .then(Commands.literal("stop").executes(ctx -> {
                Session s = active;
                if (s == null) { ctx.getSource().sendFailure(Component.literal("Diagnostics are not running.")); return 0; }
                synchronized (s) { active = null; s.ended = System.nanoTime(); s.heapEnd = usedHeap(); s.tickStart = 0; last = s; }
                ctx.getSource().sendSuccess(() -> Component.literal("Diagnostics stopped. Use /ccperf export to save."), false); return 1;
            }))
            .then(Commands.literal("reset").executes(ctx -> {
                active = null; last = null;
                ctx.getSource().sendSuccess(() -> Component.literal("Diagnostics cleared."), false); return 1;
            }))
            .then(Commands.literal("status").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal(active != null ? "Diagnostics running." : last != null ? "Diagnostics stopped; export available." : "Diagnostics disabled; no capture."), false); return 1;
            }))
            .then(Commands.literal("export").executes(ctx -> export(ctx.getSource()))));
    }
    private static int export(CommandSourceStack source) {
        Session s = last;
        if (active != null || s == null) { source.sendFailure(Component.literal("Stop a capture before exporting.")); return 0; }
        try {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("schema", 1); report.put("captureId", s.id);
            report.put("durationNanos", s.ended - s.started);
            report.put("javaVersion", System.getProperty("java.version"));
            report.put("jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
            report.put("heapStartBytes", s.heapStart); report.put("heapEndBytes", s.heapEnd);
            report.put("heapMaxBytes", Runtime.getRuntime().maxMemory());
            report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            report.put("counters", s.counters);
            report.put("tickIntervalNanos", Arrays.copyOf(s.ticks, s.size));
            report.put("droppedTickSamples", s.dropped);
            long[] sorted = Arrays.copyOf(s.ticks, s.size); Arrays.sort(sorted);
            if (s.size > 0) {
                Map<String, Long> quantiles = new LinkedHashMap<>();
                for (int percentile : new int[]{50,95,99,100}) quantiles.put("p" + percentile, sorted[(int)Math.ceil(s.size * percentile / 100.0) - 1]);
                report.put("tickIntervalPercentilesNanos", quantiles);
            }
            report.put("notes", List.of("Tick interval: Forge START/HIGHEST to END/LOWEST, not entire Minecraft tick or sleep.",
                "Tick samples retain first 72000; counters cover full capture. Dropped samples invalidate full-window percentiles.",
                "Encode calls and payload bytes exclude discriminator/framing, do not count broadcast recipients, and may include client encoders in singleplayer.",
                "Heap belongs to entire JVM, including client in singleplayer; no forced GC.",
                "Enabled instrumentation has overhead; compare identical instrumentation. Nested timings must not be summed."));
            Path dir = source.getServer().getServerDirectory().toPath().resolve("debug/compiledcircuits-performance");
            Files.createDirectories(dir);
            Path file = dir.resolve(s.id + ".json");
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardOpenOption.CREATE_NEW);
            source.sendSuccess(() -> Component.literal("Saved diagnostics: " + file.toAbsolutePath()), false); return 1;
        } catch (java.io.IOException failure) {
            source.sendFailure(Component.literal("Cannot export diagnostics: " + failure.getMessage())); return 0;
        }
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void tickStart(TickEvent.ServerTickEvent event) {
        Session s = active;
        if (s != null && event.phase == TickEvent.Phase.START) s.tickStart = System.nanoTime();
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void tickEnd(TickEvent.ServerTickEvent event) {
        Session s = active;
        if (s == null || event.phase != TickEvent.Phase.END || s.tickStart == 0) return;
        long elapsed = System.nanoTime() - s.tickStart; s.tickStart = 0;
        if (s.size < MAX_TICKS) s.ticks[s.size++] = elapsed; else s.dropped++;
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { active = null; last = null; }
}
