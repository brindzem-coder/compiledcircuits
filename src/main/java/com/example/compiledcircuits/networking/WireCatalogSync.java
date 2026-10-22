package com.example.compiledcircuits.networking;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID)
public final class WireCatalogSync {
    private static final Map<UUID, Long> sent = new HashMap<>();
    public static void send(ServerPlayer player) {
        var data = WireCatalog.get(player.getServer()); var entries = data.colors().entrySet().iterator(); int part = 0;
        do {
            var batch = new LinkedHashMap<String, Integer>();
            while (batch.size() < 256 && entries.hasNext()) { var e = entries.next(); batch.put(e.getKey(), e.getValue()); }
            var locked = new HashSet<String>();
            for (String id : batch.keySet()) if (data.isColorLocked(id)) locked.add(id);
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WireCatalogS2CPacket(data.revision(), part++, !entries.hasNext(), batch, locked));
        } while (entries.hasNext());
        sent.put(player.getUUID(), data.revision());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) send(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) { sent.remove(p.getUUID()); WireToolService.forget(p); } }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { sent.clear(); WireToolService.clear(); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long revision = WireCatalog.get(event.getServer()).revision();
        for (var player : event.getServer().getPlayerList().getPlayers()) if (!Objects.equals(sent.get(player.getUUID()), revision)) send(player);
    }
}
