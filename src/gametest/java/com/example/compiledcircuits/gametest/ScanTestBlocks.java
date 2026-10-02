package com.example.compiledcircuits.gametest;

import com.example.compiledcircuits.block.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

/** Test-only directional endpoints; not part of the shipped mod. */
@Mod.EventBusSubscriber(modid="compiledcircuits",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ScanTestBlocks {
    static InputEndpointBlock input;
    static OutputEndpointBlock output;
    @SubscribeEvent public static void register(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCKS)) return;
        input=new InputEndpointBlock(BlockBehaviour.Properties.of()) {
            @Override public boolean canWireConnect(BlockState state,Direction side) {return side.getAxis()==Direction.Axis.Y;}
        };
        output=new OutputEndpointBlock(BlockBehaviour.Properties.of()) {
            @Override public boolean canWireConnect(BlockState state,Direction side) {
                return state.getValue(FACING)!=Direction.EAST && side.getAxis()==Direction.Axis.Y;
            }
        };
        event.register(ForgeRegistries.Keys.BLOCKS,helper->{
            helper.register("scan_test_input",input);
            helper.register("scan_test_output",output);
        });
    }
}
