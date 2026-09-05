package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkSelectionData {

    private static final String KEY_X = "compiledcircuits_selected_x";
    private static final String KEY_Y = "compiledcircuits_selected_y";
    private static final String KEY_Z = "compiledcircuits_selected_z";

    private static final String KEY_HAS_SELECTION =
            "compiledcircuits_has_selection";

    private NetworkSelectionData() {
    }

    public static void set(
            ServerPlayer player,
            BlockPos pos
    ) {
        CompoundTag data = player.getPersistentData();

        data.putBoolean(KEY_HAS_SELECTION, true);

        data.putInt(KEY_X, pos.getX());
        data.putInt(KEY_Y, pos.getY());
        data.putInt(KEY_Z, pos.getZ());
    }

    public static BlockPos get(ServerPlayer player) {

        CompoundTag data = player.getPersistentData();

        if (!data.getBoolean(KEY_HAS_SELECTION)) {
            return null;
        }

        return new BlockPos(
                data.getInt(KEY_X),
                data.getInt(KEY_Y),
                data.getInt(KEY_Z)
        );
    }

    public static void clear(ServerPlayer player) {

        CompoundTag data = player.getPersistentData();

        data.remove(KEY_HAS_SELECTION);
        data.remove(KEY_X);
        data.remove(KEY_Y);
        data.remove(KEY_Z);
    }
}