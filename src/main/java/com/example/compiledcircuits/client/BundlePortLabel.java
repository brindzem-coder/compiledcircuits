package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Full ID remains readable on hover even when a 64-digit label is small in the world. */
@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, value = Dist.CLIENT)
public final class BundlePortLabel {
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.screen != null || client.options.hideGui || !(client.hitResult instanceof BlockHitResult hit)) return;
        String id = null;
        var entity = client.level.getBlockEntity(hit.getBlockPos());
        if (entity instanceof BundleWireEntity wire && wire.port(hit.getDirection()) != null) id = wire.port(hit.getDirection()).id();
        else if (entity instanceof BundlePortEntity port) id = port.channel();
        if (id == null || id.isEmpty()) return;
        var label = Component.translatable("bundle.compiledcircuits.channel", id);
        var graphics = event.getGuiGraphics(); int width = client.getWindow().getGuiScaledWidth();
        float scale = Math.min(1, (width - 20f) / Math.max(1, client.font.width(label)));
        graphics.pose().pushPose(); graphics.pose().translate(width / 2f, client.getWindow().getGuiScaledHeight() / 2f + 18, 0);
        graphics.pose().scale(scale, scale, 1); graphics.drawCenteredString(client.font, label, 0, 0, 0xffffff); graphics.pose().popPose();
    }
}
