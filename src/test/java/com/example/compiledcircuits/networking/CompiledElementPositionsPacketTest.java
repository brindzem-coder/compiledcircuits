package com.example.compiledcircuits.networking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;
public class CompiledElementPositionsPacketTest {
 static int checks;
 static void check(boolean b){checks++;if(!b)throw new AssertionError("Packet check " + checks);}
 public static void run() {
  var dim=new ResourceLocation("minecraft:overworld");var mutable=new BlockPos.MutableBlockPos(1,2,3);
  var entry=new CompiledElementPositionsS2CPacket.Entry(4,mutable);mutable.set(8,8,8);check(entry.pos().equals(new BlockPos(1,2,3)));
  var entries=new ArrayList<CompiledElementPositionsS2CPacket.Entry>();
  for(int i=0;i<4096;i++)entries.add(new CompiledElementPositionsS2CPacket.Entry(Integer.MAX_VALUE,new BlockPos(i,-64,1)));
  var packet=new CompiledElementPositionsS2CPacket(dim,1,0,1,entries);entries.clear();check(packet.entries().size()==4096);
  var buf=new FriendlyByteBuf(Unpooled.buffer());CompiledElementPositionsS2CPacket.encode(packet,buf);check(buf.readableBytes()<65536);
  check(CompiledElementPositionsS2CPacket.decode(buf).equals(packet));buf.release();
  try {new CompiledElementPositionsS2CPacket(dim,2,0,1,List.of(entry,entry));throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
  try {new CompiledElementPositionsS2CPacket.Entry(0,BlockPos.ZERO);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
  for(int count:new int[]{0,-1,1000}) {
   try {new CompiledElementPositionsS2CPacket(dim,2,0,count,List.of(entry));throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
  }
  System.out.println("Compiled membership packet checks passed: " + checks);
 }
}
