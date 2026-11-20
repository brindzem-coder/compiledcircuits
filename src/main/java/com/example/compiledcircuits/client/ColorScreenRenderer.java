package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.network.ScreenColors;
import com.example.compiledcircuits.registry.ModBlockEntities;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=CompiledCircuits.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ColorScreenRenderer implements BlockEntityRenderer<ColorScreenEntity> {
    private static final ResourceLocation WHITE=new ResourceLocation("compiledcircuits","textures/block/screen_white.png");
    public ColorScreenRenderer(BlockEntityRendererProvider.Context context){}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(ModBlockEntities.COLOR_SCREEN.get(),ColorScreenRenderer::new);}
    @Override public void render(ColorScreenEntity screen,float tick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        int rgb=ScreenColors.rgb(screen.color());pose.pushPose();pose.translate(.5,.5,.5);
        pose.mulPose(screen.getBlockState().getValue(EndpointBlock.FACING).getRotation());pose.mulPose(Axis.XP.rotationDegrees(-90));
        // Full-bright lightmap plus no directional diffuse shading keeps identical pixels identical.
        var out=buffers.getBuffer(net.minecraftforge.client.ForgeRenderTypes.getUnlitTranslucent(WHITE));
        vertex(out,pose,-.5f,-.5f,0,1,rgb);vertex(out,pose,.5f,-.5f,1,1,rgb);vertex(out,pose,.5f,.5f,1,0,rgb);vertex(out,pose,-.5f,.5f,0,0,rgb);pose.popPose();
    }
    private static void vertex(VertexConsumer out,PoseStack pose,float x,float y,float u,float v,int rgb){out.vertex(pose.last().pose(),x,y,.5005f).color(rgb>>16&255,rgb>>8&255,rgb&255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.last().normal(),0,0,1).endVertex();}
}
