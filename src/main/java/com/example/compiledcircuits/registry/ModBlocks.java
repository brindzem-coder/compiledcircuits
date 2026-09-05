package com.example.compiledcircuits.registry;

import com.example.compiledcircuits.CompiledCircuits;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.example.compiledcircuits.block.BasicWireBlock;
import com.example.compiledcircuits.block.InputEndpointBlock;
import com.example.compiledcircuits.block.OutputEndpointBlock;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CompiledCircuits.MOD_ID);

    public static final RegistryObject<Block> BASIC_WIRE =
            BLOCKS.register("basic_wire",
                    () -> new BasicWireBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_RED)
                                    .strength(1.0F)
                                    .noOcclusion()
                    ));

    public static final RegistryObject<Block> INPUT_ENDPOINT =
            BLOCKS.register(
                    "input_endpoint",
                    () -> new InputEndpointBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GREEN)
                                    .strength(1.0F)
                                    .noOcclusion()
                    )
            );

    public static final RegistryObject<Block> OUTPUT_ENDPOINT =
            BLOCKS.register(
                    "output_endpoint",
                    () -> new OutputEndpointBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_BLUE)
                                    .strength(1.0F)
                                    .noOcclusion()
                    )
            );

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}