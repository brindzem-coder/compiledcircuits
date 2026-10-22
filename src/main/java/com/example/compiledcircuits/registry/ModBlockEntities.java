package com.example.compiledcircuits.registry;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.IdWireEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.*;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CompiledCircuits.MOD_ID);
    public static final RegistryObject<BlockEntityType<com.example.compiledcircuits.block.BundleWireEntity>> BUNDLE_WIRE = TYPES.register("bundle_wire", () -> BlockEntityType.Builder.of(com.example.compiledcircuits.block.BundleWireEntity::new, ModBlocks.BUNDLED_WIRE.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.example.compiledcircuits.block.BundlePortEntity>> BUNDLE_PORT = TYPES.register("bundle_port", () -> BlockEntityType.Builder.of(com.example.compiledcircuits.block.BundlePortEntity::new, ModBlocks.BUNDLE_INPUT.get(), ModBlocks.BUNDLE_OUTPUT.get(), ModBlocks.BUNDLE_NETWORK_INPUT.get(), ModBlocks.BUNDLE_NETWORK_OUTPUT.get()).build(null));
    public static final RegistryObject<BlockEntityType<IdWireEntity>> ID_WIRE = TYPES.register("id_wire", () -> BlockEntityType.Builder.of(IdWireEntity::new, ModBlocks.ID_WIRE.get()).build(null));
}
