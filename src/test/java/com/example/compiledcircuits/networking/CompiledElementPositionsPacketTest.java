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
  var blockedPacket = new CompiledElementPositionsS2CPacket(dim,3,0,1,List.of(entry),List.of(BlockPos.ZERO),true);
  var blockedBuffer = new FriendlyByteBuf(Unpooled.buffer());
  CompiledElementPositionsS2CPacket.encode(blockedPacket,blockedBuffer);
  check(blockedBuffer.readableBytes()==blockedPacket.encodedBytes());
  check(CompiledElementPositionsS2CPacket.decode(blockedBuffer).equals(blockedPacket)); blockedBuffer.release();
  try { new CompiledElementPositionsS2CPacket(dim,4,0,1,List.of(entry),List.of(entry.pos()),false); throw new AssertionError(); }
  catch (IllegalArgumentException expected) { checks++; }
  var worstEntries=new ArrayList<CompiledElementPositionsS2CPacket.Entry>();for(int i=0;i<4096;i++)worstEntries.add(new CompiledElementPositionsS2CPacket.Entry(Integer.MAX_VALUE,new BlockPos(i,0,0)));
  var worst=new CompiledElementPositionsS2CPacket(new ResourceLocation("t:"+"a".repeat(254)),7,0,1,worstEntries,List.of(),false,UUID.randomUUID(),CompiledElementPositionsS2CPacket.State.READY,"漢".repeat(128));
  var worstBuffer=new FriendlyByteBuf(Unpooled.buffer());
  try{CompiledElementPositionsS2CPacket.encode(worst,worstBuffer);check(worstBuffer.readableBytes()==worst.encodedBytes()&&worst.encodedBytes()<=worst.estimatedBytes()&&worst.encodedBytes()<65536);check(CompiledElementPositionsS2CPacket.decode(worstBuffer).equals(worst));}
  finally{worstBuffer.release();}
  var trailing=new FriendlyByteBuf(Unpooled.buffer());try{CompiledElementPositionsS2CPacket.encode(packet,trailing);trailing.writeByte(0);try{CompiledElementPositionsS2CPacket.decode(trailing);throw new AssertionError("Trailing data accepted");}catch(IllegalArgumentException expected){checks++;}}finally{trailing.release();}
  var excessive=new FriendlyByteBuf(Unpooled.buffer());try{excessive.writeZero(65537);try{CompiledElementPositionsS2CPacket.decode(excessive);throw new AssertionError("Oversized packet accepted");}catch(IllegalArgumentException expected){checks++;}}finally{excessive.release();}
  for(int size:new int[]{1023,1024,1025}){
   var rows=Collections.nCopies(size,new NetworkListS2CPacket.Entry(1,"name",0,"minecraft:overworld",false,1,0,0));check(NetworkListS2CPacket.fits(rows,List.of())==(size<=1024));
  }
  check(!NetworkListS2CPacket.fits(Collections.nCopies(1024,new NetworkListS2CPacket.Entry(1,"漢".repeat(256),0,"t:"+"a".repeat(254),false,1,0,0)),List.of()));
  var unavailable=new FriendlyByteBuf(Unpooled.buffer());try{NetworkListS2CPacket.encode(NetworkListS2CPacket.unavailable(),unavailable);check(!NetworkListS2CPacket.decode(unavailable).available);}finally{unavailable.release();}
  for(int size:new int[]{-1,1025,Integer.MAX_VALUE}){var invalid=new FriendlyByteBuf(Unpooled.buffer());try{invalid.writeBoolean(true);invalid.writeInt(size);try{NetworkListS2CPacket.decode(invalid);throw new AssertionError("Invalid list count");}catch(IllegalArgumentException expected){checks++;}}finally{invalid.release();}}
  for(int count:new int[]{CompiledElementPositionsS2CPacket.MAX_PARTS-1,CompiledElementPositionsS2CPacket.MAX_PARTS,CompiledElementPositionsS2CPacket.MAX_PARTS+1}){
   try{new CompiledElementPositionsS2CPacket(dim,8,0,count,List.of(entry));check(count<=CompiledElementPositionsS2CPacket.MAX_PARTS);}catch(IllegalArgumentException expected){check(count>CompiledElementPositionsS2CPacket.MAX_PARTS);}
  }
  try{new CompiledElementPositionsS2CPacket(dim,8,0,1,Collections.nCopies(Integer.MAX_VALUE,entry),List.of(BlockPos.ZERO),false);throw new AssertionError("Overflowing list sizes accepted");}catch(IllegalArgumentException expected){checks++;}
  System.out.println("Compiled membership packet checks passed: " + checks);
 }
}
