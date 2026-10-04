package com.example.compiledcircuits.network;
import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.networking.*;
import com.example.compiledcircuits.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("compiledcircuits") @PrefixGameTestTemplate(false)
public class MembershipCapacityGameTests {
    static final String DIM="minecraft:overworld",OTHER="minecraft:the_nether";
    static NetworkSavedData limited(int network,int dimension){return new NetworkSavedData(network,dimension);}
    static CompiledNetwork n(int id,int count,int offset,String dim){var positions=new HashSet<BlockPos>();for(int i=0;i<count;i++)positions.add(new BlockPos(offset+i,80,1900000));return new CompiledNetwork(id,"capacity",0,dim,positions,Set.of(),Set.of());}
    static void reject(GameTestHelper h,NetworkSavedData data,Runnable work){var before=data.save(new CompoundTag());int id=data.getNextNetworkId();data.setDirty(false);try{work.run();throw new AssertionError("Accepted over capacity");}catch(NetworkSavedData.AdmissionException expected){}h.assertTrue(before.equals(data.save(new CompoundTag()))&&!data.isDirty()&&id==data.getNextNetworkId(),"rejection changes no saved data or next ID");}
    @GameTest(template="empty",timeoutTicks=100)
    public static void admissionBoundaries(GameTestHelper h){
        var d=limited(4,8);d.addNetwork(n(1,3,0,DIM));h.assertTrue(d.capacity().count(DIM)==3,"limit minus one");d.replaceNetwork(n(1,4,0,DIM));reject(h,d,()->d.replaceNetwork(n(1,5,0,DIM)));
        d.addNetwork(n(2,3,10,DIM));d.addNetwork(n(3,1,20,DIM));h.assertTrue(d.capacity().count(DIM)==8,"dimension inclusive limit");reject(h,d,()->d.addNetwork(n(4,1,30,DIM)));
        d.getNetwork(1).markBroken(new BrokenCircuitElement(1,"minecraft:air",0));d.getNetwork(2).setPowered(true);h.assertTrue(d.capacity().count(DIM)==8,"damage and power do not free capacity");
        d.removeNetwork(3);d.addNetwork(n(4,1,30,DIM));h.assertTrue(d.capacity().count(DIM)==8,"removal frees capacity");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void batchAndCrossDimension(GameTestHelper h){
        var d=limited(4,4);reject(h,d,()->d.addNetworks(List.of(n(1,3,0,DIM),n(2,2,10,DIM))));h.assertTrue(d.capacity().count(DIM)==0,"batch atomic");
        d.addNetworks(List.of(n(1,3,0,DIM),n(2,3,10,OTHER)));reject(h,d,()->d.replaceNetwork(n(1,2,0,OTHER)));
        d.replaceNetwork(n(1,1,0,OTHER));h.assertTrue(d.capacity().count(DIM)==0&&d.capacity().count(OTHER)==4,"both dimensions updated");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void legacyReductionAndReload(GameTestHelper h){
        var raw=n(1,9,0,DIM);var list=new ListTag();list.add(raw.save());var tag=new CompoundTag();tag.put("networks",list);
        var d=NetworkSavedData.load(tag,limited(4,5));
        h.assertTrue(d.getNetwork(1).getElements().size()==9&&d.capacity().oversizedNetworks(DIM)==1,"legacy preserved");
        h.assertTrue(CompiledElementSync.buildSnapshot(d,DIM,1).get(0).state()==CompiledElementPositionsS2CPacket.State.OVER_CAPACITY,"explicit capacity status");
        reject(h,d,()->d.addNetwork(n(2,1,20,DIM)));d.replaceNetwork(n(1,7,0,DIM));h.assertTrue(d.capacity().count(DIM)==7,"gradual reduction while oversized");
        d.replaceNetwork(n(1,4,0,DIM));h.assertTrue(d.capacity().usage(DIM).state()==MembershipCapacity.State.READY&&d.capacity().oversizedNetworks(DIM)==0,"automatic recovery");
        h.assertTrue(CompiledElementSync.buildSnapshot(d,DIM,2).get(0).entries().size()==4,"recovered full snapshot");
        h.assertTrue(NetworkSavedData.load(d.save(new CompoundTag())).capacity().count(DIM)==4,"derived count rebuilt on reload");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void actualProtocolBounds(GameTestHelper h){
        h.assertTrue(MembershipCapacity.parts(MembershipCapacity.DIMENSION_HARD)==CompiledElementPositionsS2CPacket.MAX_PARTS,"hard parts relationship");
        h.assertTrue(MembershipCapacity.encodedUpperBound(MembershipCapacity.DIMENSION_HARD)<CompiledElementPositionsS2CPacket.MAX_BYTES,"hard byte relationship");
        try{MembershipCapacity.encodedUpperBound(Long.MAX_VALUE);throw new AssertionError("Overflow accepted");}catch(ArithmeticException expected){}
        var d=limited(50000,1000000);reject(h,d,()->d.addNetwork(n(1,50001,0,DIM)));h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void preparedCommitRace(GameTestHelper h){
        var d=limited(3,3);var a=new CompiledNetwork.Builder();var b=new CompiledNetwork.Builder();
        for(int i=0;i<2;i++){
            var type=i==0?CircuitElementType.INPUT:CircuitElementType.OUTPUT;String block=i==0?"compiledcircuits:input_endpoint":"compiledcircuits:output_endpoint";
            a.add(new CompiledCircuitElement(i+1,new BlockPos(i,90,1800000),type,block));b.add(new CompiledCircuitElement(i+1,new BlockPos(i+10,90,1800000),type,block));
        }
        var na=a.seal("A",DIM);var nb=b.seal("B",DIM);var pa=d.prepareCompilation(na);var pb=d.prepareCompilation(nb);
        for(var e:na.getElements())pa.stage(e);for(var e:nb.getElements())pb.stage(e);
        while(pa.preparedRuntime.indexNext()){}while(pb.preparedRuntime.indexNext()){}
        pa.publish();reject(h,d,pb::publish);pb.beginRollback();while(pb.rollbackNext()){}
        h.assertTrue(d.capacity().count(DIM)==2&&d.getNextNetworkId()==2&&d.getNetworks().size()==1,"only first concurrent publication consumes capacity and ID");
        d.removeNetwork(1);d.addNetwork(new CompiledNetwork(2,"after rollback",0,DIM,nb.getElements()));h.assertTrue(d.capacity().count(DIM)==2,"failed preparation leaves no capacity debt");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void isolatedAccountingAndUnknown(GameTestHelper h){
        var raw=n(1,3,0,DIM).save();var list=new ListTag();list.add(raw);list.add(raw.copy());var tag=new CompoundTag();tag.put("networks",list);
        var d=NetworkSavedData.load(tag,limited(4,5));h.assertTrue(d.capacity().count(DIM)==6&&d.reservedPositionsView(DIM).size()==3,"each isolated record conservatively counts, wire positions deduplicate");
        h.assertTrue(d.capacity().usage(DIM).state()==MembershipCapacity.State.OVER_CAPACITY,"raw records consume capacity");
        var records=d.getInvalidMembershipRecords();h.assertTrue(records.get(0).getCompound("raw").equals(raw),"raw evidence untouched");
        d.removeInvalidMembershipRecord(records.get(0).getString("recordId"));h.assertTrue(d.capacity().count(DIM)==3&&d.capacity().usage(DIM).state()==MembershipCapacity.State.READY,"isolated deletion releases only its own accounting");
        var bad=new CompoundTag();bad.putString("networks","unreadable");var unknown=NetworkSavedData.load(bad,limited(4,5));
        h.assertTrue(CompiledElementSync.buildSnapshot(unknown,DIM,1).get(0).state()==CompiledElementPositionsS2CPacket.State.UNKNOWN,"unknown accounting is explicit");
        try{unknown.addNetwork(n(1,1,0,DIM));throw new AssertionError("Unknown accounting admitted");}catch(IllegalStateException expected){}
        unknown.removeInvalidMembershipRecord(unknown.getInvalidMembershipRecords().get(0).getString("recordId"));unknown.addNetwork(n(1,1,0,DIM));h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void streamRecoveryAcrossDimensions(GameTestHelper h){
        var list=new ListTag();list.add(n(1,9,0,DIM).save());list.add(n(2,1,0,OTHER).save());var tag=new CompoundTag();tag.put("networks",list);var d=NetworkSavedData.load(tag,limited(4,5));
        var received=new HashMap<Object,List<CompiledElementPositionsS2CPacket>>();var engine=new CompiledElementSync.Engine(d,(key,p)->received.computeIfAbsent(key,k->new ArrayList<>()).add(p));
        Object a=new Object(),b=new Object();UUID ca=UUID.randomUUID(),cb=UUID.randomUUID();engine.open(a,ca,DIM,0);engine.open(b,cb,OTHER,0);
        for(int tick=0;tick<5;tick++){engine.begin(tick);for(int i=0;i<100;i++)engine.step();}
        h.assertTrue(received.get(a).get(0).state()==CompiledElementPositionsS2CPacket.State.OVER_CAPACITY&&received.get(b).get(0).entries().size()==1,"capacity failure isolated to its dimension");
        engine.ack(a,ca,received.get(a).get(0).snapshotId());engine.ack(b,cb,received.get(b).get(0).snapshotId());d.replaceNetwork(n(1,4,0,DIM));
        for(int tick=5;tick<10;tick++){engine.begin(tick);for(int i=0;i<100;i++)engine.step();}
        h.assertTrue(received.get(a).size()==2&&received.get(a).get(1).entries().size()==4,"automatic READY recovery without resync or relog");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void oversizedIsolatedRemovalIsBudgeted(GameTestHelper h){
        var raw=n(1,50001,0,DIM).save();var records=new ListTag();records.add(raw);records.add(raw.copy());var tag=new CompoundTag();tag.put("networks",records);
        var d=NetworkSavedData.load(tag,limited(4,5));var id=d.getInvalidMembershipRecords().get(0).getString("recordId");long before=d.capacity().count(DIM);
        h.assertTrue(d.queueInvalidRecordRemoval(id,null),"large isolated record queued");h.assertTrue(!d.queueInvalidRecordRemoval(id,null),"one bounded removal job");
        d.reservationRemovalStep();h.assertTrue(d.capacity().count(DIM)==before&&d.getInvalidMembershipRecordCount()==2,"no partial raw removal publication");
        int work=1;while(d.reservationRemovalStep()){if(++work>300000)throw new AssertionError("Cleanup did not finish");}
        h.assertTrue(work>50000&&d.capacity().count(DIM)==50001&&d.getInvalidMembershipRecordCount()==1,"incremental cleanup releases one raw record only");
        h.assertTrue(d.getInvalidMembershipRecords().get(0).getCompound("raw").equals(raw),"remaining oversized evidence untouched");h.succeed();
    }

    @GameTestGenerator
    public static java.util.Collection<TestFunction> copiedLegacyWorlds(){
        String path=System.getProperty("compiledcircuits.legacyInputs");if(path==null||path.isEmpty())return List.of();
        return List.of(new TestFunction("stage09_legacy","copiedbaselineworlds","compiledcircuits:empty",net.minecraft.world.level.block.Rotation.NONE,100,0,true,h->{
            try{MembershipLegacyCheck.main(new String[]{path});h.succeed();}catch(Exception failure){throw new RuntimeException(failure);}
        }));
    }
}
