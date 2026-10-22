package com.example.compiledcircuits.registry;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.IdWireEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.*;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CompiledCircuits.MOD_ID);
    public static final RegistryObject<BlockEntityType<IdWireEntity>> ID_WIRE = TYPES.register("id_wire", () -> BlockEntityType.Builder.of(IdWireEntity::new, ModBlocks.ID_WIRE.get()).build(null));
}
