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
    public static final RegistryObject<Item> NAND_GATE = ITEMS.register("nand_gate",
            () -> new BlockItem(ModBlocks.NAND_GATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> NOR_GATE = ITEMS.register("nor_gate",
            () -> new BlockItem(ModBlocks.NOR_GATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> XOR_GATE = ITEMS.register("xor_gate",
            () -> new BlockItem(ModBlocks.XOR_GATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> XNOR_GATE = ITEMS.register("xnor_gate",
            () -> new BlockItem(ModBlocks.XNOR_GATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> GATE_INPUT = ITEMS.register("gate_input",
            () -> new com.example.compiledcircuits.item.GateFacePortItem(new Item.Properties(), true));
    public static final RegistryObject<Item> GATE_OUTPUT = ITEMS.register("gate_output",
            () -> new com.example.compiledcircuits.item.GateFacePortItem(new Item.Properties(), false));
    public static final RegistryObject<Item> NOT_GATE = ITEMS.register("not_gate",
            () -> new BlockItem(ModBlocks.NOT_GATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> D_LATCH = ITEMS.register("d_latch",
            () -> new BlockItem(ModBlocks.D_LATCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> WDE_PORT = ITEMS.register("wde_port",
            () -> new com.example.compiledcircuits.item.WdePortItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GATE_SEPARATOR = ITEMS.register("gate_separator",
            () -> new com.example.compiledcircuits.item.GateSeparatorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> NETWORK_INPUT = ITEMS.register("network_input",
            () -> new com.example.compiledcircuits.item.NetworkPortItem(new Item.Properties(),true));
    public static final RegistryObject<Item> NETWORK_OUTPUT = ITEMS.register("network_output",
            () -> new com.example.compiledcircuits.item.NetworkPortItem(new Item.Properties(),false));

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
