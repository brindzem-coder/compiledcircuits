package com.example.compiledcircuits.client;
import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.networking.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.lang.management.*;
/** Full production-limit CPU/heap/payload measurement; no game world, GPU or FPS claim. */
public final class MembershipCapacityScaleTest {
    public static void main(String[] args){
        String dim="minecraft:overworld";var data=new NetworkSavedData();long start=System.nanoTime();
        for(int id=1;id<=20;id++){
            var elements=new ArrayList<CompiledCircuitElement>();
            for(int i=0;i<50000;i++){int global=(id-1)*50000+i;elements.add(new CompiledCircuitElement(i+1,new BlockPos(global%1000,global/1000,0),CircuitElementType.WIRE,"compiledcircuits:basic_wire"));}
            data.addNetwork(new CompiledNetwork(id,"maximum",0,dim,elements));
        }
        long admission=System.nanoTime()-start;
        for(var pool:ManagementFactory.getMemoryPoolMXBeans())pool.resetPeakUsage();
        long baseline=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        start=System.nanoTime();var parts=CompiledElementSync.buildSnapshot(data,dim,1);long build=System.nanoTime()-start;
        if(parts.size()!=245)throw new AssertionError("Hard part limit");
        ClientCompiledElements.clear();ClientCompiledElements.onLevelChanged(new Object(),dim);
        long bytes=0,maxPart=0,encode=0,decode=0,apply=0,maxApply=0;int records=0;
        for(var part:parts){
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try{
                start=System.nanoTime();CompiledElementPositionsS2CPacket.encode(part,buffer);encode+=System.nanoTime()-start;
                bytes+=buffer.readableBytes();maxPart=Math.max(maxPart,buffer.readableBytes());
                if(buffer.readableBytes()!=part.encodedBytes()||buffer.readableBytes()>65536)throw new AssertionError("Physical part budget");
                start=System.nanoTime();var decoded=CompiledElementPositionsS2CPacket.decode(buffer);decode+=System.nanoTime()-start;
                records+=decoded.entries().size();ClientCompiledElements.accept(decoded,System.nanoTime());
            }finally{buffer.release();}
        }
        int ticks=0;
        while(!ClientCompiledElements.isReadyFor(dim)&&ticks++<600){start=System.nanoTime();ClientCompiledElements.tick(System.nanoTime());long elapsed=System.nanoTime()-start;apply+=elapsed;maxApply=Math.max(maxApply,elapsed);}
        if(!ClientCompiledElements.isReadyFor(dim)||records!=1000000||bytes>MembershipCapacity.encodedUpperBound(records))throw new AssertionError("Full capacity transfer failed");
        for(int id=1;id<=20;id++)if(ClientCompiledElements.positionsForNetwork(id).size()!=50000)throw new AssertionError("Lost membership");
        final int viewers=64;int[] received=new int[viewers],perTick=new int[viewers],global={0},maxGlobal={0},maxPlayer={0};long[] retained={0};
        UUID[] contexts=new UUID[viewers];for(int i=0;i<viewers;i++)contexts[i]=UUID.randomUUID();
        long[] simulatedNow={0};ClientCompiledElements.clear();ClientCompiledElements.onLevelChanged(new Object(),dim);ClientCompiledElements.context(contexts[viewers-1]);
        CompiledElementSync.Engine[] shared=new CompiledElementSync.Engine[1];
        shared[0]=new CompiledElementSync.Engine(data,(key,packet)->{
            int index=(Integer)key;received[index]++;perTick[index]++;global[0]++;
            if(!packet.context().equals(contexts[index]))throw new AssertionError("Peer context");
            if(index==viewers-1)ClientCompiledElements.accept(packet,simulatedNow[0]);
            if(packet.partIndex()==packet.partCount()-1&&index!=0&&index!=viewers-1)shared[0].ack(key,contexts[index],packet.snapshotId());
        });
        for(int i=0;i<viewers;i++)shared[0].open(i,contexts[i],dim,0);
        long drainStart=System.nanoTime();int serverTicks=0;
        for(;serverTicks<4000;serverTicks++){
            Arrays.fill(perTick,0);global[0]=0;simulatedNow[0]=serverTicks*50_000_000L;shared[0].begin(serverTicks);
            for(int i=0;i<2048;i++)shared[0].step();
            maxGlobal[0]=Math.max(maxGlobal[0],global[0]);for(int n:perTick)maxPlayer[0]=Math.max(maxPlayer[0],n);
            if(global[0]>8||Arrays.stream(perTick).max().orElse(0)>2)throw new AssertionError("Delivery budget");
            retained[0]=Math.max(retained[0],shared[0].retained());if(retained[0]>CompiledElementPositionsS2CPacket.MAX_BYTES)throw new AssertionError("Retained budget");
            ClientCompiledElements.tick(simulatedNow[0]);
            if(ClientCompiledElements.isReadyFor(dim))shared[0].ack(viewers-1,contexts[viewers-1],ClientCompiledElements.completedSnapshot());
            if(Arrays.stream(received).allMatch(n->n==245)&&ClientCompiledElements.isReadyFor(dim))break;
        }
        if(serverTicks>=4000)throw new AssertionError("Many-player delivery failed");
        System.out.printf(Locale.ROOT,"MEMBERSHIP_VIEWERS players=%d recordsPerPlayer=1000000 packets=%d maxGlobalPacketsPerTick=%d maxPlayerPacketsPerTick=%d sharedRetainedUpperBytes=%d simulatedTicks=%d drainWallMs=%.3f slowPeerAck=false%n",viewers,Arrays.stream(received).sum(),maxGlobal[0],maxPlayer[0],retained[0],serverTicks,(System.nanoTime()-drainStart)/1e6);
        long peak=0;for(var pool:ManagementFactory.getMemoryPoolMXBeans())if(pool.getType()==MemoryType.HEAP)peak+=pool.getPeakUsage().getUsed();
        System.out.printf(Locale.ROOT,"MEMBERSHIP_MAX records=%d parts=%d bytes=%d maxPartBytes=%d admissionMs=%.3f buildMs=%.3f encodeMs=%.3f decodeMs=%.3f applyMs=%.3f maxApplyTickMs=%.3f applyTicks=%d heapBeforeBytes=%d heapPoolPeakSumBytes=%d%n",records,parts.size(),bytes,maxPart,admission/1e6,build/1e6,encode/1e6,decode/1e6,apply/1e6,maxApply/1e6,ticks,baseline,peak);
    }
}
