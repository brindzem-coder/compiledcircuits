package com.example.compiledcircuits.event;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.IWireConnectable;
import com.example.compiledcircuits.network.NetworkSelectionData;
import com.example.compiledcircuits.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = CompiledCircuits.MOD_ID
)
public class ServerSelectionEvents {

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event
    ) {

        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!event.getItemStack().is(ModItems.NETWORK_SELECTOR.get())) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.isShiftKeyDown()) {

            NetworkSelectionData.clear(player);
            return;
        }

        BlockPos pos = event.getPos();

        BlockState state =
                event.getLevel().getBlockState(pos);

        if (!(state.getBlock() instanceof IWireConnectable)) {
            return;
        }

        NetworkSelectionData.set(player, pos);
    }
}