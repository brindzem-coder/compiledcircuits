package com.example.compiledcircuits.client;
import com.example.compiledcircuits.networking.*;
import com.example.compiledcircuits.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;

/** Synthetic CPU/payload benchmark. Does not measure game ticks, GPU or FPS. */
public final class VisualScaleTest {
    public static void main(String[] args) {
        for (int size : new int[]{10000,50000}) {
            var elements = new ArrayList<CompiledCircuitElement>();
            for (int i=0;i<size;i++) elements.add(new CompiledCircuitElement(i+1,
                    new BlockPos(i%1000-500,-64+i/1000,-17), CircuitElementType.WIRE,"compiledcircuits:basic_wire"));
            var data = new NetworkSavedData();
            data.addNetwork(new CompiledNetwork(1,"scale",0,"minecraft:overworld",Set.of(),Set.of(),Set.of(),elements));
            var parts = CompiledElementSync.buildSnapshot(data,"minecraft:overworld",size);
            long buildNanos = CompiledElementSync.getSnapshotBuildNanos();
            ClientCompiledElements.clear();
            Object level = new Object();
            ClientCompiledElements.onLevelChanged(level,"minecraft:overworld");
            long bytes=0, encodeNanos=0, decodeNanos=0, commitNanos=0;
            for (var part : parts) {
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    long start = System.nanoTime();
                    CompiledElementPositionsS2CPacket.encode(part,buffer);
                    encodeNanos += System.nanoTime()-start;
                    if (buffer.readableBytes()!=part.encodedBytes() || buffer.readableBytes()>65536) throw new AssertionError("Packet budget");
                    bytes+=buffer.readableBytes();
                    start=System.nanoTime();
                    var decoded=CompiledElementPositionsS2CPacket.decode(buffer);
                    decodeNanos+=System.nanoTime()-start;
                    start=System.nanoTime();
                    ClientCompiledElements.accept(decoded,System.nanoTime());
                    commitNanos+=System.nanoTime()-start;
                } finally { buffer.release(); }
            }
            if(bytes!=CompiledElementSync.getSnapshotBytes()) throw new AssertionError("Byte counter");
            var positions=ClientCompiledElements.positionsForNetwork(1);
            if(positions.size()!=size) throw new AssertionError("Membership lost");
            ClientHoveredCircuit.reset();
            long start=System.nanoTime();
            for(int i=0;i<10000;i++) ClientHoveredCircuit.update(level,"minecraft:overworld",elements.get(0).getPos(),1,1,
                    positions,(p,d)->{throw new AssertionError("Compiled BFS");});
            long hoverNanos=System.nanoTime()-start;
            if(ClientHoveredCircuit.getBfsStarted()!=0 || ClientHoveredCircuit.getPositions()!=positions) throw new AssertionError("Compiled cache");
            System.out.printf(Locale.ROOT,"SCALE nodes=%d parts=%d bytes=%d buildMs=%.3f encodeMs=%.3f decodeMs=%.3f commitMs=%.3f hover10000Ms=%.3f%n",
                    size,parts.size(),bytes,buildNanos/1e6,encodeNanos/1e6,decodeNanos/1e6,commitNanos/1e6,hoverNanos/1e6);
        }
        ClientCompiledElements.clear();ClientHoveredCircuit.reset();CompiledElementSync.clear();
    }
}
