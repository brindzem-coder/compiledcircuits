package com.example.compiledcircuits.registry;

import com.example.compiledcircuits.CompiledCircuits;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CompiledCircuits.MOD_ID);

    public static final RegistryObject<CreativeModeTab> CIRCUITS = TABS.register("circuits",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.compiledcircuits"))
                    .icon(() -> ModItems.AND_GATE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // Logic gates, their ports, and the latch with its enable port.
                        output.accept(ModItems.AND_GATE.get());
                        output.accept(ModItems.NAND_GATE.get());
                        output.accept(ModItems.OR_GATE.get());
                        output.accept(ModItems.NOR_GATE.get());
                        output.accept(ModItems.XOR_GATE.get());
                        output.accept(ModItems.XNOR_GATE.get());
                        output.accept(ModItems.NOT_GATE.get());
                        output.accept(ModItems.GATE_INPUT.get());
                        output.accept(ModItems.GATE_OUTPUT.get());
                        output.accept(ModItems.GATE_SEPARATOR.get());
                        output.accept(ModItems.D_LATCH.get());
                        output.accept(ModItems.WDE_PORT.get());

                        // Bundled cable and its four ports.
                        output.accept(ModItems.BUNDLED_WIRE.get());
                        output.accept(ModItems.BUNDLE_INPUT.get());
                        output.accept(ModItems.BUNDLE_OUTPUT.get());
                        output.accept(ModItems.BUNDLE_NETWORK_INPUT.get());
                        output.accept(ModItems.BUNDLE_NETWORK_OUTPUT.get());

                        // Ordinary wires, endpoints, and signal controls.
                        output.accept(ModItems.BASIC_WIRE.get());
                        output.accept(ModItems.ID_WIRE.get());
                        output.accept(ModItems.INPUT_ENDPOINT.get());
                        output.accept(ModItems.OUTPUT_ENDPOINT.get());
                        output.accept(ModItems.NETWORK_INPUT.get());
                        output.accept(ModItems.NETWORK_OUTPUT.get());
                        output.accept(ModItems.SIGNAL_SWITCH.get());
                        output.accept(ModItems.SIGNAL_LAMP.get());
                        output.accept(ModItems.SWITCH_OUTPUT.get());
                        output.accept(ModItems.LAMP_INPUT.get());

                        // Tools.
                        output.accept(ModItems.NETWORK_SELECTOR.get());
                        output.accept(ModItems.CIRCUIT_COPIER.get());
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }
}
