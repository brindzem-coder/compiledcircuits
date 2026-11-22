package com.example.compiledcircuits.mixin.client;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;
@Mixin(AbstractContainerScreen.class)
public interface HotbarScreenAccessor {
    @Accessor("topPos") void compiledcircuits$setTop(int value);
    @Accessor("isQuickCrafting") void compiledcircuits$setQuickCrafting(boolean value);
    @Accessor("quickCraftSlots") Set<Slot> compiledcircuits$quickCraftSlots();
    @Accessor("skipNextRelease") void compiledcircuits$setSkipRelease(boolean value);
    @Accessor("doubleclick") void compiledcircuits$setDoubleClick(boolean value);
    @Accessor("lastClickSlot") void compiledcircuits$setLastClickSlot(Slot slot);
}
