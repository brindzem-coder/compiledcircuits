package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class EndpointWireShapeGameTests {
    @GameTest(template="empty",batch="endpoint_wire_shapes")
    public static void armsSurviveNetworkPortConversion(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(3,3,3));
        for(var endpoint:java.util.List.of(ModBlocks.INPUT_ENDPOINT.get(),ModBlocks.OUTPUT_ENDPOINT.get())) {
            level.setBlock(pos,endpoint.defaultBlockState(),3);
            for(var side:Direction.values()) {
                var next=pos.relative(side);
                level.setBlock(next,ModBlocks.BASIC_WIRE.get().defaultBlockState(),3);
                h.assertTrue(level.getBlockState(pos).getValue(RedstoneEndpointBlock.connectionProperty(side)),"bare wire has an endpoint arm");
                var expected=level.getBlockState(pos).getShape(level,pos,CollisionContext.empty()).bounds();
                for(int kind:new int[]{2,3}) {
                    // The port is on the far face; the endpoint still touches an ordinary wire face.
                    level.setBlock(next,NetworkWireBlock.withFace(ModBlocks.NETWORK_WIRE.get().defaultBlockState(),side,kind),3);
                    h.assertTrue(level.getBlockState(pos).getValue(RedstoneEndpointBlock.connectionProperty(side)),"network wire retains endpoint arm on every axis");
                    h.assertTrue(level.getBlockState(pos).getShape(level,pos,CollisionContext.empty()).bounds().equals(expected),"outline still reaches the wire");
                    h.assertTrue(CircuitConnections.connects(level,pos,level.getBlockState(pos),level.getBlockState(next),side),"ordinary face remains electrically connected");
                    // An actual network boundary must remain disconnected, visually and structurally.
                    level.setBlock(next,NetworkWireBlock.withFace(ModBlocks.NETWORK_WIRE.get().defaultBlockState(),side.getOpposite(),kind),3);
                    h.assertTrue(!level.getBlockState(pos).getValue(RedstoneEndpointBlock.connectionProperty(side)),"facing network boundary has no ordinary arm");
                    h.assertTrue(!CircuitConnections.connects(level,pos,level.getBlockState(pos),level.getBlockState(next),side),"network boundary remains intact");
                }
                level.setBlock(next,Blocks.AIR.defaultBlockState(),3);
                h.assertTrue(!level.getBlockState(pos).getValue(RedstoneEndpointBlock.connectionProperty(side)),"removed wire removes arm");
            }
        }
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        h.succeed();
    }
}
