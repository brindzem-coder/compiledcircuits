package com.example.compiledcircuits.config;

import net.minecraftforge.common.ForgeConfigSpec;
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue EXACT_BLOCK_STATE_INTEGRITY;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        EXACT_BLOCK_STATE_INTEGRITY = builder.comment("Compare all compiled properties, including dynamic wire connections. Requires restart.")
                .worldRestart().define("exactBlockStateIntegrity", false);
        SPEC = builder.build();
    }
    private ServerConfig() {}
}
