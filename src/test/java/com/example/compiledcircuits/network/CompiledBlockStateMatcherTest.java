package com.example.compiledcircuits.network;
import com.example.compiledcircuits.registry.ModBlocks;
import com.example.compiledcircuits.block.EndpointBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import static com.example.compiledcircuits.network.CompiledBlockStateCodecTest.check;
public class CompiledBlockStateMatcherTest {
 public static void run() {
  var east=ModBlocks.INPUT_ENDPOINT.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
  var north=east.setValue(EndpointBlock.FACING,Direction.NORTH);
  var element=new CompiledCircuitElement(7,BlockPos.ZERO,CircuitElementType.INPUT,CompiledBlockStateCodec.capture(east));
  check(CompiledBlockStateMatcher.match(element,north,false)==CompiledBlockStateMatcher.Match.MATCH);
  check(CompiledBlockStateMatcher.match(element,north,true)==CompiledBlockStateMatcher.Match.STATE_MISMATCH);
  check(CompiledBlockStateMatcher.match(element,east,true)==CompiledBlockStateMatcher.Match.MATCH);
  check(CompiledBlockStateMatcher.match(element,Blocks.AIR.defaultBlockState(),false)==CompiledBlockStateMatcher.Match.BLOCK_MISMATCH);
  var reloaded=CompiledCircuitElement.load(element.save());check(reloaded.resolveState().state().orElseThrow().equals(east));
  var legacy=new CompiledCircuitElement(7,BlockPos.ZERO,CircuitElementType.INPUT,element.getBlockId());
  check(CompiledBlockStateMatcher.match(legacy,north,true)==CompiledBlockStateMatcher.Match.MATCH);
  var bad=element.save();bad.putInt("stateDataVersion",2);
  check(CompiledBlockStateMatcher.match(CompiledCircuitElement.load(bad),east,false)==CompiledBlockStateMatcher.Match.UNRESOLVED);
 }
}
