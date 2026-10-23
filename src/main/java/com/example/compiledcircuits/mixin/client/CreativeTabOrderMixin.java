package com.example.compiledcircuits.mixin.client;

import com.example.compiledcircuits.registry.ModCreativeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.CreativeModeTabRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = CreativeModeTabRegistry.class, remap = false)
abstract class CreativeTabOrderMixin {
    @Inject(method = "getSortedCreativeModeTabs", at = @At("RETURN"), cancellable = true, remap = false)
    private static void compiledcircuits$pinFirstPage(CallbackInfoReturnable<List<CreativeModeTab>> callback) {
        if (!ModCreativeTabs.CIRCUITS.isPresent()) return;
        var tab = ModCreativeTabs.CIRCUITS.get();
        var sorted = callback.getReturnValue();
        if (!sorted.contains(tab) || sorted.get(0) == tab) return;

        // Forge paginates this list in groups of ten. Keep the other tabs in their existing order.
        // Returning a copy also avoids changing Forge's registry or introducing sorting cycles.
        var pinned = new ArrayList<>(sorted);
        pinned.remove(tab);
        pinned.add(0, tab);
        callback.setReturnValue(List.copyOf(pinned));
    }
}
