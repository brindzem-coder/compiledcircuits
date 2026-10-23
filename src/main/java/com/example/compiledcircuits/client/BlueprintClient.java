package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.networking.*;
import com.example.compiledcircuits.registry.ModItems;
import com.example.compiledcircuits.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@Mod.EventBusSubscriber(modid=CompiledCircuits.MOD_ID,value=Dist.CLIENT)
public final class BlueprintClient {
    private static UUID requested,token;
    private static final ListTag pending=new ListTag();
    private static CircuitBlueprint blueprint;
    private static List<CompiledCircuitElement> transformed=List.of();
    private record Ghost(CompiledCircuitElement element,List<AABB> boxes) {}
    private static List<Ghost> ghosts=List.of();
    private static BlockPos locked;
    private static int turns;
    private static boolean mirrored,mirroredZ;
    private static boolean cancelled;
    private static Object connection,dimension;
    private static long lastConfirm;
    private static int retryTicks;
    private static ItemStack held(){var p=Minecraft.getInstance().player;if(p==null)return ItemStack.EMPTY;return p.getMainHandItem().is(ModItems.CIRCUIT_COPIER.get())?p.getMainHandItem():p.getOffhandItem().is(ModItems.CIRCUIT_COPIER.get())?p.getOffhandItem():ItemStack.EMPTY;}
    private static boolean active(){return blueprint!=null&&!held().isEmpty()&&Objects.equals(token,BlueprintService.id(held()));}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(connection!=mc.getConnection()||dimension!=mc.level){connection=mc.getConnection();dimension=mc.level;requested=null;token=null;blueprint=null;pending.clear();locked=null;}
        UUID id=BlueprintService.id(held());
        if(id==null){requested=null;return;}
        if(!id.equals(requested)||(!active()&&++retryTicks>=40)){requested=id;retryTicks=0;ModNetworking.CHANNEL.sendToServer(new BlueprintRequestC2SPacket(0));}
    }
    public static void accept(BlueprintPreviewS2CPacket packet){
        if(packet.total()<1||packet.total()>CircuitBlueprint.MAX_ELEMENTS||packet.payload()==null)return;
        if(packet.offset()==0){pending.clear();token=packet.id();blueprint=null;}
        if(!packet.id().equals(token)||packet.offset()!=pending.size())return;
        var part=packet.payload().getList("elements",Tag.TAG_COMPOUND);if(part.size()>128||pending.size()+part.size()>packet.total())return;
        part.forEach(t->pending.add(t.copy()));
        if(pending.size()==packet.total()){
            var data=new CompoundTag();data.put("elements",pending.copy());data.putLong("origin",BlockPos.ZERO.asLong());data.putString("name",packet.name());
            blueprint=new CircuitBlueprint(data);requested=token;turns=0;mirrored=false;mirroredZ=false;locked=null;cancelled=false;rebuild();
        }
    }
    private static void rebuild(){
        transformed=blueprint.elements(BlockPos.ZERO,turns,mirrored,mirroredZ);var cached=new ArrayList<Ghost>();
        for(var e:transformed){var state=e.resolveState().state().orElseThrow();
            var boxes=new ArrayList<>(state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO).toAabbs());
            BundleWireEntity.decode(e.bundleFaces()).forEach((side,port)->{
                double[] low={.15625,.15625,.15625},high={.84375,.84375,.84375};int axis=side.getAxis()==Direction.Axis.X?0:side.getAxis()==Direction.Axis.Y?1:2;
                low[axis]=side.getAxisDirection()==Direction.AxisDirection.POSITIVE?.6875:0;high[axis]=side.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:.3125;
                boxes.add(new AABB(low[0],low[1],low[2],high[0],high[1],high[2]));
            });
            cached.add(new Ghost(e,boxes.stream().map(box->box.move(e.getPos())).toList()));
        }
        ghosts=List.copyOf(cached);
    }
    public static BlockPos anchor(){
        if(locked!=null)return locked;var mc=Minecraft.getInstance();
        if(mc.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK)return mc.level.getBlockState(hit.getBlockPos()).isAir()?hit.getBlockPos():hit.getBlockPos().relative(hit.getDirection());
        return BlockPos.containing(mc.player.getEyePosition().add(mc.player.getLookAngle().scale(6)));
    }
    public static void capture(BlockPos pos){
        ModNetworking.CHANNEL.sendToServer(new BlueprintRequestC2SPacket(0,net.minecraft.client.gui.screens.Screen.hasControlDown(),pos));
    }
    public static void confirm(){
        if(!active()||cancelled)return;long now=System.currentTimeMillis();if(now-lastConfirm<300)return;lastConfirm=now;
        ModNetworking.CHANNEL.sendToServer(new BlueprintPlaceC2SPacket(token,anchor(),turns,mirrored,mirroredZ));
    }
    @SubscribeEvent public static void key(InputEvent.Key event){
        var mc=Minecraft.getInstance();if(!active()||event.getAction()!=GLFW.GLFW_PRESS)return;
        int key=event.getKey();
        if(key==GLFW.GLFW_KEY_ESCAPE&&(mc.screen==null||mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)){cancelled=true;locked=null;return;}
        if(mc.screen!=null)return;
        if(key==GLFW.GLFW_KEY_F){mc.options.keySwapOffhand.setDown(false);while(mc.options.keySwapOffhand.consumeClick()){}cancelled=false;locked=locked==null?anchor():null;return;}
        if(key==GLFW.GLFW_KEY_Y){if(net.minecraft.client.gui.screens.Screen.hasControlDown())mirroredZ=!mirroredZ;else mirrored=!mirrored;rebuild();return;}
        if(key==GLFW.GLFW_KEY_R){turns=(turns+1)%4;rebuild();return;}
        Direction move=switch(key){case GLFW.GLFW_KEY_LEFT->Direction.WEST;case GLFW.GLFW_KEY_RIGHT->Direction.EAST;case GLFW.GLFW_KEY_UP->Direction.NORTH;case GLFW.GLFW_KEY_DOWN->Direction.SOUTH;case GLFW.GLFW_KEY_PAGE_UP->Direction.UP;case GLFW.GLFW_KEY_PAGE_DOWN->Direction.DOWN;default->null;};
        if(move!=null){locked=anchor().relative(move);cancelled=false;}
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(!active()||mc.screen!=null||mc.options.hideGui)return;
        var g=event.getGuiGraphics();int y=mc.getWindow().getGuiScaledHeight()-65;
        var controls=Component.translatable(cancelled?"blueprint.compiledcircuits.cancelled":"blueprint.compiledcircuits.controls");
        float scale=Math.min(1,(mc.getWindow().getGuiScaledWidth()-12f)/Math.max(1,mc.font.width(controls)));
        g.pose().pushPose();g.pose().translate(mc.getWindow().getGuiScaledWidth()/2f,y,0);g.pose().scale(scale,scale,1);
        g.drawCenteredString(mc.font,controls,0,0,0xffffff);g.pose().popPose();
        if(!cancelled)g.drawCenteredString(mc.font,blueprint.name()+" | "+anchor().toShortString()+" | "+turns*90+"°"+(mirrored?" | X":"")+(mirroredZ?" | Z":""),mc.getWindow().getGuiScaledWidth()/2,y-11,0xffffff);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!active()||cancelled||mc.screen!=null)return;
        var anchor=anchor();var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        pose.pushPose();pose.translate(anchor.getX()-camera.x,anchor.getY()-camera.y,anchor.getZ()-camera.z);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(var ghost:ghosts){
            var e=ghost.element();var pos=e.getPos();var world=pos.offset(anchor);
            boolean blocked=(ClientCompiledElements.isReadyFor(mc.level.dimension().location().toString())&&(ClientCompiledElements.isBlocked(world)||ClientCompiledElements.networkIdAt(world)!=null))||!mc.level.hasChunkAt(world)||!mc.level.getBlockState(world).isAir()||mc.level.isOutsideBuildHeight(world)||!mc.level.getWorldBorder().isWithinBounds(world);
            int color=blocked?0xff2020:switch(e.getType()){case INPUT->0x66ff66;case OUTPUT->0xffcc66;case GATE,GATE_INPUT,GATE_OUTPUT->0xaa88ff;default->e.wireId().isEmpty()?0x55ccff:WireClient.color(e.wireId(),e.wireColor());};
            for(var box:ghost.boxes())box(buffer,pose.last().pose(),box,color);
        }
        BufferUploader.drawWithShader(buffer.end());RenderSystem.enableCull();RenderSystem.depthMask(true);RenderSystem.disableBlend();pose.popPose();
    }
    private static void box(BufferBuilder b,org.joml.Matrix4f m,AABB a,int rgb){
        float[][] p={{(float)a.minX,(float)a.minY,(float)a.minZ},{(float)a.maxX,(float)a.minY,(float)a.minZ},{(float)a.maxX,(float)a.maxY,(float)a.minZ},{(float)a.minX,(float)a.maxY,(float)a.minZ},{(float)a.minX,(float)a.minY,(float)a.maxZ},{(float)a.maxX,(float)a.minY,(float)a.maxZ},{(float)a.maxX,(float)a.maxY,(float)a.maxZ},{(float)a.minX,(float)a.maxY,(float)a.maxZ}};
        for(int[] face:new int[][]{{0,1,2,3},{4,7,6,5},{0,4,5,1},{3,2,6,7},{0,3,7,4},{1,5,6,2}})for(int i:face)b.vertex(m,p[i][0],p[i][1],p[i][2]).color(rgb>>16&255,rgb>>8&255,rgb&255,110).endVertex();
    }
}
