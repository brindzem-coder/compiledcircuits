package com.example.compiledcircuits.network;
import com.example.compiledcircuits.registry.ModBlocks;
import com.example.compiledcircuits.block.EndpointBlock;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

public class CompiledBlockStateCodecTest {
 public static int checks;
 public static void check(boolean value) { checks++; if (!value) throw new AssertionError("Stage7 check " + checks); }
 static void roundtrip(BlockState state) {
  var snapshot=CompiledBlockStateCodec.capture(state);
  var decoded=CompiledBlockStateCodec.readAndResolve(CompiledBlockStateCodec.write(snapshot),snapshot.blockId());
  check(decoded.state().orElseThrow().equals(state));
 }
 public static void run() {
  for(var block : new net.minecraft.world.level.block.Block[]{ModBlocks.INPUT_ENDPOINT.get(),ModBlocks.OUTPUT_ENDPOINT.get()})
   for(var facing : Direction.values()) roundtrip(block.defaultBlockState().setValue(EndpointBlock.FACING,facing));
  var wire=ModBlocks.BASIC_WIRE.get();
  for(var state: wire.getStateDefinition().getPossibleStates()) roundtrip(state);
  roundtrip(Blocks.STONE.defaultBlockState());
  for(var state: Blocks.REPEATER.getStateDefinition().getPossibleStates()) roundtrip(state);
  var snapshot=CompiledBlockStateCodec.capture(ModBlocks.INPUT_ENDPOINT.get().defaultBlockState());
  var good=CompiledBlockStateCodec.write(snapshot);
  for(int i=0;i<10;i++) {
   var bad=good.copy(); var properties=bad.getCompound("compiledBlockState").getCompound("Properties");
   switch(i) {
    case 0 -> properties.remove("facing");
    case 1 -> properties.putString("unknown","true");
    case 2 -> properties.putString("facing","diagonal");
    case 3 -> properties.putInt("facing",1);
    case 4 -> bad.putInt("stateDataVersion",99);
    case 5 -> bad.remove("stateOrigin");
    case 6 -> bad.putString("stateOrigin","FUTURE");
    case 7 -> bad.getCompound("compiledBlockState").putString("Name","missing:block");
    case 8 -> properties.putString("facing","x".repeat(129));
    case 9 -> bad.putString("compiledBlockState","bad");
   }
   check(CompiledBlockStateCodec.readAndResolve(bad,snapshot.blockId()).status()==CompiledBlockStateCodec.Status.UNRESOLVED);
   bad.putInt("id",1);bad.putLong("pos",0);bad.putString("type","INPUT");bad.putString("blockId",snapshot.blockId());
   check(CompiledCircuitElement.load(bad).save().equals(bad));
  }
  check(CompiledBlockStateCodec.readAndResolve(good,"minecraft:stone").status()==CompiledBlockStateCodec.Status.UNRESOLVED);
 }
}
