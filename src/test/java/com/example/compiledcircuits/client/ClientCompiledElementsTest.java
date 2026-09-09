package com.example.compiledcircuits.client;
import com.example.compiledcircuits.networking.CompiledElementPositionsS2CPacket;
import com.example.compiledcircuits.networking.CompiledElementPositionsPacketTest;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public class ClientCompiledElementsTest {
 static int checks;
 static void check(boolean b) {checks++;if(!b)throw new AssertionError("Cache check " + checks);}
 static CompiledElementPositionsS2CPacket part(String dim,long id,int index,int count,BlockPos... positions) {
  return new CompiledElementPositionsS2CPacket(new ResourceLocation(dim),id,index,count,
   Arrays.stream(positions).map(p -> new CompiledElementPositionsS2CPacket.Entry(7,p)).toList());
 }
 public static void main(String[] args) {
  var a=new BlockPos(-1,-64,4);var b=a.above();Object level=new Object();
  ClientCompiledElements.clear();check(!ClientCompiledElements.isReadyFor("minecraft:overworld"));
  ClientCompiledElements.accept(part("minecraft:overworld",1,1,2,b),0);
  ClientCompiledElements.onLevelChanged(level,"minecraft:overworld");check(!ClientCompiledElements.isReadyFor("minecraft:overworld"));
  ClientCompiledElements.accept(part("minecraft:overworld",1,1,2,b),1);check(ClientCompiledElements.networkIdAt(b)==null);
  ClientCompiledElements.accept(part("minecraft:overworld",1,0,2,a),2);check(ClientCompiledElements.isReadyFor("minecraft:overworld"));
  check(ClientCompiledElements.networkIdAt(a)==7);check(ClientCompiledElements.positionsForNetwork(7).equals(Set.of(a,b)));
  var view=ClientCompiledElements.positionsForNetwork(7);check(view==ClientCompiledElements.positionsForNetwork(7));
  try {view.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
  long revision=ClientCompiledElements.getRevision();ClientCompiledElements.accept(part("minecraft:overworld",1,0,2,a),3);
  check(revision==ClientCompiledElements.getRevision());
  ClientCompiledElements.accept(part("minecraft:overworld",2,0,2,a),4);check(ClientCompiledElements.positionsForNetwork(7).size()==2);
  ClientCompiledElements.accept(part("minecraft:overworld",2,1,2,a),5);check(ClientCompiledElements.positionsForNetwork(7).size()==2);
  ClientCompiledElements.accept(part("minecraft:overworld",3,0,2,a),6);
  ClientCompiledElements.tick(CompiledElementPositionsS2CPacket.STAGING_TIMEOUT_NANOS+7);
  ClientCompiledElements.accept(part("minecraft:overworld",3,1,2,b),CompiledElementPositionsS2CPacket.STAGING_TIMEOUT_NANOS+8);
  check(ClientCompiledElements.positionsForNetwork(7).size()==2);
  ClientCompiledElements.accept(part("minecraft:the_nether",4,0,1,b),10);
  check(ClientCompiledElements.isReadyFor("minecraft:overworld"));
  ClientCompiledElements.onLevelChanged(new Object(),"minecraft:the_nether");
  check(ClientCompiledElements.isReadyFor("minecraft:the_nether"));check(ClientCompiledElements.networkIdAt(a)==null);
  ClientCompiledElements.onLevelChanged(null,"");check(ClientCompiledElements.networkIdAt(b)==null);
  ClientCompiledElements.onLevelChanged(new Object(),"minecraft:the_nether");check(ClientCompiledElements.networkIdAt(b)==7);
  ClientCompiledElements.accept(part("minecraft:the_nether",5,0,1),11);check(ClientCompiledElements.isReadyFor("minecraft:the_nether"));
  check(ClientCompiledElements.positionsForNetwork(7).isEmpty());
  ClientCompiledElements.clear();ClientCompiledElements.onLevelChanged(new Object(),"minecraft:the_nether");
  check(!ClientCompiledElements.isReadyFor("minecraft:the_nether"));
  CompiledElementPositionsPacketTest.run();
  System.out.println("Compiled membership cache checks passed: " + checks);
 }
}
