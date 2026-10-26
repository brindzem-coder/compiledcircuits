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
        player.getPersistentData().remove("compiledcircuits_selected_lane");
        CompilationJobs.cancel(player);
        CompoundTag data = player.getPersistentData();

        data.putBoolean(KEY_HAS_SELECTION, true);

        data.putInt(KEY_X, pos.getX());
        data.putInt(KEY_Y, pos.getY());
        data.putInt(KEY_Z, pos.getZ());
    }

    public static void set(net.minecraft.server.level.ServerPlayer player, BlockPos pos, net.minecraft.world.phys.Vec3 hit) {
        set(player,pos);var state=player.serverLevel().getBlockState(pos);
        if(state.getBlock() instanceof com.example.compiledcircuits.block.CrossingWireBlock){
            var axis=com.example.compiledcircuits.block.CrossingWireBlock.hit(state,pos,hit);
            if(axis!=null)player.getPersistentData().putInt("compiledcircuits_selected_lane",1<<axis.ordinal());
        }
    }
    public static int mask(ServerPlayer player){return player.getPersistentData().contains("compiledcircuits_selected_lane")?player.getPersistentData().getInt("compiledcircuits_selected_lane"):7;}
    public static CompiledNetwork network(ServerPlayer player){
        var pos=get(player);if(pos==null)return null;var data=NetworkSavedData.get(player.getServer());
        var matches=data.getMembershipOwners(player.serverLevel().dimension().location().toString(),pos).stream()
                .filter(o->(Crossings.mask(o.element())&mask(player))!=0).map(NetworkSavedData.ElementLocation::network).distinct().toList();
        return matches.size()==1?matches.get(0):null;
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
        CompilationJobs.cancel(player);

        CompoundTag data = player.getPersistentData();

        data.remove(KEY_HAS_SELECTION);
        data.remove(KEY_X);
        data.remove(KEY_Y);
        data.remove(KEY_Z);
    }
}