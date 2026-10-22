package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.item.IdWireItem;
import com.example.compiledcircuits.network.WireIds;
import com.example.compiledcircuits.registry.ModItems;
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
        int columns = 8, lines = (id.length() + columns - 1) / columns, width = 1;
        for (int row = 0; row < lines; row++)
            width = Math.max(width, font.width(id.substring(row * columns, Math.min(id.length(), (row + 1) * columns))));
        float scale = Math.min(.75f, Math.min(14f / width, 8f / (lines * font.lineHeight)));
        var pose = graphics.pose();
        pose.pushPose();
        // Above the icon, in the top of the slot so the stack count remains visible.
        pose.translate(x + 8, y + 1, 210);
        int halfWidth = (int)Math.ceil(width * scale / 2) + 1;
        graphics.fill(-halfWidth, 0, halfWidth, (int)Math.ceil(lines * font.lineHeight * scale) + 1, 0xeeffffff);
        pose.translate(0, 1, 1);
        pose.scale(scale, scale, 1);
        for (int row = 0; row < lines; row++) {
            String line = id.substring(row * columns, Math.min(id.length(), (row + 1) * columns));
            graphics.drawString(font, line, -font.width(line) / 2, row * font.lineHeight, 0xff101010, false);
        }
        pose.popPose();
        return false;
    }
}
