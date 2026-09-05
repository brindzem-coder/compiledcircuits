package com.example.compiledcircuits.block;

import com.example.compiledcircuits.network.CompiledNetwork;
import com.example.compiledcircuits.network.NetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public class OutputEndpointBlock
        extends EndpointBlock {

    public OutputEndpointBlock(
            Properties properties
    ) {
        super(properties);
    }

    @Override
    public boolean isSignalSource(
            BlockState state
    ) {
        return true;
    }

    @Override
    public int getSignal(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            Direction direction
    ) {

        return getNetworkSignal(
                level,
                pos
        );
    }

    @Override
    public int getDirectSignal(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            Direction direction
    ) {

        return getNetworkSignal(
                level,
                pos
        );
    }

    private int getNetworkSignal(
            BlockGetter level,
            BlockPos pos
    ) {

        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }

        NetworkSavedData data =
                NetworkSavedData.get(
                        serverLevel.getServer()
                );

        CompiledNetwork network =
                data.findNetworkByOutput(
                        serverLevel,
                        pos
                );

        if (network == null) {
            return 0;
        }

        return network.isPowered()
                ? 15
                : 0;
    }
}