package com.example.compiledcircuits.block;
import com.example.compiledcircuits.network.*;
import com.example.compiledcircuits.registry.ModBlockEntities;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class CrossingWireEntity extends BlockEntity {
    private CompoundTag lanes=new CompoundTag();
    public CrossingWireEntity(BlockPos p,BlockState s){super(ModBlockEntities.CROSSING_WIRE.get(),p,s);}
    public String id(Direction.Axis a){return lanes.contains(a.getName())?lanes.getCompound(a.getName()).getString("id"):"0";}
    public int color(Direction.Axis a){int c=lanes.getCompound(a.getName()).getInt("color");return level instanceof ServerLevel s?WireCatalog.get(s.getServer()).color(id(a),c):c;}
    public CompoundTag snapshot(int mask){var out=new CompoundTag();for(var a:Direction.Axis.values())if((mask&(1<<a.ordinal()))!=0&&lanes.contains(a.getName()))out.put(a.getName(),lanes.getCompound(a.getName()).copy());return out;}
    public void configure(Direction.Axis a,String id,int rgb){var t=new CompoundTag();t.putString("id",WireIds.normalize(id));t.putInt("color",rgb&0xffffff);lanes.put(a.getName(),t);changed();}
    public void restore(CompoundTag t){lanes=t.copy();changed();}
    private void changed(){setChanged();if(level instanceof ServerLevel s){CompilationJobs.blockChanged(s,worldPosition);NetworkIntegrityManager.scheduleCheck(s,worldPosition);s.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}}
    @Override public void load(CompoundTag t){super.load(t);lanes=t.getCompound("lanes").copy();}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);t.put("lanes",lanes.copy());}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
