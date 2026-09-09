package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.IWireConnectable;
import com.example.compiledcircuits.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = CompiledCircuits.MOD_ID,
        value = Dist.CLIENT
)
public class ClientSelectionEvents {

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBrokenElementList.clear();
        ClientNetworkSelection.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() == Minecraft.getInstance().level) ClientNetworkSelection.clear();
    }

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event
    ) {

        if (!event.getLevel().isClientSide()) {
            return;
        }

        if (!event.getItemStack().is(ModItems.NETWORK_SELECTOR.get())) {
            return;
        }

        if (event.getEntity().isShiftKeyDown()) {

            ClientNetworkSelection.clear();

            Minecraft minecraft = Minecraft.getInstance();

            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("Network selection cleared."),
                        true
                );
            }

            return;
        }

        BlockPos clickedPos = event.getPos();
        BlockState clickedState =
                event.getLevel().getBlockState(clickedPos);

        if (!(clickedState.getBlock() instanceof IWireConnectable)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        ClientLevel clientLevel = minecraft.level;

        if (clientLevel == null) {
            return;
        }

        boolean success =
                ClientNetworkSelection.selectNetwork(
                        clientLevel,
                        clickedPos
                );

        if (minecraft.player != null) {

            if (success) {

                minecraft.player.displayClientMessage(
                        Component.literal(
                                "Selected network: "
                                        + ClientNetworkSelection.getWires().size()
                                        + " wires, "
                                        + ClientNetworkSelection.getInputs().size()
                                        + " inputs, "
                                        + ClientNetworkSelection.getOutputs().size()
                                        + " outputs"
                        ),
                        true
                );

            } else {

                minecraft.player.displayClientMessage(
                        Component.literal(
                                "Could not select network."
                        ),
                        true
                );
            }
        }
    }
}