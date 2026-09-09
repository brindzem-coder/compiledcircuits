package com.example.compiledcircuits.gametest;
import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.registry.ModBlocks;
import com.example.compiledcircuits.block.EndpointBlock;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.example.compiledcircuits.network.CompiledBlockStateCodecTest.check;
@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class Stage7GameTests {
 @GameTest(template="empty",timeoutTicks=100)
 public static void persistenceAndRepair(GameTestHelper helper) {
  CompiledBlockStateCodecTest.run();CompiledBlockStateMigrationTest.run();CompiledBlockStateMatcherTest.run();
  var level=helper.getLevel(); var pos=helper.absolutePos(new BlockPos(1,2,1));
  var east=ModBlocks.INPUT_ENDPOINT.get().defaultBlockState().setValue(EndpointBlock.FACING,Direction.EAST);
  level.setBlock(pos,east,Block.UPDATE_ALL);
  var elements=CompiledElementFactory.create(level,Set.of(),Set.of(pos),Set.of());
  check(elements.get(0).resolveState().state().orElseThrow().equals(east));
  var data=NetworkSavedData.get(level.getServer());
  int id=data.getNextNetworkId();
  var network=new CompiledNetwork(id,"Stage7 test",0,level.dimension().location().toString(),Set.of(),Set.of(pos),Set.of(),elements);
  network=CompiledNetwork.load(network.save());data.addNetwork(network);
  level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
  NetworkIntegrityManager.checkPosition(level,pos);check(network.isDamaged());
  var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
  var result=NetworkRepairManager.repairNetwork(level,network,player);
  check(result.repaired()==1);check(level.getBlockState(pos).equals(east));
  NetworkIntegrityManager.processPending(level.getServer());check(!network.isDamaged());
  level.setBlock(pos,Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);NetworkIntegrityManager.checkPosition(level,pos);
  check(NetworkRepairManager.repairNetwork(level,network,player).skippedOccupied()==1);
  check(level.getBlockState(pos).is(Blocks.STONE));
  data.removeNetwork(id);
  System.out.println("Stage7 checks passed: " + CompiledBlockStateCodecTest.checks);
  helper.succeed();
 }
}

