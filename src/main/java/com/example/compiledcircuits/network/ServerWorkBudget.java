package com.example.compiledcircuits.network;

import com.example.compiledcircuits.config.ServerConfig;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.server.MinecraftServer;
import java.util.Arrays;

/** One accounting frame for this world's server work, including reentrant requests. */
public final class ServerWorkBudget {
    public enum Lane {
        POINT, RECHECK, REPAIR, AUDIT, SCAN, RUNTIME, SYNC;
        final String workMetric = "work." + name() + ".units";
        final String timeMetric = "work." + name() + ".nanos";
        final String peakMetric = "work." + name() + ".tickPeak";
    }
    private int tick = Integer.MIN_VALUE, total, depth;
    private long elapsed, integrityNanos, runtimeNanos, scanNanos;
    private final int[] work = new int[Lane.values().length];
    private void reset(int now) {
        if (tick == now) return;
        tick = now; total = depth = 0; elapsed = integrityNanos = runtimeNanos = scanNanos = 0; Arrays.fill(work, 0);
    }
    private static ServerWorkBudget frame(MinecraftServer server) { return NetworkSavedData.get(server).workBudget; }
    public static boolean inWork(MinecraftServer server) { return frame(server).depth != 0; }
    public static boolean available(MinecraftServer server, Lane lane) {
        var f = frame(server); f.reset(server.getTickCount());
        int cap = switch (lane) {
            case POINT -> ServerConfig.POINT_WORK.get(); case RECHECK -> ServerConfig.RECHECK_WORK.get();
            case REPAIR -> ServerConfig.REPAIR_WORK.get(); case AUDIT -> ServerConfig.AUDIT_WORK.get();
            case SCAN -> CompilationJobs.WORK_PER_TICK; case RUNTIME -> NetworkRuntime.WORK_PER_TICK; case SYNC -> 2;
        };
        if (f.total >= ServerConfig.TOTAL_WORK.get() || f.work[lane.ordinal()] >= cap
                || f.elapsed >= ServerConfig.TOTAL_MICROS.get() * 1000L) return false;
        // Reserve operation shares even at the minimum global setting: audit/repair cannot
        // consume every slot before compilation and runtime get their turn. Two are for sync.
        int usable=ServerConfig.TOTAL_WORK.get()-2, integrityShare=usable/6, scanShare=usable*2/3;
        int integrityWork=f.work[Lane.POINT.ordinal()]+f.work[Lane.RECHECK.ordinal()]
                +f.work[Lane.REPAIR.ordinal()]+f.work[Lane.AUDIT.ordinal()];
        boolean shareAvailable=switch(lane) {
            case POINT, RECHECK, REPAIR, AUDIT -> integrityWork<integrityShare;
            case SCAN -> f.work[lane.ordinal()]<scanShare;
            case RUNTIME -> f.work[lane.ordinal()]<usable-integrityShare-scanShare;
            case SYNC -> true;
        };
        if(!shareAvailable)return false;
        return switch (lane) {
            case POINT, RECHECK, REPAIR, AUDIT -> f.integrityNanos < ServerConfig.INTEGRITY_MICROS.get() * 1000L;
            case SCAN -> f.scanNanos < CompilationJobs.NANOS_PER_TICK;
            case RUNTIME -> f.runtimeNanos < 2_000_000L;
            case SYNC -> true;
        };
    }
    public static long begin(MinecraftServer server, Lane lane) {
        if (!available(server, lane)) return 0;
        var f = frame(server); f.work[lane.ordinal()]++; f.total++; f.depth++;
        return System.nanoTime();
    }
    public static void end(MinecraftServer server, Lane lane, long start) {
        if (start == 0) return;
        long nanos = System.nanoTime() - start; var f = frame(server); f.depth--; f.elapsed += nanos;
        switch (lane) {
            case POINT, RECHECK, REPAIR, AUDIT -> f.integrityNanos += nanos;
            case SCAN -> f.scanNanos += nanos; case RUNTIME -> f.runtimeNanos += nanos; default -> { }
        }
        PerformanceDiagnostics.add(lane.workMetric, 1); PerformanceDiagnostics.add(lane.timeMetric, nanos);
        PerformanceDiagnostics.max(lane.peakMetric, f.work[lane.ordinal()]);
        PerformanceDiagnostics.max("work.total.tickPeak", f.total); PerformanceDiagnostics.max("work.total.tickNanos", f.elapsed);
        PerformanceDiagnostics.max("work.operation.maxNanos", nanos);
        if (nanos > ServerConfig.INTEGRITY_MICROS.get() * 1000L) PerformanceDiagnostics.add("work.operation.overruns", 1);
    }
}
