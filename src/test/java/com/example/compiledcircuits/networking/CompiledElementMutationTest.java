package com.example.compiledcircuits.networking;
import com.example.compiledcircuits.network.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import java.util.*;
public final class CompiledElementMutationTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Mutation check "+checks);}
    static CompiledNetwork n(int id,String dim){return new CompiledNetwork(id,"test",0,dim,Set.of(new BlockPos(id,0,0)),Set.of(),Set.of());}
    public static void main(String[] args){
        String a="minecraft:overworld",b="minecraft:the_nether";var d=new NetworkSavedData();
        d.addNetwork(n(1,a));long revision=d.capacity().revision(a);check(revision>0&&d.capacity().count(a)==1);
        check(d.moveNetwork(1,0));check(d.capacity().revision(a)==revision);check(!d.removeNetwork(999));check(d.capacity().revision(a)==revision);
        check(d.removeNetworks(List.of(1,999)).isEmpty());check(d.capacity().count(a)==1);
        d.replaceNetwork(n(1,b));check(d.capacity().count(a)==0&&d.capacity().count(b)==1);check(d.capacity().revision(a)>revision);
        var loaded=NetworkSavedData.load(d.save(new CompoundTag()));check(loaded.capacity().count(b)==1);check(loaded.removeNetwork(1));check(loaded.capacity().count(b)==0);
        var packets=new HashMap<Object,List<CompiledElementPositionsS2CPacket>>();
        var engine=new CompiledElementSync.Engine(d,(peer,packet)->packets.computeIfAbsent(peer,k->new ArrayList<>()).add(packet));
        Object p1=new Object(),p2=new Object();UUID c1=UUID.randomUUID(),c2=UUID.randomUUID();check(engine.open(p1,c1,b,0));check(engine.open(p2,c2,b,0));
        for(int tick=0;tick<10;tick++){engine.begin(tick);for(int i=0;i<100;i++)engine.step();}
        check(packets.get(p1).size()==1&&packets.get(p2).size()==1);var first=packets.get(p1).get(0);check(first.snapshotId()==packets.get(p2).get(0).snapshotId());check(first.context().equals(c1));
        engine.ack(p1,c1,first.snapshotId());engine.ack(p2,c2,first.snapshotId());d.removeNetwork(1);
        for(int tick=10;tick<20;tick++){engine.begin(tick);for(int i=0;i<100;i++)engine.step();}
        check(packets.get(p1).size()==2&&packets.get(p1).get(1).entries().isEmpty());
        engine.close(p1);engine.close(p2);engine.begin(21);check(engine.clients()==0&&engine.retained()==0);
        var keys=new Object[65];var token=UUID.randomUUID();
        for(int i=0;i<65;i++){keys[i]=new Object();check(engine.open(keys[i],token,b,50)==(i<64));}
        for(int tick=50;tick<65;tick++){engine.begin(tick);for(int i=0;i<100;i++)engine.step();}
        check(packets.get(keys[64]).get(0).state()==CompiledElementPositionsS2CPacket.State.UNKNOWN);
        engine.close(keys[0]);check(engine.open(keys[64],UUID.randomUUID(),b,100));
        engine.begin(1000);check(engine.clients()==1);
        System.out.println("Mutation and membership engine checks passed: "+checks);
    }
}
