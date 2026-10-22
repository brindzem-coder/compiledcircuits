package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits") @PrefixGameTestTemplate(false)
public class NetworkTeleportGameTests {
    @GameTest(template="empty",batch="network_teleport")
    public static void landingAvoidsSolidsHazardsAndUnsupportedAir(GameTestHelper h){
        var level=h.getLevel();var feet=h.absolutePos(new BlockPos(2,4,2));
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"teleport-safe"));
        var positions=List.of(feet.below(),feet,feet.above());var old=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();positions.forEach(p->old.put(p,level.getBlockState(p)));
        try{
            for(var p:positions)level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(!NetworkTeleport.safe(level,player,feet,true),"survival cannot land over a drop");
            h.assertTrue(NetworkTeleport.safe(level,player,feet,false),"flight can land in clear air");
            level.setBlock(feet.below(),Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(NetworkTeleport.safe(level,player,feet,true),"clear standing space on solid ground is valid");
            level.setBlock(feet.above(),Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(!NetworkTeleport.safe(level,player,feet,true),"head collision is rejected");
            level.setBlock(feet.above(),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(feet.below(),Blocks.MAGMA_BLOCK.defaultBlockState(),3);
            h.assertTrue(!NetworkTeleport.safe(level,player,feet,true),"dangerous floor rejected");
            level.setBlock(feet.below(),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(feet,Blocks.LAVA.defaultBlockState(),3);
            h.assertTrue(!NetworkTeleport.safe(level,player,feet,false),"lava rejected even with flight");
        }finally{old.forEach((p,s)->level.setBlock(p,s,3));}
        h.succeed();
    }
    @GameTest(template="empty",batch="network_teleport_validation")
    public static void serverRequiresExactlyOneExistingNetwork(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();var original=NetworkSavedData.get(server);server.overworld().getDataStorage().set("compiledcircuits_networks",new NetworkSavedData());
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"teleport-validation"));player.getAbilities().mayBuild=true;var start=player.position();
        try{
            h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.TELEPORT,List.of(),0,"").code()==NetworkOperations.Code.INVALID_ARGUMENT,"empty selection rejected");
            h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.TELEPORT,List.of(1,2),0,"").code()==NetworkOperations.Code.INVALID_ARGUMENT,"multiple targets rejected by server");
            h.assertTrue(NetworkOperations.execute(player.createCommandSourceStack(),NetworkOperations.Action.TELEPORT,List.of(1),0,"").code()==NetworkOperations.Code.NOT_FOUND,"deleted network rejected");
            h.assertTrue(player.position().equals(start),"rejected requests do not move player");
        }finally{NetworkOperations.forget(player);server.overworld().getDataStorage().set("compiledcircuits_networks",original);}
        h.succeed();
    }
}
