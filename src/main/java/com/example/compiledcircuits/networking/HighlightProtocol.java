package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.network.OperationLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Separate selection stream, using the stage-03 bounded-delivery conventions. */
public final class HighlightProtocol {
    public static final UUID NO_CONTEXT=new UUID(0,0);
    public static final int IDS=OperationLimits.IDS, POSITIONS=OperationLimits.ELEMENTS;
    public static final int PART_BYTES=DamageProtocol.PART_BYTES, PART_POSITIONS=3000, PARTS=32, BATCH_BYTES=1024*1024;
    public static final int PEERS=64, PARTS_PER_TICK=8, PARTS_PER_PLAYER=2, CLIENT_STEPS=1024;
    public static final int IDLE_TICKS=200, LIFETIME_TICKS=20000, DIMENSION=256, MESSAGE=256;
    public enum State { READY, ERROR, INVALIDATED }
    public record Request(UUID context,long id,String dimension) {
        public Request { if(context==null||id<0||dimension==null||dimension.length()>DIMENSION
                ||!dimension.isEmpty()&&ResourceLocation.tryParse(dimension)==null)throw new IllegalArgumentException("Invalid highlight request"); }
        public boolean current(){return !context.equals(NO_CONTEXT)&&id>0&&!dimension.isEmpty();}
    }
    public static final Request LEGACY=new Request(NO_CONTEXT,0,"");
    private HighlightProtocol() {}
}
