package com.example.compiledcircuits.network;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import java.util.*;
import static com.example.compiledcircuits.network.CompiledBlockStateCodecTest.check;
public class CompiledBlockStateMigrationTest {
 public static void run() {
  var pos=new BlockPos(1,64,2);
  var n=new CompiledNetwork(19,"Saved",7,"minecraft:overworld",Set.of(pos),Set.of(),Set.of(),
   List.of(new CompiledCircuitElement(6,pos,CircuitElementType.WIRE,"compiledcircuits:basic_wire")));
  n.setPowered(true); n.markBroken(new BrokenCircuitElement(6,"minecraft:air",123));
  var tag=n.save(); var old=tag.getList("elements",Tag.TAG_COMPOUND).getCompound(0);
  old.remove("stateDataVersion"); old.remove("stateOrigin");
  var root=new CompoundTag(); var list=new ListTag(); list.add(tag); root.put("networks",list); root.putInt("nextNetworkId",20);
  var data=NetworkSavedData.load(root);check(data.isDirty());
  var loaded=data.getNetwork(19); check(loaded.getElement(6).getPos().equals(pos));
  check(loaded.getElement(6).resolveState().status()==CompiledBlockStateCodec.Status.LEGACY);
  check(loaded.isPowered() && loaded.isDamaged() && loaded.getFolderId()==7);
  check(!NetworkSavedData.load(data.save(new CompoundTag())).isDirty());
  tag.remove("elements"); var legacy=CompiledNetwork.load(tag);
  check(legacy.needsPersistenceUpgrade());check(legacy.getElement(1)!=null);
  check(legacy.getElement(1).resolveState().status()==CompiledBlockStateCodec.Status.LEGACY);
  check(!CompiledNetwork.load(legacy.save()).needsPersistenceUpgrade());
 }
}
