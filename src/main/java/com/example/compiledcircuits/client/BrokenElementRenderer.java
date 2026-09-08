package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, value = Dist.CLIENT)
public final class BrokenElementRenderer {
    private static final double MIN_OFFSET = 0.025D;
    private static final double MAX_OFFSET = 0.975D;
    private BrokenElementRenderer() {}

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBrokenElements.clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        var positions = ClientBrokenElements.getRenderPositions(minecraft.level.dimension().location().toString());
        if (positions.isEmpty()) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        try {
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f matrix = poseStack.last().pose();
            for (BlockPos pos : positions) {
                addBox(buffer, matrix, pos, 1.0F, 0.05F, 0.05F, 0.55F);
            }
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            poseStack.popPose();
        }
    }

    private static void addBox(
            BufferBuilder buffer,
            Matrix4f matrix,
            BlockPos pos,
            float red,
            float green,
            float blue,
            float alpha
    ) {

        float x1 =
                (float) (
                        pos.getX()
                                + MIN_OFFSET
                );

        float y1 =
                (float) (
                        pos.getY()
                                + MIN_OFFSET
                );

        float z1 =
                (float) (
                        pos.getZ()
                                + MIN_OFFSET
                );

        float x2 =
                (float) (
                        pos.getX()
                                + MAX_OFFSET
                );

        float y2 =
                (float) (
                        pos.getY()
                                + MAX_OFFSET
                );

        float z2 =
                (float) (
                        pos.getZ()
                                + MAX_OFFSET
                );

        int r =
                toColor(red);

        int g =
                toColor(green);

        int b =
                toColor(blue);

        int a =
                toColor(alpha);

        /*
         * -------------------------
         * BOTTOM
         * -------------------------
         */

        vertex(
                buffer,
                matrix,
                x1, y1, z1,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x2, y1, z1,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x2, y1, z2,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x1, y1, z2,
                r, g, b, a
        );

        /*
         * -------------------------
         * TOP
         * -------------------------
         */

        vertex(
                buffer,
                matrix,
                x1, y2, z2,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x2, y2, z2,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x2, y2, z1,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x1, y2, z1,
                r, g, b, a
        );

        /*
         * -------------------------
         * NORTH
         * -------------------------
         */

        vertex(
                buffer,
                matrix,
                x1, y1, z1,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x1, y2, z1,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x2, y2, z1,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x2, y1, z1,
                r, g, b, a
        );

        /*
         * -------------------------
         * SOUTH
         * -------------------------
         */

        vertex(
                buffer,
                matrix,
                x2, y1, z2,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x2, y2, z2,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x1, y2, z2,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x1, y1, z2,
                r, g, b, a
        );

        /*
         * -------------------------
         * WEST
         * -------------------------
         */

        vertex(
                buffer,
                matrix,
                x1, y1, z2,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x1, y2, z2,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x1, y2, z1,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x1, y1, z1,
                r, g, b, a
        );

        /*
         * -------------------------
         * EAST
         * -------------------------
         */

        vertex(
                buffer,
                matrix,
                x2, y1, z1,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x2, y2, z1,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x2, y2, z2,
                r, g, b, a
        );

        vertex(
                buffer,
                matrix,
                x2, y1, z2,
                r, g, b, a
        );
    }

    private static void vertex(
            BufferBuilder buffer,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            int red,
            int green,
            int blue,
            int alpha
    ) {

        buffer.vertex(
                        matrix,
                        x,
                        y,
                        z
                )
                .color(
                        red,
                        green,
                        blue,
                        alpha
                )
                .endVertex();
    }

    private static int toColor(
            float value
    ) {

        value =
                Math.max(
                        0.0F,
                        Math.min(
                                1.0F,
                                value
                        )
                );

        return Math.round(
                value * 255.0F
        );
    }
}