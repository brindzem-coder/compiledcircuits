package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = CompiledCircuits.MOD_ID,
        value = Dist.CLIENT
)
public class NetworkSelectionRenderer {

    /*
     * Wire:
     * cyan / blue
     */
    private static final float WIRE_R = 0.15F;
    private static final float WIRE_G = 0.75F;
    private static final float WIRE_B = 1.00F;

    /*
     * Input:
     * bright green
     */
    private static final float INPUT_R = 0.20F;
    private static final float INPUT_G = 1.00F;
    private static final float INPUT_B = 0.25F;

    /*
     * Output:
     * orange
     */
    private static final float OUTPUT_R = 1.00F;
    private static final float OUTPUT_G = 0.45F;
    private static final float OUTPUT_B = 0.10F;

    private static final float ALPHA = 0.22F;

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

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        MultiBufferSource.BufferSource bufferSource =
                minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();

        /*
         * BlockPos/AABB мають world coordinates,
         * тому переносимо систему координат відносно camera.
         */
        poseStack.translate(
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z
        );

        for (BlockPos pos : ClientNetworkSelection.getWires()) {

            renderBox(
                    poseStack,
                    bufferSource,
                    pos,
                    WIRE_R,
                    WIRE_G,
                    WIRE_B,
                    ALPHA
            );
        }

        for (BlockPos pos : ClientNetworkSelection.getInputs()) {

            renderBox(
                    poseStack,
                    bufferSource,
                    pos,
                    INPUT_R,
                    INPUT_G,
                    INPUT_B,
                    0.30F
            );
        }

        for (BlockPos pos : ClientNetworkSelection.getOutputs()) {

            renderBox(
                    poseStack,
                    bufferSource,
                    pos,
                    OUTPUT_R,
                    OUTPUT_G,
                    OUTPUT_B,
                    0.30F
            );
        }

        poseStack.popPose();

        bufferSource.endBatch();
    }

    private static void renderBox(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            BlockPos pos,
            float red,
            float green,
            float blue,
            float alpha
    ) {

        /*
         * Трохи менше за повний блок,
         * щоб сусідні highlights не зливались
         * в одну суцільну поверхню.
         */
        AABB box = new AABB(
                pos.getX() + 0.03D,
                pos.getY() + 0.03D,
                pos.getZ() + 0.03D,

                pos.getX() + 0.97D,
                pos.getY() + 0.97D,
                pos.getZ() + 0.97D
        );

        DebugRenderer.renderFilledBox(
                poseStack,
                bufferSource,
                box,
                red,
                green,
                blue,
                alpha
        );
    }
}