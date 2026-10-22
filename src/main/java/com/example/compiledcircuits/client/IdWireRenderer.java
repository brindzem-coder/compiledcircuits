package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.IdWireEntity;
import com.example.compiledcircuits.block.NetworkWireBlock;
import com.example.compiledcircuits.registry.ModBlockEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class IdWireRenderer implements BlockEntityRenderer<IdWireEntity> {
    private final Font font;
    public IdWireRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ID_WIRE.get(), IdWireRenderer::new);
    }
    @Override public int getViewDistance() { return 32; }
    @Override public void render(IdWireEntity wire, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        String id = wire.wireId();
        if (id.isEmpty()) return;
        int color = contrastingText(WireClient.color(id, wire.color()));
        int columns = 8, lines = (id.length() + columns - 1) / columns;
        int width = 1;
        for (int row = 0; row < lines; row++)
            width = Math.max(width, font.width(id.substring(row * columns, Math.min(id.length(), (row + 1) * columns))));
        float scale = Math.min(.27f / width, .27f / (lines * font.lineHeight));
        for (var side : Direction.values()) {
            int face = NetworkWireBlock.face(wire.getBlockState(), side);
            // Port markings keep their own surface; ordinary arms extend to the cell boundary.
            if (face >= 2) continue;
            pose.pushPose();
            pose.translate(.5, .5, .5);
            pose.mulPose(side.getRotation());
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.translate(0, 0, face == 0 ? 2.5 / 16 + .0005 : .5005);
            pose.scale(scale, -scale, scale);
            for (int row = 0; row < lines; row++) {
                String line = id.substring(row * columns, Math.min(id.length(), (row + 1) * columns));
                font.drawInBatch(line, -font.width(line) / 2f, row * font.lineHeight - lines * font.lineHeight / 2f,
                        color, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
            }
            pose.popPose();
        }
    }
    // Pick the larger black/white contrast ratio using linear sRGB luminance.
    static int contrastingText(int rgb) {
        double luminance = .2126 * linear(rgb >> 16 & 255) + .7152 * linear(rgb >> 8 & 255) + .0722 * linear(rgb & 255);
        return luminance > .1791287847 ? 0xff000000 : 0xffffffff;
    }
    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
