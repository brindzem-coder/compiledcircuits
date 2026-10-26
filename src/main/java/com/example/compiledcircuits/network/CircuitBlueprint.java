package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.*;

/** Immutable saved topology. Element IDs remain stable across every transform. */
public final class CircuitBlueprint {
    public static final int MAX_ELEMENTS = 4096;
    private final CompoundTag data;
    public CircuitBlueprint(CompoundTag data) {
        this.data = data.copy(); this.data.remove("blueprintId");
        var entries = data.getList("elements", Tag.TAG_COMPOUND);
        if (entries.isEmpty() || entries.size() > MAX_ELEMENTS) throw new IllegalArgumentException("Blueprint limit: 1–4096 blocks");
        Map<BlockPos,Integer> positions=new HashMap<>();
        for (var tag : entries) {
            var e = CompiledCircuitElement.load((CompoundTag)tag);
            int used=positions.getOrDefault(e.getPos(),0),mask=Crossings.mask(e);if((used&mask)!=0)throw new IllegalArgumentException("Duplicate blueprint position");positions.put(e.getPos(),used|mask);
            var relative=e.getPos().subtract(origin());
            if(Math.abs((long)relative.getX())>256||Math.abs((long)relative.getY())>256||Math.abs((long)relative.getZ())>256)throw new IllegalArgumentException("Blueprint size limit: 256 blocks per axis");
            if (e.resolveState().status() != CompiledBlockStateCodec.Status.EXACT) throw new IllegalArgumentException("Blueprint contains an unresolved block");
        }
    }
    public CompoundTag save() { return data.copy(); }
    public long estimatedBytes(){if(data.contains("networks"))return parts().stream().mapToLong(CircuitBlueprint::estimatedBytes).sum()+(long)data.getList("elements",Tag.TAG_COMPOUND).size()*2048;return (long)data.getList("elements",Tag.TAG_COMPOUND).size()*2048+data.getCompound("table").getLongArray("rows").length*8L;}
    private List<CircuitBlueprint> parts() {
        if (!data.contains("networks")) return List.of(this);
        return data.getList("networks",Tag.TAG_COMPOUND).stream().map(t->new CircuitBlueprint((CompoundTag)t)).toList();
    }
    public int networkCount() { return parts().size(); }
    public CircuitBlueprint append(CircuitBlueprint next) {
        if (data.getString("sourceDimension").isEmpty() || !data.getString("sourceDimension").equals(next.data.getString("sourceDimension")))
            throw new IllegalArgumentException("Copies must come from the same dimension; start a new copy with Shift+right-click");
        var parts=new ArrayList<>(parts());
        if(parts.stream().anyMatch(p->p.data.getInt("sourceNetwork")==next.data.getInt("sourceNetwork"))) return this;
        parts.addAll(next.parts());
        var group=new CompoundTag(); var networks=new ListTag(); var elements=new ListTag();
        for(var part:parts) { networks.add(part.save()); part.data.getList("elements",Tag.TAG_COMPOUND).forEach(t->elements.add(t.copy())); }
        group.put("networks",networks); group.put("elements",elements); group.putLong("origin",origin().asLong());
        group.putString("sourceDimension",data.getString("sourceDimension")); group.putString("name",parts.size()+" networks");
        return new CircuitBlueprint(group);
    }
    public List<CompiledNetwork> networks(int firstId,String dimension,BlockPos anchor,int turns,boolean mirrored) { return networks(firstId,dimension,anchor,turns,mirrored,false); }
    public List<CompiledNetwork> networks(int firstId,String dimension,BlockPos anchor,int turns,boolean mirrored,boolean mirroredZ) {
        var result=new ArrayList<CompiledNetwork>();
        for(var part:parts()) {
            if((long)firstId+result.size()>Integer.MAX_VALUE)throw new IllegalArgumentException("Network ID space exhausted");
            var tag=part.save(); tag.putLong("origin",origin().asLong());
            result.add(new CircuitBlueprint(tag).network(firstId+result.size(),dimension,anchor,turns,mirrored,mirroredZ));
        }
        return result;
    }
    public String name() { return data.getString("name"); }
    public BlockPos origin() { return BlockPos.of(data.getLong("origin")); }
    public static CircuitBlueprint capture(CompiledNetwork network, ServerLevel level) {
        if(network.getElements().size()>MAX_ELEMENTS)throw new IllegalArgumentException("Blueprint limit: 4096 blocks");
        var data = new CompoundTag(); var list = new ListTag();
        var min = network.getElements().stream().map(CompiledCircuitElement::getPos).reduce((a,b)->new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()))).orElseThrow();
        for (var e : network.getElements()) {
            if(e.resolveState().status()!=CompiledBlockStateCodec.Status.EXACT)throw new IllegalArgumentException("Cannot copy an unresolved saved block");
            var tag = e.save();
            // Capture live switches when available; missing blocks use the saved structure.
            if (level.hasChunkAt(e.getPos())) {
                var actual = level.getBlockState(e.getPos());
                if (actual.getBlock() instanceof SignalSwitchBlock && actual.is(e.resolveState().state().orElseThrow().getBlock()))
                    tag.merge(CompiledBlockStateCodec.write(CompiledBlockStateCodec.capture(actual)));
            }
            list.add(tag);
        }
        data.putString("sourceDimension",network.getDimension()); data.putInt("sourceNetwork",network.getId());
        data.put("elements", list); data.putLong("origin",min.asLong()); data.putString("name",network.getName());
        data.putBoolean("optimized",network.isOptimized());
        if (network.networkTable()!=null) data.put("table", network.networkTable().save());
        return new CircuitBlueprint(data);
    }
    public static BlockPos rotate(BlockPos p, int turns) {
        return switch(Math.floorMod(turns,4)) {
            case 1 -> new BlockPos(-p.getZ(),p.getY(),p.getX());
            case 2 -> new BlockPos(-p.getX(),p.getY(),-p.getZ());
            case 3 -> new BlockPos(p.getZ(),p.getY(),-p.getX()); default -> p;
        };
    }
    public static Rotation rotation(int turns) { return Rotation.values()[Math.floorMod(turns,4)]; }
    public BlockPos position(BlockPos p, BlockPos anchor, int turns) { return position(p,anchor,turns,false); }
    public BlockPos position(BlockPos p,BlockPos anchor,int turns,boolean mirrored) { return position(p,anchor,turns,mirrored,false); }
    public BlockPos position(BlockPos p,BlockPos anchor,int turns,boolean mirrored,boolean mirroredZ) {
        var relative=p.subtract(origin());
        if(mirrored)relative=new BlockPos(-relative.getX(),relative.getY(),relative.getZ());
        if(mirroredZ)relative=new BlockPos(relative.getX(),relative.getY(),-relative.getZ());
        return rotate(relative,turns).offset(anchor);
    }
    public static Direction transformSide(Direction side,int turns,boolean mirrored) { return transformSide(side,turns,mirrored,false); }
    public static Direction transformSide(Direction side,int turns,boolean mirrored,boolean mirroredZ) {
        if(mirrored)side=net.minecraft.world.level.block.Mirror.FRONT_BACK.mirror(side);
        if(mirroredZ)side=net.minecraft.world.level.block.Mirror.LEFT_RIGHT.mirror(side);
        return rotation(turns).rotate(side);
    }
    public static BlockState transformState(BlockState state,int turns,boolean mirrored) { return transformState(state,turns,mirrored,false); }
    public static BlockState transformState(BlockState state,int turns,boolean mirrored,boolean mirroredZ) {
        for(var mirror:net.minecraft.world.level.block.Mirror.values()) {
            if(mirror==net.minecraft.world.level.block.Mirror.NONE || mirror==net.minecraft.world.level.block.Mirror.FRONT_BACK&&!mirrored || mirror==net.minecraft.world.level.block.Mirror.LEFT_RIGHT&&!mirroredZ)continue;
            var original=state;
            state=state.mirror(mirror);
            if(original.getBlock() instanceof EndpointBlock && !(original.getBlock() instanceof DLatchBlock))
                state=state.setValue(EndpointBlock.FACING,mirror.mirror(original.getValue(EndpointBlock.FACING)));
            if(original.getBlock() instanceof BasicWireBlock && !(original.getBlock() instanceof NetworkWireBlock))
                for(var side:Direction.values())state=state.setValue(wireProperty(mirror.mirror(side)),original.getValue(wireProperty(side)));
        }
        return rotateState(state,turns);
    }
    public static BlockState rotateState(BlockState state, int turns) {
        var rotation=rotation(turns); var result=state.rotate(rotation);
        // Endpoints and basic wires have no vanilla rotation override.
        if (state.getBlock() instanceof EndpointBlock && !(state.getBlock() instanceof DLatchBlock))
            result=result.setValue(EndpointBlock.FACING,rotation.rotate(state.getValue(EndpointBlock.FACING)));
        if (state.getBlock() instanceof BasicWireBlock && !(state.getBlock() instanceof NetworkWireBlock)) {
            for (var side:Direction.values()) result=result.setValue(wireProperty(rotation.rotate(side)),state.getValue(wireProperty(side)));
        }
        if(result.getBlock() instanceof SignalLampBlock) result=result.setValue(BlockStateProperties.LIT,false);
        return result;
    }
    private static net.minecraft.world.level.block.state.properties.BooleanProperty wireProperty(Direction side) {
        return switch(side){case DOWN->BasicWireBlock.DOWN;case UP->BasicWireBlock.UP;case NORTH->BasicWireBlock.NORTH;case SOUTH->BasicWireBlock.SOUTH;case WEST->BasicWireBlock.WEST;case EAST->BasicWireBlock.EAST;};
    }
    public List<CompiledCircuitElement> elements(BlockPos anchor,int turns) { return elements(anchor,turns,false); }
    public List<CompiledCircuitElement> elements(BlockPos anchor,int turns,boolean mirrored) { return elements(anchor,turns,mirrored,false); }
    public List<CompiledCircuitElement> elements(BlockPos anchor,int turns,boolean mirrored,boolean mirroredZ) {
        var result=new ArrayList<CompiledCircuitElement>();
        for(var raw:data.getList("elements",Tag.TAG_COMPOUND)) {
            var old=CompiledCircuitElement.load((CompoundTag)raw);var tag=old.save();
            tag.putLong("pos",position(old.getPos(),anchor,turns,mirrored,mirroredZ).asLong());
            tag.merge(CompiledBlockStateCodec.write(CompiledBlockStateCodec.capture(transformState(old.resolveState().state().orElseThrow(),turns,mirrored,mirroredZ))));
            if(!old.bundleFaces().isEmpty()) {
                Map<Direction,BundleWireEntity.Port> faces=new EnumMap<>(Direction.class);
                BundleWireEntity.decode(old.bundleFaces()).forEach((side,port)->faces.put(transformSide(side,turns,mirrored,mirroredZ),port));
                tag.put("bundleFaces",BundleWireEntity.encode(faces));
            }
            if(!old.crossingLanes().isEmpty()){
                var lanes=new CompoundTag();for(var axis:Direction.Axis.values())if(old.crossingLanes().contains(axis.getName()))
                    lanes.put(transformSide(Direction.get(Direction.AxisDirection.POSITIVE,axis),turns,mirrored,mirroredZ).getAxis().getName(),old.crossingLanes().getCompound(axis.getName()));
                tag.put("crossingLanes",lanes);
            }
            result.add(CompiledCircuitElement.load(tag));
        }
        return result;
    }
    CompiledNetwork network(int id,String dimension,BlockPos anchor,int turns) { return network(id,dimension,anchor,turns,false); }
    CompiledNetwork network(int id,String dimension,BlockPos anchor,int turns,boolean mirrored) { return network(id,dimension,anchor,turns,mirrored,false); }
    CompiledNetwork network(int id,String dimension,BlockPos anchor,int turns,boolean mirrored,boolean mirroredZ) {
        if(data.contains("networks"))throw new IllegalArgumentException("Use grouped placement for multiple networks");
        var elements=elements(anchor,turns,mirrored,mirroredZ);
        var network=new CompiledNetwork(id,name()+" (copy)",0,dimension,elements);
        Map<BlockPos,BlockState> states=new HashMap<>();elements.forEach(e->states.put(e.getPos(),e.resolveState().state().orElseThrow()));
        network.prepareLogic(states);
        if(data.contains("table")) {
            var graph=LogicProgram.compileFaces(states,network.wireIds(),network.bundleFaces());
            network.setNetworkTable(remap(NetworkTruthTable.load(data.getCompound("table")),graph,anchor,turns,mirrored,mirroredZ));
        }
        if(data.getBoolean("optimized"))network.enableOptimization();
        return network;
    }
    private NetworkTruthTable remap(NetworkTruthTable old,LogicProgram graph,BlockPos anchor,int turns,boolean mirrored,boolean mirroredZ) {
        var inputs=graph.benchmarkInputs();var outputs=graph.tableOutputs();var faceInputs=graph.tableFaceInputs();var faceOutputs=graph.tableFaceOutputs();
        List<Object> mappedInputs=new ArrayList<>(),mappedOutputs=new ArrayList<>(),newInputs=new ArrayList<>(),newOutputs=new ArrayList<>();
        old.inputs.forEach(p->mappedInputs.add(position(p,anchor,turns,mirrored,mirroredZ)));
        old.faceInputs.forEach(f->mappedInputs.add(new BundleFace(position(f.pos(),anchor,turns,mirrored,mirroredZ),transformSide(f.side(),turns,mirrored,mirroredZ))));
        old.outputs.forEach(p->mappedOutputs.add(position(p,anchor,turns,mirrored,mirroredZ)));
        old.faceOutputs.forEach(f->mappedOutputs.add(new BundleFace(position(f.pos(),anchor,turns,mirrored,mirroredZ),transformSide(f.side(),turns,mirrored,mirroredZ))));
        newInputs.addAll(inputs);newInputs.addAll(faceInputs);newOutputs.addAll(outputs);newOutputs.addAll(faceOutputs);
        if(!new HashSet<>(mappedInputs).equals(new HashSet<>(newInputs))||!new HashSet<>(mappedOutputs).equals(new HashSet<>(newOutputs)))throw new IllegalArgumentException("Copied table terminals do not match");
        int[] in=newInputs.stream().mapToInt(mappedInputs::indexOf).toArray(),out=newOutputs.stream().mapToInt(mappedOutputs::indexOf).toArray();
        int words=(out.length+63)/64;long[] rows=new long[(1<<in.length)*words];
        for(int key=0;key<(1<<in.length);key++) {
            int oldKey=0;for(int bit=0;bit<in.length;bit++)if((key&(1<<bit))!=0)oldKey|=1<<in[bit];
            for(int bit=0;bit<out.length;bit++)if(old.output(oldKey,out[bit]))rows[key*words+bit/64]|=1L<<(bit%64);
        }
        return new NetworkTruthTable(graph.graphSignature(),inputs,outputs,faceInputs,faceOutputs,rows);
    }
}
