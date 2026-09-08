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

@Mod.EventBusSubscriber(
        modid = CompiledCircuits.MOD_ID,
        value = Dist.CLIENT
)
public class NetworkSelectionRenderer {

    /*
     * Wire — cyan / blue
     */
    private static final float WIRE_R = 0.15F;
    private static final float WIRE_G = 0.75F;
    private static final float WIRE_B = 1.00F;

    /*
     * Input — bright green
     */
    private static final float INPUT_R = 0.20F;
    private static final float INPUT_G = 1.00F;
    private static final float INPUT_B = 0.25F;

    /*
     * Output — orange
     */
    private static final float OUTPUT_R = 1.00F;
    private static final float OUTPUT_G = 0.45F;
    private static final float OUTPUT_B = 0.10F;

    /*
     * Загальна прозорість.
     *
     * Можеш потім спробувати:
     * 0.35F — слабше
     * 0.45F — нормальна яскравість
     * 0.60F — дуже яскраво
     */
    private static final float ALPHA = 0.45F;

    private static final double MIN_OFFSET = 0.03D;
    private static final double MAX_OFFSET = 0.97D;

    @SubscribeEvent
    public static void onRenderLevelStage(
            RenderLevelStageEvent event
    ) {

        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        if (ClientNetworkSelection.getTotalSize() == 0) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        PoseStack poseStack =
                event.getPoseStack();

        Camera camera =
                minecraft.gameRenderer
                        .getMainCamera();

        Vec3 cameraPos =
                camera.getPosition();

        poseStack.pushPose();

        /*
         * Наші BlockPos — world coordinates.
         * Переводимо їх відносно camera.
         */
        poseStack.translate(
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z
        );

        /*
         * -------------------------------------------------
         * X-RAY RENDER STATE
         * -------------------------------------------------
         */

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        /*
         * Головне:
         * geometry НЕ перевіряється по depth buffer.
         *
         * Тому highlight видно крізь stone,
         * walls та інші blocks.
         */
        RenderSystem.disableDepthTest();

        /*
         * Highlight також не записує свою depth
         * у depth buffer.
         */
        RenderSystem.depthMask(false);

        /*
         * Малюємо обидві сторони faces.
         */
        RenderSystem.disableCull();

        RenderSystem.setShader(
                GameRenderer::getPositionColorShader
        );

        /*
         * Один BufferBuilder для всього network.
         *
         * Це значно краще, ніж робити окремий
         * draw call для кожного wire.
         */
        Tesselator tesselator =
                Tesselator.getInstance();

        BufferBuilder buffer =
                tesselator.getBuilder();

        buffer.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
        );

        Matrix4f matrix =
                poseStack.last().pose();

        /*
         * WIRES
         */
        for (BlockPos pos
                : ClientNetworkSelection.getWires()) {

            addBox(
                    buffer,
                    matrix,
                    pos,
                    WIRE_R,
                    WIRE_G,
                    WIRE_B,
                    ALPHA
            );
        }

        /*
         * INPUTS
         */
        for (BlockPos pos
                : ClientNetworkSelection.getInputs()) {

            addBox(
                    buffer,
                    matrix,
                    pos,
                    INPUT_R,
                    INPUT_G,
                    INPUT_B,
                    ALPHA
            );
        }

        /*
         * OUTPUTS
         */
        for (BlockPos pos
                : ClientNetworkSelection.getOutputs()) {

            addBox(
                    buffer,
                    matrix,
                    pos,
                    OUTPUT_R,
                    OUTPUT_G,
                    OUTPUT_B,
                    ALPHA
            );
        }

        /*
         * Реальний draw відбувається ТУТ,
         * поки depth test все ще disabled.
         */
        BufferUploader.drawWithShader(
                buffer.end()
        );

        /*
         * -------------------------------------------------
         * RESTORE MINECRAFT RENDER STATE
         * -------------------------------------------------
         */

        RenderSystem.enableCull();

        RenderSystem.depthMask(true);

        RenderSystem.enableDepthTest();

        RenderSystem.disableBlend();

        poseStack.popPose();
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