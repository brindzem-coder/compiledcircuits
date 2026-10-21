package com.example.compiledcircuits.block;

import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;
import com.example.compiledcircuits.registry.ModBlocks;

/** Face states: 0 empty, 1 ordinary wire arm, 2 direct input, 3 direct output. */
public class NetworkWireBlock extends BasicWireBlock {
    public static final IntegerProperty[] FACES=java.util.Arrays.stream(Direction.values())
            .map(d->IntegerProperty.create("face_"+d.getName(),0,3)).toArray(IntegerProperty[]::new);
    public NetworkWireBlock(Properties properties) {
        super(properties,true);
        var state=defaultBlockState();for(var face:FACES)state=state.setValue(face,0);registerDefaultState(state);
    }
    public static int face(BlockState state,Direction side){return state.getValue(FACES[side.ordinal()]);}
    public static BlockState withFace(BlockState state,Direction side,int value){return state.setValue(FACES[side.ordinal()],value);}
    public static boolean hasPort(BlockState state,int kind){for(var d:Direction.values())if(face(state,d)==kind)return true;return false;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACES);}
    @Override public boolean canWireConnect(BlockState state,Direction side){return face(state,side)<2;}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context){
        return Block.updateFromNeighbourShapes(defaultBlockState(),context.getLevel(),context.getClickedPos());
    }
    @Override public BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos next){
        return face(state,side)>=2?state:withFace(state,side,canConnectTo(level,pos,side)?1:0);
    }
    private static final VoxelShape[] SHAPES=new VoxelShape[4096];
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        int index=0;for(var d:Direction.values())index|=face(state,d)<<(d.ordinal()*2);
        if(SHAPES[index]!=null)return SHAPES[index];
        var base=ModBlocks.BASIC_WIRE.get().defaultBlockState();
        for(var side:Direction.values())base=base.setValue(switch(side){case DOWN->DOWN;case UP->UP;case NORTH->NORTH;case SOUTH->SOUTH;case WEST->WEST;case EAST->EAST;},face(state,side)>0);
        var shape=base.getShape(level,pos,context);
        for(var d:Direction.values())if(face(state,d)>=2){
            double[] low={4.5,4.5,4.5},high={11.5,11.5,11.5};int axis=d.getAxis()==Direction.Axis.X?0:d.getAxis()==Direction.Axis.Y?1:2;
            boolean positive=d.getAxisDirection()==Direction.AxisDirection.POSITIVE;
            low[axis]=positive?9:0;high[axis]=positive?16:7;
            shape=Shapes.or(shape,Block.box(low[0],low[1],low[2],high[0],high[1],high[2]));
        }
        return SHAPES[index]=shape;
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation){var result=state;for(var d:Direction.values())result=withFace(result,rotation.rotate(d),face(state,d));return result;}
    @Override public BlockState mirror(BlockState state,Mirror mirror){var result=state;for(var d:Direction.values())result=withFace(result,mirror.mirror(d),face(state,d));return result;}
}
