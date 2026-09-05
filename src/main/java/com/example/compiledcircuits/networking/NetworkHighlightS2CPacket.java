package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.client.ClientPacketHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class NetworkHighlightS2CPacket {

    private final Set<BlockPos> wires;
    private final Set<BlockPos> inputs;
    private final Set<BlockPos> outputs;

    public NetworkHighlightS2CPacket(
            Set<BlockPos> wires,
            Set<BlockPos> inputs,
            Set<BlockPos> outputs
    ) {
        this.wires = wires;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public Set<BlockPos> getWires() {
        return wires;
    }

    public Set<BlockPos> getInputs() {
        return inputs;
    }

    public Set<BlockPos> getOutputs() {
        return outputs;
    }

    public static void encode(
            NetworkHighlightS2CPacket packet,
            FriendlyByteBuf buf
    ) {

        writePositions(
                buf,
                packet.wires
        );

        writePositions(
                buf,
                packet.inputs
        );

        writePositions(
                buf,
                packet.outputs
        );
    }

    public static NetworkHighlightS2CPacket decode(
            FriendlyByteBuf buf
    ) {

        Set<BlockPos> wires =
                readPositions(buf);

        Set<BlockPos> inputs =
                readPositions(buf);

        Set<BlockPos> outputs =
                readPositions(buf);

        return new NetworkHighlightS2CPacket(
                wires,
                inputs,
                outputs
        );
    }

    private static void writePositions(
            FriendlyByteBuf buf,
            Set<BlockPos> positions
    ) {

        buf.writeInt(
                positions.size()
        );

        for (BlockPos pos : positions) {
            buf.writeBlockPos(pos);
        }
    }

    private static Set<BlockPos> readPositions(
            FriendlyByteBuf buf
    ) {

        int size =
                buf.readInt();

        Set<BlockPos> result =
                new HashSet<>();

        for (int i = 0; i < size; i++) {
            result.add(
                    buf.readBlockPos()
            );
        }

        return result;
    }

    public static void handle(
            NetworkHighlightS2CPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {

        NetworkEvent.Context context =
                contextSupplier.get();

        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT,
                        () -> () ->
                                ClientPacketHandlers.handleHighlight(
                                        packet
                                )
                )
        );

        context.setPacketHandled(true);
    }
}