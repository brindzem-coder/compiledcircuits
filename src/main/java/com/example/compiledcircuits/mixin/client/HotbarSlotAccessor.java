package com.example.compiledcircuits.mixin.client;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Slot.class)
public interface HotbarSlotAccessor {
    @Mutable @Accessor("x") void compiledcircuits$setX(int value);
    @Mutable @Accessor("y") void compiledcircuits$setY(int value);
}
