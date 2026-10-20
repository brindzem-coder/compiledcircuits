package com.example.compiledcircuits.registry;

import com.example.compiledcircuits.CompiledCircuits;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.example.compiledcircuits.item.NetworkSelectorItem;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CompiledCircuits.MOD_ID);

    public static final RegistryObject<Item> AND_GATE = ITEMS.register("and_gate",
            () -> new BlockItem(ModBlocks.AND_GATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> OR_GATE = ITEMS.register("or_gate",
            () -> new BlockItem(ModBlocks.OR_GATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> GATE_INPUT = ITEMS.register("gate_input",
            () -> new BlockItem(ModBlocks.GATE_INPUT.get(), new Item.Properties()));
    public static final RegistryObject<Item> GATE_OUTPUT = ITEMS.register("gate_output",
            () -> new BlockItem(ModBlocks.GATE_OUTPUT.get(), new Item.Properties()));
    public static final RegistryObject<Item> NOT_GATE = ITEMS.register("not_gate",
            () -> new BlockItem(ModBlocks.NOT_GATE.get(), new Item.Properties()));

    public static final RegistryObject<Item> BASIC_WIRE =
            ITEMS.register("basic_wire",
                    () -> new BlockItem(
                            ModBlocks.BASIC_WIRE.get(),
                            new Item.Properties()
                    ));

    public static final RegistryObject<Item> INPUT_ENDPOINT =
            ITEMS.register(
                    "input_endpoint",
                    () -> new BlockItem(
                            ModBlocks.INPUT_ENDPOINT.get(),
                            new Item.Properties()
                    )
            );

    public static final RegistryObject<Item> OUTPUT_ENDPOINT =
            ITEMS.register(
                    "output_endpoint",
                    () -> new BlockItem(
                            ModBlocks.OUTPUT_ENDPOINT.get(),
                            new Item.Properties()
                    )
            );

    public static final RegistryObject<Item> NETWORK_SELECTOR =
            ITEMS.register(
                    "network_selector",
                    () -> new NetworkSelectorItem(
                            new Item.Properties()
                                    .stacksTo(1)
                    )
            );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
