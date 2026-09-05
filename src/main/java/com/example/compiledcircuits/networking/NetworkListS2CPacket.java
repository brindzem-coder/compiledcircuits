package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class NetworkListS2CPacket {

    public final List<Entry> entries;

    public final List<FolderEntry> folders;

    public NetworkListS2CPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public static void encode(NetworkListS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.entries.size());

        for (Entry entry : packet.entries) {
            buf.writeInt(entry.id);
            buf.writeUtf(entry.name);
            buf.writeInt(entry.folderId());
            buf.writeUtf(entry.dimension);
            buf.writeBoolean(entry.powered);
            buf.writeInt(entry.wires);
            buf.writeInt(entry.inputs);
            buf.writeInt(entry.outputs);
        }

        buf.writeInt(packet.folders.size());

        for (FolderEntry folder : packet.folders) {

            buf.writeInt(folder.id());
            buf.writeUtf(folder.name());
            buf.writeInt(folder.parentId());
        }
    }

    public static NetworkListS2CPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            entries.add(new Entry(
                    buf.readInt(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
            ));
            entries.add(
                    new Entry(
                            buf.readInt(),
                            buf.readUtf(),
                            buf.readInt(),
                            buf.readUtf(),
                            buf.readBoolean(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt()
                    )
            );
        }

        int folderCount =
                buf.readInt();

        List<FolderEntry> folders =
                new ArrayList<>();

        for (int i = 0;
             i < folderCount;
             i++) {

            folders.add(
                    new FolderEntry(
                            buf.readInt(),
                            buf.readUtf(),
                            buf.readInt()
                    )
            );
        }

        return new NetworkListS2CPacket(
                entries,
                folders
        );
    }

    public static void handle(
            NetworkListS2CPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT,
                        () -> () -> ClientPacketHandlers.openNetworkManager(packet)
                )
        );

        context.setPacketHandled(true);
    }

    public record Entry(
            int id,
            String name,
            int folderId,
            String dimension,
            boolean powered,
            int wires,
            int inputs,
            int outputs
    ) {
    }

    public NetworkListS2CPacket(
            List<Entry> entries,
            List<FolderEntry> folders
    ) {
        this.entries = entries;
        this.folders = folders;
    }

    public record FolderEntry(
            int id,
            String name,
            int parentId
    ) {
    }
}