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

    public static final RegistryObject<Block> AND_GATE = BLOCKS.register("and_gate",
            () -> new com.example.compiledcircuits.block.GateBodyBlock(BlockBehaviour.Properties.of().strength(1),
                    com.example.compiledcircuits.block.GateBodyBlock.Kind.AND));
    public static final RegistryObject<Block> OR_GATE = BLOCKS.register("or_gate",
            () -> new com.example.compiledcircuits.block.GateBodyBlock(BlockBehaviour.Properties.of().strength(1),
                    com.example.compiledcircuits.block.GateBodyBlock.Kind.OR));
    public static final RegistryObject<Block> GATE_INPUT = BLOCKS.register("gate_input",
            () -> new com.example.compiledcircuits.block.GatePortBlock(BlockBehaviour.Properties.of().strength(1).noOcclusion(), true));
    public static final RegistryObject<Block> GATE_OUTPUT = BLOCKS.register("gate_output",
            () -> new com.example.compiledcircuits.block.GatePortBlock(BlockBehaviour.Properties.of().strength(1).noOcclusion(), false));
    public static final RegistryObject<Block> NOT_GATE = BLOCKS.register("not_gate",
            () -> new com.example.compiledcircuits.block.NotGateBlock(BlockBehaviour.Properties.of().strength(1).noOcclusion()));

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
