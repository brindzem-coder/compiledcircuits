package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.io.*;

@GameTestHolder("compiledcircuits") @PrefixGameTestTemplate(false)
public class PortableBlueprintGameTests {
    private static CircuitBlueprint source(int network, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        var tag=new CompoundTag();var list=new ListTag();
        list.add(new CompiledCircuitElement(1,pos,CircuitElementType.WIRE,CompiledBlockStateCodec.capture(state)).save());
        tag.put("elements",list);tag.putLong("origin",pos.asLong());tag.putString("sourceDimension","minecraft:overworld");
        tag.putInt("sourceNetwork",network);tag.putString("name","Portable test");return new CircuitBlueprint(tag);
    }
    private static void rejected(GameTestHelper h, Runnable action, String reason) {
        boolean failed=false;try{action.run();}catch(IllegalArgumentException expected){failed=true;}h.assertTrue(failed,reason);
    }
    @GameTest(template="empty",batch="portable_format")
    public static void groupsBecomeRawAndPreserveSettings(GameTestHelper h) {
        var origin=new BlockPos(12000,70,-8000);
        var a=source(1,origin,NetworkWireBlock.withFace(ModBlocks.ISOLATED_WIRE.get().defaultBlockState(),Direction.UP,3));
        var b=source(2,origin.offset(5,2,-4),ModBlocks.BUNDLED_WIRE.get().defaultBlockState());
        var data=b.save();var entry=data.getList("elements",Tag.TAG_COMPOUND).getCompound(0);
        var faces=BundleWireEntity.encode(Map.of(Direction.EAST,new BundleWireEntity.Port(7,"0-7=8-15")));
        entry.put("bundleFaces",faces);b=new CircuitBlueprint(data);
        var group=a.append(b);h.assertTrue(group.append(b).networkCount()==2,"duplicate additions do not multiply networks");
        var loaded=PortableBlueprint.decode(PortableBlueprint.encode(group));
        h.assertTrue(loaded.uncompiled()&&loaded.networks(5,"minecraft:the_nether",BlockPos.ZERO,0,false).isEmpty(),"no compiled network is imported");
        var elements=loaded.elements(BlockPos.ZERO,0);
        h.assertTrue(elements.size()==2&&elements.get(0).getPos().equals(BlockPos.ZERO)&&elements.get(1).getPos().equals(new BlockPos(5,2,-4)),"relative spacing preserved across world coordinates");
        h.assertTrue(elements.get(0).getBlockId().equals("compiledcircuits:isolated_wire")&&NetworkWireBlock.face(elements.get(0).resolveState().state().orElseThrow(),Direction.UP)==3,"isolated wire and face port retained");
        h.assertTrue(elements.get(1).bundleFaces().equals(faces),"mapping settings retained");
        h.assertTrue(Arrays.equals(PortableBlueprint.encode(loaded),PortableBlueprint.encode(PortableBlueprint.decode(PortableBlueprint.encode(loaded)))),"file round trip is stable");
        h.succeed();
    }
    @GameTest(template="empty",batch="portable_format")
    public static void rejectsInvalidFilesAndRuntimeMetadata(GameTestHelper h) throws Exception {
        var good=source(1,BlockPos.ZERO,ModBlocks.BASIC_WIRE.get().defaultBlockState());
        var bytes=PortableBlueprint.encode(good);
        rejected(h,()->PortableBlueprint.decode(Arrays.copyOf(bytes,bytes.length-2)),"truncated file rejected");
        rejected(h,()->PortableBlueprint.decode(new byte[PortableBlueprint.MAX_BYTES+1]),"oversized file rejected");
        rejected(h,()->PortableBlueprint.decode(Arrays.copyOf(bytes,bytes.length+1)),"trailing data rejected");
        rejected(h,()->PortableBlueprint.encode(source(1,BlockPos.ZERO,Blocks.COMMAND_BLOCK.defaultBlockState())),"arbitrary vanilla blocks rejected");
        var root=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
        root.getCompound("blueprint").putBoolean("uncompiled",false);
        var networks=new ListTag();networks.add(good.save());root.getCompound("blueprint").put("networks",networks);
        var output=new ByteArrayOutputStream();NbtIo.write(root,new DataOutputStream(output));
        var loaded=PortableBlueprint.decode(output.toByteArray());h.assertTrue(loaded.uncompiled()&&!loaded.save().contains("networks"),"file cannot reintroduce compiled network metadata");
        root.putInt("formatVersion",999);output.reset();NbtIo.write(root,new DataOutputStream(output));
        var invalidVersion=output.toByteArray();rejected(h,()->PortableBlueprint.decode(invalidVersion),"future format rejected");h.succeed();
    }
    @GameTest(template="empty",batch="portable_format")
    public static void transferIsBoundedAndAtomic(GameTestHelper h) {
        byte[] bytes=new byte[PortableBlueprint.PART_BYTES+123];new Random(42).nextBytes(bytes);
        var id=UUID.randomUUID();var transfer=new BlueprintTransfer();
        h.assertTrue(transfer.accept(id,0,bytes.length,Arrays.copyOf(bytes,PortableBlueprint.PART_BYTES))==null,"partial transfer is not published");
        rejected(h,()->transfer.accept(UUID.randomUUID(),PortableBlueprint.PART_BYTES,bytes.length,Arrays.copyOfRange(bytes,PortableBlueprint.PART_BYTES,bytes.length)),"wrong token rejected");
        var complete=transfer.accept(id,PortableBlueprint.PART_BYTES,bytes.length,Arrays.copyOfRange(bytes,PortableBlueprint.PART_BYTES,bytes.length));
        h.assertTrue(Arrays.equals(bytes,complete),"ordered transfer reconstructs exact bytes");
        rejected(h,()->new BlueprintTransfer().accept(id,0,Integer.MAX_VALUE,new byte[1]),"unbounded total rejected");
        rejected(h,()->new BlueprintTransfer().accept(id,1,10,new byte[1]),"missing first part rejected");
        h.succeed();
    }
    @GameTest(template="empty",batch="portable_world",timeoutTicks=300)
    public static void importedFilePastesWithoutCompilation(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();
        server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"portable-test"));
        player.getAbilities().mayBuild=true;player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(ModItems.CIRCUIT_COPIER.get()));
        var target=h.absolutePos(new BlockPos(1,3,1));var old=level.getBlockState(target);long chunk=net.minecraft.world.level.ChunkPos.asLong(target.getX()>>4,target.getZ()>>4);boolean forced=level.getForcedChunks().contains(chunk);
        level.setChunkForced(target.getX()>>4,target.getZ()>>4,true);level.getChunk(target);
        Runnable cleanup=()->{level.setBlock(target,old,3);if(!forced)level.setChunkForced(target.getX()>>4,target.getZ()>>4,false);server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();};
        var blueprint=PortableBlueprint.decode(PortableBlueprint.encode(source(1,new BlockPos(10000,50,10000),ModBlocks.ISOLATED_WIRE.get().defaultBlockState())));
        new Runnable(){int attempts;public void run(){try{
            if(++attempts>150)throw new IllegalArgumentException("Chunk unavailable");
            if(!NetworkRuntime.isChunkAvailable(level,chunk)){h.runAfterDelay(1,this::run);return;}
            level.setBlock(target,Blocks.AIR.defaultBlockState(),3);int next=data.getNextNetworkId();
            h.assertTrue(BlueprintService.pasteAll(player,blueprint,target,0,false).isEmpty(),"import does not compile on paste");
            h.assertTrue(data.getNextNetworkId()==next&&data.getNetworks().isEmpty(),"paste allocates no network records");
            h.assertTrue(level.getBlockState(target).is(ModBlocks.ISOLATED_WIRE.get()),"imported block placed");
            rejected(h,()->BlueprintService.pasteAll(player,blueprint,target,0,false),"occupied destination remains protected");
            cleanup.run();h.succeed();
        }catch(Throwable error){cleanup.run();h.fail(error.toString());}}}.run();
    }
    @GameTest(template="empty",batch="portable_import")
    public static void uploadRequiresHeldToolAndPublishesOnlyCompleteFile(GameTestHelper h) {
        var player=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"upload-test"));
        player.getAbilities().mayBuild=true;
        var stack=new net.minecraft.world.item.ItemStack(ModItems.CIRCUIT_COPIER.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        var bytes=PortableBlueprint.encode(source(1,BlockPos.ZERO,ModBlocks.BASIC_WIRE.get().defaultBlockState()));var token=UUID.randomUUID();
        BlueprintFileService.accept(player,new com.example.compiledcircuits.networking.BlueprintFilePartC2SPacket(token,0,bytes.length,bytes));
        h.assertTrue(BlueprintService.id(stack)==null,"unsolicited upload rejected");
        BlueprintFileService.request(player,new com.example.compiledcircuits.networking.BlueprintFileRequestC2SPacket(BlueprintFileService.LOAD,0,token));
        int split=bytes.length/2;
        BlueprintFileService.accept(player,new com.example.compiledcircuits.networking.BlueprintFilePartC2SPacket(token,0,bytes.length,Arrays.copyOf(bytes,split)));
        h.assertTrue(BlueprintService.id(stack)==null,"partial upload leaves copier unchanged");
        BlueprintFileService.accept(player,new com.example.compiledcircuits.networking.BlueprintFilePartC2SPacket(token,split,bytes.length,Arrays.copyOfRange(bytes,split,bytes.length)));
        var id=BlueprintService.id(stack);h.assertTrue(id!=null,"complete valid upload installs blueprint");
        h.assertTrue(BlueprintStorage.get(player.getServer()).get(id).uncompiled(),"uploaded copier contains raw blocks");
        token=UUID.randomUUID();BlueprintFileService.request(player,new com.example.compiledcircuits.networking.BlueprintFileRequestC2SPacket(BlueprintFileService.LOAD,0,token));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        BlueprintFileService.accept(player,new com.example.compiledcircuits.networking.BlueprintFilePartC2SPacket(token,0,bytes.length,bytes));
        h.assertTrue(id.equals(BlueprintService.id(stack)),"changed held tool cancels import");
        BlueprintFileService.forget(player);h.succeed();
    }
    @GameTest(template="empty",batch="portable_powered")
    public static void allCircuitBlocksExportWithHighStates(GameTestHelper h) {
        int count=0;
        for(var registered:ModBlocks.BLOCKS.getEntries()){
            var block=registered.get();if(!(block instanceof IWireConnectable))continue;
            var state=block.defaultBlockState();
            for(var property:state.getProperties())if(property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag)state=state.setValue(flag,true);
            if(block instanceof CrossingWireBlock)state=state.setValue(CrossingWireBlock.LANES[0],1).setValue(CrossingWireBlock.LANES[2],3);
            var type=block instanceof InputEndpointBlock?CircuitElementType.INPUT:block instanceof OutputEndpointBlock?CircuitElementType.OUTPUT
                :block instanceof GatePortBlock port?(port.isInput()?CircuitElementType.GATE_INPUT:CircuitElementType.GATE_OUTPUT)
                :block instanceof GateBodyBlock||block instanceof NotGateBlock||block instanceof DLatchBlock?CircuitElementType.GATE:CircuitElementType.WIRE;
            var entry=new CompiledCircuitElement(1,new BlockPos(9000,70,9000),type,CompiledBlockStateCodec.capture(state)).save();
            if(block instanceof IdWireBlock)entry.putString("wireId","17");
            if(block instanceof BundlePortBlock)entry.putString("channelId","17");
            var data=new CompoundTag();var list=new ListTag();list.add(entry);data.put("elements",list);data.putLong("origin",new BlockPos(9000,70,9000).asLong());
            data.putString("sourceDimension","minecraft:overworld");data.putInt("sourceNetwork",++count);
            var loaded=PortableBlueprint.decode(PortableBlueprint.encode(new CircuitBlueprint(data)));
            var actual=loaded.elements(BlockPos.ZERO,0).get(0).resolveState().state().orElseThrow();
            h.assertTrue(actual.is(block),"HIGH block exports: "+registered.getId());
            if(block instanceof SignalSwitchBlock)h.assertTrue(actual.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"enabled switch retained");
        }
        h.assertTrue(count>20,"all circuit block variants covered");h.succeed();
    }
    @GameTest(template="empty",batch="portable_ack")
    public static void bufferClearsOnlyAfterMatchingSuccessfulSave(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);var data=new NetworkSavedData();
        server.overworld().getDataStorage().set("compiledcircuits_networks",data);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"export-ack-test"));player.getAbilities().mayBuild=true;
        try{
            for(int id=1;id<=2;id++){
                var pos=h.absolutePos(new BlockPos(id,4,1));
                var element=new CompiledCircuitElement(1,pos,CircuitElementType.WIRE,CompiledBlockStateCodec.capture(ModBlocks.BASIC_WIRE.get().defaultBlockState()));
                data.addNetwork(new CompiledNetwork(id,"ack-test",0,level.dimension().location().toString(),List.of(element)));
            }
            for(int n=0;n<OperationLimits.REQUESTS_PER_WINDOW;n++)NetworkOperations.authorizeDamageResync(player);
            h.assertTrue(!NetworkOperations.authorizeDamageResync(player),"shared background quota exhausted");
            BlueprintFileService.request(player,new com.example.compiledcircuits.networking.BlueprintFileRequestC2SPacket(BlueprintFileService.ADD,1,UUID.randomUUID()));
            h.assertTrue(BlueprintFileService.buffer(player)!=null,"selected network added");
            var token=UUID.randomUUID();BlueprintFileService.request(player,new com.example.compiledcircuits.networking.BlueprintFileRequestC2SPacket(BlueprintFileService.SAVE,0,token));
            h.assertTrue(BlueprintFileService.buffer(player)!=null,"sending bytes alone does not clear buffer");
            BlueprintFileService.request(player,new com.example.compiledcircuits.networking.BlueprintFileRequestC2SPacket(BlueprintFileService.SAVED,0,UUID.randomUUID()));
            h.assertTrue(BlueprintFileService.buffer(player)!=null,"wrong acknowledgement ignored");
            BlueprintFileService.request(player,new com.example.compiledcircuits.networking.BlueprintFileRequestC2SPacket(BlueprintFileService.SAVED,0,token));
            h.assertTrue(BlueprintFileService.buffer(player)==null,"successful save clears buffer");
            BlueprintFileService.request(player,new com.example.compiledcircuits.networking.BlueprintFileRequestC2SPacket(BlueprintFileService.ADD,1,UUID.randomUUID()));
            token=UUID.randomUUID();BlueprintFileService.request(player,new com.example.compiledcircuits.networking.BlueprintFileRequestC2SPacket(BlueprintFileService.SAVE,0,token));
            BlueprintFileService.request(player,new com.example.compiledcircuits.networking.BlueprintFileRequestC2SPacket(BlueprintFileService.ADD,2,UUID.randomUUID()));
            BlueprintFileService.request(player,new com.example.compiledcircuits.networking.BlueprintFileRequestC2SPacket(BlueprintFileService.SAVED,0,token));
            h.assertTrue(BlueprintFileService.buffer(player).networkCount()==2,"late acknowledgement cannot discard newly added networks");
            h.succeed();
        }finally{NetworkOperations.forget(player);for(var network:new ArrayList<>(data.getNetworks()))data.removeNetwork(network.getId());server.overworld().getDataStorage().set("compiledcircuits_networks",original);NetworkIntegrityManager.clearPending();}
    }
}
