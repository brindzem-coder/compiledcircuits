package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.item.IdWireItem;
import com.example.compiledcircuits.network.WireIds;
import com.example.compiledcircuits.registry.ModItems;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Stack-specific labels shared by inventory, hotbar and carried-stack icons. */
@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ItemIdDecorations {
    @SubscribeEvent public static void register(RegisterItemDecorationsEvent event) {
        event.register(ModItems.ID_WIRE.get(), ItemIdDecorations::render);
        event.register(ModItems.BUNDLE_INPUT.get(), ItemIdDecorations::render);
        event.register(ModItems.BUNDLE_OUTPUT.get(), ItemIdDecorations::render);
        event.register(ModItems.BUNDLE_NETWORK_INPUT.get(), ItemIdDecorations::render);
        event.register(ModItems.BUNDLE_NETWORK_OUTPUT.get(), ItemIdDecorations::render);
    }
    private static boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        String id = "0";
        // Rendering must not create or change the stack's NBT.
        if (stack.hasTag()) {
            try { id = WireIds.normalize(stack.getTag().getString(stack.getItem() instanceof IdWireItem ? "WireId" : "ChannelId")); }
            catch (IllegalArgumentException ignored) { }
        }
        boolean wire = stack.getItem() instanceof IdWireItem;
        boolean network = stack.is(ModItems.BUNDLE_NETWORK_INPUT.get()) || stack.is(ModItems.BUNDLE_NETWORK_OUTPUT.get());
        // Coordinates are in the 16x16 inventory slot, matched to the 128px item textures.
        float centerX = wire ? 12.05f : network ? 7.4f : 8f;
        float centerY = wire ? 10.3f : network ? 6.5f : 6.7f;
        float labelWidth = wire ? 4.4f : 6.7f;
        float labelHeight = network ? 1.8f : 2.3f;
        int columns = wire ? 6 : 10;
        int lines = (id.length() + columns - 1) / columns, width = 1;
        for (int row = 0; row < lines; row++)
            width = Math.max(width, font.width(id.substring(row * columns, Math.min(id.length(), (row + 1) * columns))));
        // Fit the visible seven-pixel digits rather than the font's extra line spacing.
        float textHeight = (lines - 1) * font.lineHeight + 7f;
        float strokeOffset = .3f;
        float scale = Math.min(labelWidth / (width + strokeOffset), labelHeight / textHeight);
        var pose = graphics.pose();
        pose.pushPose();
        // Draw directly on the blank tag/plate, leaving the vanilla stack count untouched.
        pose.translate(x + centerX, y + centerY, 210);
        pose.mulPose(Axis.ZP.rotationDegrees(wire ? -7f : network ? 6f : 0f));
        pose.scale(scale, scale, 1);
        for (int row = 0; row < lines; row++) {
            String line = id.substring(row * columns, Math.min(id.length(), (row + 1) * columns));
            pose.pushPose();
            pose.translate(-(font.width(line) + strokeOffset) / 2f,
                    row * font.lineHeight - textHeight / 2f, 0);
            graphics.drawString(font, line, 0, 0, 0xff101010, false);
            // A fractional second pass adds a little weight without a heavy full-pixel bold stroke.
            pose.translate(strokeOffset, 0, 0);
            graphics.drawString(font, line, 0, 0, 0xff101010, false);
            pose.popPose();
        }
        pose.popPose();
        return false;
    }
}
