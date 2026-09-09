package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.CircuitElementPredicates;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.OptionalDouble;

/** Physical-index traversal only: no discovery, connectivity search or world mutation. */
@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, value = Dist.CLIENT)
public final class CircuitStateOutlineRenderer {
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(256 * 1024));
    private static int outlinedPositions, drawCalls;

    private CircuitStateOutlineRenderer() {}
    public static int getOutlinedPositions() { return outlinedPositions; }
    public static int getDrawCalls() { return drawCalls; }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        outlinedPositions = 0;
        drawCalls = 0;
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || !ClientCompiledElements.isReadyFor(level.dimension().location().toString())) return;
        var exclusions = CircuitVisualPriority.capture(level.dimension().location().toString());
        var camera = event.getCamera().getPosition();
        double distance = minecraft.options.getEffectiveRenderDistance() * 16.0;
        double distanceSquared = distance * distance;
        var poses = event.getPoseStack();
        var stale = new ArrayList<BlockPos>();
        poses.pushPose();
        try {
            poses.translate(-camera.x, -camera.y, -camera.z);
            // This buffer belongs exclusively to state outlines. Both colors use one RenderType.
            var lines = BUFFERS.getBuffer(OutlineType.LINES);
            for (var chunkEntry : ClientCircuitBlockIndex.getBuckets().entrySet()) {
                int chunkX = ChunkPos.getX(chunkEntry.getKey());
                int chunkZ = ChunkPos.getZ(chunkEntry.getKey());
                double x = chunkX * 16.0, z = chunkZ * 16.0;
                double dx = axisDistance(camera.x, x, x + 16), dz = axisDistance(camera.z, z, z + 16);
                double horizontalDistanceSquared = dx * dx + dz * dz;
                if (horizontalDistanceSquared > distanceSquared) continue;
                var chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (var section : chunkEntry.getValue().entrySet()) {
                    if (section.getValue().isEmpty()) continue;
                    double y = section.getKey() * 16.0;
                    double dy = axisDistance(camera.y, y, y + 16);
                    if (horizontalDistanceSquared + dy * dy > distanceSquared
                            || !event.getFrustum().isVisible(new AABB(x, y, z, x + 16, y + 16, z + 16))) continue;
                    for (BlockPos pos : section.getValue()) {
                        if (pos.distToCenterSqr(camera.x, camera.y, camera.z) > distanceSquared
                                || !event.getFrustum().isVisible(new AABB(pos))) continue;
                        if (!CircuitElementPredicates.isCircuit(chunk.getBlockState(pos))) {
                            stale.add(pos);
                            continue;
                        }
                        if (exclusions.suppressesOutline(pos)) continue;
                        boolean hovered = ClientHoveredCircuit.contains(pos);
                        if (!hovered && ClientCompiledElements.networkIdAt(pos) != null) continue;
                        LevelRenderer.renderLineBox(poses, lines,
                                pos.getX() + 0.002, pos.getY() + 0.002, pos.getZ() + 0.002,
                                pos.getX() + 0.998, pos.getY() + 0.998, pos.getZ() + 0.998,
                                hovered ? 0.15F : 1.00F, hovered ? 0.85F : 0.30F,
                                hovered ? 1.00F : 0.72F, hovered ? 0.85F : 0.70F);
                        outlinedPositions++;
                    }
                }
            }
        } finally {
            try {
                BUFFERS.endBatch(OutlineType.LINES);
                if (outlinedPositions > 0) drawCalls = 1;
            } finally {
                poses.popPose();
                ClientCircuitBlockIndex.removeStale(level, stale);
            }
        }
    }

    private static double axisDistance(double point, double min, double max) {
        return Math.max(0, Math.max(min - point, point - max));
    }

    private static final class OutlineType extends RenderType {
        // Keep the normal world-stage depth baseline after drawing, including after X-Ray subscribers.
        private static final DepthTestStateShard WORLD_DEPTH = new DepthTestStateShard("lequal", 515) {
            @Override public void setupRenderState() { RenderSystem.enableDepthTest(); RenderSystem.depthFunc(515); }
            @Override public void clearRenderState() { RenderSystem.enableDepthTest(); RenderSystem.depthFunc(515); }
        };
        private static final RenderType LINES = create("compiledcircuits_state_outlines",
                DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256 * 1024,
                false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(1.5)))
                        .setDepthTestState(WORLD_DEPTH)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(MAIN_TARGET)
                        .createCompositeState(false));

        private OutlineType() {
            super("unused", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                    256, false, false, () -> {}, () -> {});
        }
    }
}
