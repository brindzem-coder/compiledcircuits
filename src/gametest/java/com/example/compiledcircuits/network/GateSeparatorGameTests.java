package com.example.compiledcircuits.network;
import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class GateSeparatorGameTests {
    @GameTest(template="empty",batch="gate_separator",timeoutTicks=100)
    public static void touchingGatesRemainIndependent(GameTestHelper h) {
        var base=ModBlocks.AND_GATE.get().defaultBlockState();
        var left=GateBodyBlock.withPort(GateBodyBlock.withPort(base,Direction.WEST,1),Direction.NORTH,2);
        var right=GateBodyBlock.withPort(GateBodyBlock.withPort(base,Direction.EAST,1),Direction.SOUTH,2);
        Map<BlockPos,BlockState> s=new HashMap<>();
        s.put(BlockPos.ZERO,left);s.put(new BlockPos(1,0,0),right);
        s.put(new BlockPos(-1,0,0),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        s.put(new BlockPos(-2,0,0),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        s.put(new BlockPos(2,0,0),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        s.put(new BlockPos(3,0,0),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        s.put(new BlockPos(0,0,-1),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        s.put(new BlockPos(0,0,-2),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        s.put(new BlockPos(1,0,1),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        s.put(new BlockPos(1,0,2),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        for(int mode=0;mode<3;mode++) {
            s.put(BlockPos.ZERO,mode==1?GateBodyBlock.withPort(left,Direction.EAST,3):left);
            s.put(new BlockPos(1,0,0),mode==2?GateBodyBlock.withPort(right,Direction.WEST,3):right);
            var result=LogicProgram.compile(s).evaluate(Map.of(new BlockPos(-2,0,0),true,new BlockPos(3,0,0),false));
            h.assertTrue(result.get(new BlockPos(0,0,-2))==(mode!=0),"either side can separate two AND bodies");
            h.assertTrue(!result.get(new BlockPos(1,0,2)),"second gate remains LOW");
        }
        for(Direction side:Direction.values()) {
            var sealed=GateBodyBlock.withPort(base,side,3);
            h.assertTrue(!CircuitConnections.connects(sealed,base,side)&&!CircuitConnections.connects(base,sealed,side.getOpposite()),"symmetric boundary");
            h.assertTrue(!((GateBodyBlock)sealed.getBlock()).canWireConnect(sealed,side),"separator is not a port");
            for(Rotation r:Rotation.values())h.assertTrue(GateBodyBlock.port(sealed.rotate(r),r.rotate(side))==3,"rotation preserves boundary");
            for(Mirror m:Mirror.values())h.assertTrue(GateBodyBlock.port(sealed.mirror(m),m.mirror(side))==3,"mirror preserves boundary");
            var e=new CompiledCircuitElement(1,BlockPos.ZERO,CircuitElementType.GATE,CompiledBlockStateCodec.capture(sealed));
            h.assertTrue(e.resolveState().state().orElseThrow()==sealed,"separator saved for repair");
            h.assertTrue(CompiledBlockStateMatcher.match(e,base,false)==CompiledBlockStateMatcher.Match.STATE_MISMATCH,"separator changes invalidate compiled topology");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="gate_separator_world",timeoutTicks=100)
    public static void toolAndPlacement(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,2));
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;var previous=player.getItemInHand(hand);
        var stack=new net.minecraft.world.item.ItemStack(ModItems.GATE_SEPARATOR.get());player.setItemInHand(hand,stack);
        Map<BlockPos,BlockState> saved=new HashMap<>();saved.put(pos,level.getBlockState(pos));
        for(Direction side:Direction.values())saved.put(pos.relative(side),level.getBlockState(pos.relative(side)));
        try {
            for(Direction side:Direction.values()) {
                level.setBlock(pos.relative(side),Blocks.AIR.defaultBlockState(),3);
                level.setBlock(pos,GateBodyBlock.withPort(ModBlocks.AND_GATE.get().defaultBlockState(),side,1),3);
                var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),side,pos,false);
                var context=new net.minecraft.world.item.context.UseOnContext(player,hand,hit);
                stack.getItem().useOn(context);
                h.assertTrue(GateBodyBlock.port(level.getBlockState(pos),side)==3,"tool replaces port with boundary");
                level.setBlock(pos.relative(side),ModBlocks.AND_GATE.get().defaultBlockState(),3);
                h.assertTrue(GateBodyBlock.port(level.getBlockState(pos),side)==3,"placing adjacent body preserves boundary");
                h.assertTrue(!CircuitConnections.connects(level.getBlockState(pos),level.getBlockState(pos.relative(side)),side),"placed bodies isolated");
                level.setBlock(pos.relative(side),Blocks.AIR.defaultBlockState(),3);
                stack.getItem().useOn(context);
                h.assertTrue(GateBodyBlock.port(level.getBlockState(pos),side)==0,"second click clears boundary");
                h.assertTrue(stack.getCount()==1,"tool reusable");
            }
        } finally {saved.forEach((p,s)->level.setBlock(p,s,3));player.setItemInHand(hand,previous);}
        h.succeed();
    }
}
