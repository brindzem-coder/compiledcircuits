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

    public static final int MAX_RECORDS=1024, MAX_BYTES=1024*1024, MAX_TEXT=256;
    public final boolean available;
    public static NetworkListS2CPacket unavailable(){return new NetworkListS2CPacket(List.of(),List.of(),false);}
    public static boolean fits(List<Entry> entries,List<FolderEntry> folders){
        if((long)entries.size()+folders.size()>MAX_RECORDS)return false;
        long bytes=16;
        for(var e:entries){if(e.name.length()>MAX_TEXT||e.dimension.length()>MAX_TEXT)return false;bytes+=32+3L*(e.name.length()+e.dimension.length());}
        for(var f:folders){if(f.name.length()>MAX_TEXT)return false;bytes+=16+3L*f.name.length();}
        return bytes<=MAX_BYTES;
    }
    public final List<Entry> entries;

    public final List<FolderEntry> folders;


    public static void encode(NetworkListS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.available);
        buf.writeInt(packet.entries.size());

        for (Entry entry : packet.entries) {
            buf.writeInt(entry.id);
            buf.writeUtf(entry.name,MAX_TEXT);
            buf.writeInt(entry.folderId());
            buf.writeUtf(entry.dimension,MAX_TEXT);
            buf.writeBoolean(entry.powered);
            buf.writeInt(entry.wires);
            buf.writeInt(entry.inputs);
            buf.writeInt(entry.outputs);
        }

        buf.writeInt(packet.folders.size());

        for (FolderEntry folder : packet.folders) {

            buf.writeInt(folder.id());
            buf.writeUtf(folder.name(),MAX_TEXT);
            buf.writeInt(folder.parentId());
        }
    }

    public static NetworkListS2CPacket decode(FriendlyByteBuf buf) {
        if(buf.readableBytes()>MAX_BYTES+4)throw new IllegalArgumentException("Oversized network list");
        boolean available=buf.readBoolean();int size=buf.readInt();
        if(size<0||size>MAX_RECORDS||size>buf.readableBytes()/23)throw new IllegalArgumentException("Invalid network count");
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            entries.add(
                    new Entry(
                            buf.readInt(),
                            buf.readUtf(MAX_TEXT),
                            buf.readInt(),
                            buf.readUtf(MAX_TEXT),
                            buf.readBoolean(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt()
                    )
            );
        }

        int folderCount =
                buf.readInt();

        if(folderCount<0||folderCount>MAX_RECORDS-size||folderCount>buf.readableBytes()/9)throw new IllegalArgumentException("Invalid folder count");
        List<FolderEntry> folders = new ArrayList<>();

        for (int i = 0;
             i < folderCount;
             i++) {

            folders.add(
                    new FolderEntry(
                            buf.readInt(),
                            buf.readUtf(MAX_TEXT),
                            buf.readInt()
                    )
            );
        }

        return new NetworkListS2CPacket(
                entries, folders, available
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
                        () -> () -> ClientPacketHandlers.openNetworkManager(packet, context.getNetworkManager())
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
        this(entries,folders,true);
    }
    private NetworkListS2CPacket(List<Entry> entries,List<FolderEntry> folders,boolean available){
        if(!fits(entries,folders)||!available&&(!entries.isEmpty()||!folders.isEmpty()))throw new IllegalArgumentException("Invalid bounded network list");
        this.entries=List.copyOf(entries);this.folders=List.copyOf(folders);this.available=available;
    }

    public record FolderEntry(
            int id,
            String name,
            int parentId
    ) {
    }
}