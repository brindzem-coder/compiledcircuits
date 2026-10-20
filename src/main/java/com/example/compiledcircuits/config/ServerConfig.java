package com.example.compiledcircuits.config;

import net.minecraftforge.common.ForgeConfigSpec;
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue EXACT_BLOCK_STATE_INTEGRITY;
    public static final ForgeConfigSpec.IntValue NETWORK_CAPACITY, DIMENSION_CAPACITY;
    public static final ForgeConfigSpec.IntValue TOTAL_WORK, TOTAL_MICROS, INTEGRITY_MICROS,
            POINT_WORK, RECHECK_WORK, REPAIR_WORK, AUDIT_WORK, MAX_PENDING, MAX_REPAIR_JOBS,
            MAX_REPAIR_TARGETS, REPAIR_LIFETIME_TICKS, REPAIR_LIFETIME_SECONDS;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        EXACT_BLOCK_STATE_INTEGRITY = builder.comment("Compare all compiled properties, including dynamic wire connections. Requires restart.")
                .worldRestart().define("exactBlockStateIntegrity", false);
        builder.push("membershipCapacity");
        NETWORK_CAPACITY=builder.comment("Admission limit per network; existing larger networks are preserved. Requires world restart.").worldRestart().defineInRange("elementsPerNetwork",50000,1,50000);
        DIMENSION_CAPACITY=builder.comment("Reserved membership records per dimension, including isolated records. Requires world restart.").worldRestart().defineInRange("elementsPerDimension",1000000,1,1000000);
        builder.pop();
        builder.push("workBudget");
        TOTAL_WORK = builder.worldRestart().defineInRange("totalOperationsPerTick", 12288, 128, 65536);
        TOTAL_MICROS = builder.worldRestart().defineInRange("totalMicrosPerTick", 9000, 100, 100000);
        INTEGRITY_MICROS = builder.worldRestart().defineInRange("integrityMicrosPerTick", 2000, 100, 20000);
        POINT_WORK = builder.worldRestart().defineInRange("pointChecksPerTick", 256, 1, 8192);
        RECHECK_WORK = builder.worldRestart().defineInRange("fullRecheckStepsPerTick", 256, 1, 8192);
        REPAIR_WORK = builder.worldRestart().defineInRange("repairStepsPerTick", 32, 1, 1024);
        AUDIT_WORK = builder.worldRestart().defineInRange("auditStepsPerTick", 128, 1, 4096);
        MAX_PENDING = builder.worldRestart().defineInRange("maxPendingPositions", 2048, 1, 65536);
        MAX_REPAIR_JOBS = builder.worldRestart().defineInRange("maxRepairJobs", 8, 1, 64);
        MAX_REPAIR_TARGETS = builder.worldRestart().defineInRange("maxRetainedRepairTargets", 100000, 1, 1000000);
        REPAIR_LIFETIME_TICKS = builder.worldRestart().defineInRange("repairLifetimeTicks", 2400, 20, 72000);
        REPAIR_LIFETIME_SECONDS = builder.worldRestart().defineInRange("repairLifetimeSeconds", 180, 1, 3600);
        builder.pop();
        SPEC = builder.build();
    }
    private ServerConfig() {}
}
