package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.ModBlockEntities;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Static plates and labels only; all signal evaluation stays on the server's compiled graph. */
@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BundlePortRenderer implements BlockEntityRenderer<BundleWireEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
    private final Font font;
    public BundlePortRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.BUNDLE_WIRE.get(), BundlePortRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BUNDLE_PORT.get(), Legacy::new);
    }
    @Override public int getViewDistance() { return 48; }
    @Override public void render(BundleWireEntity wire, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        for (var entry : wire.ports().entrySet()) draw(font, entry.getKey(), entry.getValue(), pose, buffers, light, true);
    }
    private static final class Legacy implements BlockEntityRenderer<BundlePortEntity> {
        private final Font font;
        Legacy(BlockEntityRendererProvider.Context context) { font = context.getFont(); }
        @Override public void render(BundlePortEntity entity, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (entity.channel().isEmpty() || !(entity.getBlockState().getBlock() instanceof BundlePortBlock block)) return;
            draw(font, entity.getBlockState().getValue(EndpointBlock.FACING),
                    new BundleWireEntity.Port((block.isExternal() ? 3 : 1) + (block.isInput() ? 0 : 1), entity.channel()), pose, buffers, light, false);
        }
    }
    private static void draw(Font font, Direction side, BundleWireEntity.Port port, PoseStack pose, MultiBufferSource buffers, int light, boolean plate) {
        pose.pushPose(); pose.translate(.5, .5, .5);
        pose.mulPose(side.getRotation()); pose.mulPose(Axis.XP.rotationDegrees(-90));
        if (plate) {
            var vertices = buffers.getBuffer(RenderType.entitySolid(TEXTURE));
            int signal = port.input() ? 0x8ed44e : 0x63b9ed;
            int border = port.external() ? 0xe6e6e6 : 0x252525;
            // A five-pixel-deep frame with a six-pixel through-hole for the cable.
            float outer = 5.5f / 16, hole = 3f / 16;
            float front = .499f, back = front - 5f / 16;
            box(vertices, pose, -outer, -outer, back, outer, -hole, front, signal, light);
            box(vertices, pose, -outer, hole, back, outer, outer, front, signal, light);
            box(vertices, pose, -outer, -hole, back, -hole, hole, front, signal, light);
            box(vertices, pose, hole, -hole, back, outer, hole, front, signal, light);
            // Mark the middle third of each narrow outer side, across its full depth.
            for (int edge = 0; edge < 4; edge++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(edge * 90));
                pose.translate(0, outer + .0002f, (front + back) / 2);
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                quad(vertices, pose, -outer / 3, -(front - back) / 2,
                        outer / 3, (front - back) / 2, 0,
                        port.external() ? 0xffffff : 0x101010, light);
                pose.popPose();
            }
            // Both ends carry the same direction and inter-network markings.
            for (boolean rear : new boolean[]{false, true}) {
                vertices = buffers.getBuffer(RenderType.entitySolid(TEXTURE));
                pose.pushPose();
                if (rear) {
                    pose.translate(0, 0, front + back);
                    pose.mulPose(Axis.YP.rotationDegrees(180));
                }
                ring(vertices, pose, outer, hole, front + .0002f, border, light);
                ring(vertices, pose, outer - 1f / 32, hole + 1f / 32, front + .0004f, signal, light);
                quad(vertices, pose, -.30f, .195f, .30f, .33f, front + .0006f, 0xffffff, light);
                label(font, port, pose, buffers, light, front + .001f);
                pose.popPose();
            }
        } else {
            label(font, port, pose, buffers, light, .502f);
        }
        pose.popPose();
    }
    private static void label(Font font, BundleWireEntity.Port port, PoseStack pose, MultiBufferSource buffers, int light, float z) {
        pose.pushPose();
        var text = port.id(); int columns = 12, lines = (text.length() + columns - 1) / columns;
        float width = 1;
        for (int i = 0; i < lines; i++) width = Math.max(width, font.width(text.substring(i * columns, Math.min(text.length(), (i + 1) * columns))));
        float scale = Math.min(.58f / width, .12f / (lines * font.lineHeight));
        pose.translate(0, .32, z); pose.scale(scale, -scale, scale);
        for (int i = 0; i < lines; i++) {
            String line = text.substring(i * columns, Math.min(text.length(), (i + 1) * columns));
            font.drawInBatch(line, -font.width(line) / 2f, i * font.lineHeight, 0xff101010, false,
                    pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        }
        pose.popPose();
    }
    private static void ring(VertexConsumer out, PoseStack pose, float outer, float hole, float z, int color, int light) {
        quad(out, pose, -outer, -outer, outer, -hole, z, color, light);
        quad(out, pose, -outer, hole, outer, outer, z, color, light);
        quad(out, pose, -outer, -hole, -hole, hole, z, color, light);
        quad(out, pose, hole, -hole, outer, hole, z, color, light);
    }
    private static void box(VertexConsumer out, PoseStack pose, float x0, float y0, float z0,
                            float x1, float y1, float z1, int color, int light) {
        pose.pushPose();
        pose.translate((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
        pose.scale(x1 - x0, y1 - y0, z1 - z0);
        for (var face : Direction.values()) {
            pose.pushPose();
            pose.mulPose(face.getRotation());
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            quad(out, pose, -.5f, -.5f, .5f, .5f, .5f, color, light);
            pose.popPose();
        }
        pose.popPose();
    }
    private static void quad(VertexConsumer out, PoseStack pose, float x0, float y0, float x1, float y1, float z, int color, int light) {
        vertex(out, pose, x0,y0,z,0,1,color,light); vertex(out, pose, x1,y0,z,1,1,color,light);
        vertex(out, pose, x1,y1,z,1,0,color,light); vertex(out, pose, x0,y1,z,0,0,color,light);
    }
    private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float z, float u, float v, int color, int light) {
        out.vertex(pose.last().pose(),x,y,z).color(color >> 16 & 255,color >> 8 & 255,color & 255,255)
                .uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(),0,0,1).endVertex();
    }
}
