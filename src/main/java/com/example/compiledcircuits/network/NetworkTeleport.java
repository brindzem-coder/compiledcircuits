package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class NetworkTeleport {
    private NetworkTeleport() {}
    static void teleport(ServerPlayer player, CompiledNetwork network) {
        var level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(network.getDimension())));
        if (level == null || network.getElements().isEmpty()) throw new IllegalArgumentException("Network destination is unavailable.");
        var origin = network.getElements().iterator().next().getPos();
        if (!level.getWorldBorder().isWithinBounds(origin)) throw new IllegalArgumentException("Network is outside the world border.");
        // Teleporting explicitly requests this small destination area; no forced chunk tickets remain.
        for (int x = (origin.getX()-4)>>4; x <= (origin.getX()+4)>>4; x++)
            for (int z = (origin.getZ()-4)>>4; z <= (origin.getZ()+4)>>4; z++) level.getChunk(x,z);
        var destination = find(level, player, origin, true);
        boolean fly = false;
        if (destination == null && player.getAbilities().mayfly) { destination = find(level, player, origin, false); fly = destination != null; }
        if (destination == null) throw new IllegalArgumentException("No safe teleport location near this network.");
        player.stopRiding();
        player.teleportTo(level, destination.getX()+.5, destination.getY(), destination.getZ()+.5, player.getYRot(), player.getXRot());
        player.fallDistance=0;
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (fly) { player.getAbilities().flying=true; player.onUpdateAbilities(); }
    }
    static BlockPos find(ServerLevel level, ServerPlayer player, BlockPos origin, boolean floorRequired) {
        for (int distance=0;distance<=32;distance++) {
            int dy = distance==0 ? 0 : (distance%2==1 ? (distance+1)/2 : -distance/2);
            for (int radius=0;radius<=4;radius++) for (int x=-radius;x<=radius;x++) for (int z=-radius;z<=radius;z++) {
                if (Math.max(Math.abs(x),Math.abs(z))!=radius) continue;
                var feet=origin.offset(x,dy,z);
                if (safe(level,player,feet,floorRequired)) return feet;
            }
        }
        return null;
    }
    static boolean safe(ServerLevel level,ServerPlayer player,BlockPos feet,boolean floorRequired) {
        if (level.isOutsideBuildHeight(feet.below()) || level.isOutsideBuildHeight(feet.above())
                || !level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)) return false;
        var floor=level.getBlockState(feet.below());
        if (hazard(floor) || !floor.getFluidState().isEmpty()) return false;
        for (var pos : new BlockPos[]{feet,feet.above()}) {
            var state=level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || hazard(state)) return false;
        }
        var box=new AABB(feet.getX()+.2,feet.getY(),feet.getZ()+.2,feet.getX()+.8,feet.getY()+2,feet.getZ()+.8);
        return level.noCollision(player,box) && (!floorRequired || floor.isFaceSturdy(level,feet.below(),Direction.UP));
    }
    private static boolean hazard(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW);
    }
}
