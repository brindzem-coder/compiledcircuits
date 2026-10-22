package com.example.compiledcircuits.block;

import net.minecraft.core.*;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** FACING always points away from the cable, towards the ordinary signal connection. */
public final class BundlePortBlock extends EndpointBlock implements EntityBlock {
    private final boolean input, external;
    public BundlePortBlock(Properties properties, boolean input, boolean external) {
        super(properties); this.input = input; this.external = external;
    }
    public boolean isInput() { return input; }
    public boolean isExternal() { return external; }
    @Override public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
            BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> box(0,4,4,16,12,12);
            case Y -> box(4,0,4,12,16,12);
            case Z -> box(4,4,0,12,12,16);
        };
    }
    public static Direction cableSide(BlockState state) { return state.getValue(FACING).getOpposite(); }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target,
            net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        var item = external ? (input ? com.example.compiledcircuits.registry.ModItems.BUNDLE_NETWORK_INPUT : com.example.compiledcircuits.registry.ModItems.BUNDLE_NETWORK_OUTPUT)
                : (input ? com.example.compiledcircuits.registry.ModItems.BUNDLE_INPUT : com.example.compiledcircuits.registry.ModItems.BUNDLE_OUTPUT);
        var stack = new net.minecraft.world.item.ItemStack(item.get());
        if (level.getBlockEntity(pos) instanceof BundlePortEntity port) stack.getOrCreateTag().putString("ChannelId", port.channel());
        return stack;
    }
    @Override public boolean canWireConnect(BlockState state, Direction side) {
        return side == cableSide(state) || !external && side == state.getValue(FACING);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BundlePortEntity(pos, state); }
}
