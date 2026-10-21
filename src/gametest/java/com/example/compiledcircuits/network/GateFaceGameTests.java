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
public class GateFaceGameTests {
    @GameTest(template="empty",batch="gate_faces",timeoutTicks=100)
    public static void independentFacesAndTruthTables(GameTestHelper h) {
        for(Block block:new Block[]{ModBlocks.AND_GATE.get(),ModBlocks.OR_GATE.get(),ModBlocks.NAND_GATE.get(),ModBlocks.NOR_GATE.get(),ModBlocks.XOR_GATE.get(),ModBlocks.XNOR_GATE.get()}) {
            var state=GateBodyBlock.withPort(GateBodyBlock.withPort(GateBodyBlock.withPort(block.defaultBlockState(),Direction.WEST,1),Direction.NORTH,1),Direction.EAST,2);
            Map<BlockPos,BlockState> s=new HashMap<>(); s.put(BlockPos.ZERO,state);
            for(Direction side:new Direction[]{Direction.WEST,Direction.NORTH,Direction.EAST}) {
                s.put(BlockPos.ZERO.relative(side),ModBlocks.BASIC_WIRE.get().defaultBlockState());
                s.put(BlockPos.ZERO.relative(side,2),(side==Direction.EAST?ModBlocks.OUTPUT_ENDPOINT:ModBlocks.INPUT_ENDPOINT).get().defaultBlockState());
            }
            s.put(new BlockPos(-1,-1,0),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
            var program=LogicProgram.compile(s);
            for(boolean a:new boolean[]{false,true}) for(boolean b:new boolean[]{false,true}) {
                boolean expected=switch(((GateBodyBlock)block).kind()) {case AND->a&&b;case OR->a||b;case NAND->!(a&&b);case NOR->!(a||b);case XOR->a^b;case XNOR->a==b;};
                var result=program.evaluate(Map.of(new BlockPos(-2,0,0),a,new BlockPos(0,0,-2),b));
                h.assertTrue(result.get(new BlockPos(2,0,0))==expected,"face-port truth table");
                h.assertTrue(result.get(new BlockPos(-1,-1,0))==a,"no backfeed into input bus");
            }
            h.assertTrue(!CircuitConnections.connects(state,ModBlocks.NOT_GATE.get().defaultBlockState(),Direction.EAST),"face ports require wire");
            var element=new CompiledCircuitElement(1,BlockPos.ZERO,CircuitElementType.GATE,CompiledBlockStateCodec.capture(state));
            h.assertTrue(CompiledBlockStateMatcher.match(element,block.defaultBlockState(),false)==CompiledBlockStateMatcher.Match.STATE_MISMATCH,"ports are functional state");
            h.assertTrue(element.resolveState().state().orElseThrow()==state,"all faces saved for repair");
            var tag=CompiledBlockStateCodec.write(new CompiledBlockStateSnapshot(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString(),Map.of()));
            h.assertTrue(CompiledBlockStateCodec.readAndResolve(tag,element.getBlockId()).state().orElseThrow()==block.defaultBlockState(),"old portless snapshot migrates");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="gate_faces_world",timeoutTicks=100)
    public static void toolAndCoveringBody(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,2));
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;var oldHand=player.getItemInHand(hand);
        Map<BlockPos,BlockState> saved=new HashMap<>();saved.put(pos,level.getBlockState(pos));
        for(Direction side:Direction.values())saved.put(pos.relative(side),level.getBlockState(pos.relative(side)));
        try {
            for(Direction side:Direction.values()) {
                level.setBlock(pos.relative(side),Blocks.AIR.defaultBlockState(),3);
                level.setBlock(pos,ModBlocks.AND_GATE.get().defaultBlockState(),3);
                var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),side,pos,false);
                for(var item:new net.minecraft.world.item.Item[]{ModItems.GATE_INPUT.get(),ModItems.GATE_OUTPUT.get(),ModItems.GATE_OUTPUT.get(),ModItems.GATE_INPUT.get()}) {
                    var stack=new net.minecraft.world.item.ItemStack(item);player.setItemInHand(hand,stack);
                    int before=GateBodyBlock.port(level.getBlockState(pos),side),requested=item==ModItems.GATE_INPUT.get()?1:2;
                    item.useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,hit));
                    h.assertTrue(GateBodyBlock.port(level.getBlockState(pos),side)==(before==requested?0:requested),"click installs replaces or removes face port");
                    h.assertTrue(stack.getCount()==1,"reusable port");
                }
                level.setBlock(pos.relative(side),ModBlocks.BASIC_WIRE.get().defaultBlockState(),3);
                var wire=Block.updateFromNeighbourShapes(level.getBlockState(pos.relative(side)),level,pos.relative(side));
                h.assertTrue(wire.getValue(switch(side.getOpposite()){case DOWN->BasicWireBlock.DOWN;case UP->BasicWireBlock.UP;case NORTH->BasicWireBlock.NORTH;case SOUTH->BasicWireBlock.SOUTH;case EAST->BasicWireBlock.EAST;case WEST->BasicWireBlock.WEST;}),"wire reaches face");
                level.setBlock(pos.relative(side),ModBlocks.AND_GATE.get().defaultBlockState(),3);
                h.assertTrue(GateBodyBlock.port(level.getBlockState(pos),side)==0,"covering body removes port");
                h.assertTrue(CircuitConnections.connects(level.getBlockState(pos),level.getBlockState(pos.relative(side)),side),"homogeneous bodies merge");
                level.setBlock(pos.relative(side),Blocks.AIR.defaultBlockState(),3);
                h.assertTrue(GateBodyBlock.port(level.getBlockState(pos),side)==0,"uncovering does not recreate removed port");
            }
        } finally {saved.forEach((p,s)->level.setBlock(p,s,3));player.setItemInHand(hand,oldHand);}
        h.succeed();
    }
    @GameTest(template="empty",batch="gate_faces",timeoutTicks=100)
    public static void mergedBodyAndSharedWire(GameTestHelper h) {
        Map<BlockPos,BlockState> s=new HashMap<>();
        var body=ModBlocks.AND_GATE.get().defaultBlockState();
        s.put(BlockPos.ZERO,GateBodyBlock.withPort(body,Direction.WEST,1));
        s.put(new BlockPos(0,1,0),GateBodyBlock.withPort(GateBodyBlock.withPort(body,Direction.WEST,1),Direction.EAST,2));
        s.put(new BlockPos(-1,0,0),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        s.put(new BlockPos(-1,1,0),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        s.put(new BlockPos(-2,0,0),ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
        s.put(new BlockPos(1,1,0),ModBlocks.BASIC_WIRE.get().defaultBlockState());
        s.put(new BlockPos(2,1,0),ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var program=LogicProgram.compile(s);
        h.assertTrue(program.evaluate(Map.of(new BlockPos(-2,0,0),true)).get(new BlockPos(2,1,0)),"merged body uses ports on both blocks");
        s.replaceAll((p,state)->state.getBlock() instanceof GateBodyBlock?ModBlocks.XOR_GATE.get().defaultBlockState().setValue(GateBodyBlock.PORTS[Direction.WEST.ordinal()],GateBodyBlock.port(state,Direction.WEST)).setValue(GateBodyBlock.PORTS[Direction.EAST.ordinal()],GateBodyBlock.port(state,Direction.EAST)):state);
        program=LogicProgram.compile(s);
        h.assertTrue(!program.evaluate(Map.of(new BlockPos(-2,0,0),true)).get(new BlockPos(2,1,0)),"two faces reading same bus remain two XOR inputs");
        h.succeed();
    }
}