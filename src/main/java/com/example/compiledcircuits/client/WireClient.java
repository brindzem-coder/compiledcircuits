package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.block.IdWireEntity;
import com.example.compiledcircuits.item.IdWireItem;
import com.example.compiledcircuits.network.WireIds;
import com.example.compiledcircuits.networking.*;
import com.example.compiledcircuits.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, value = Dist.CLIENT)
public final class WireClient {
    private static volatile Map<String, Integer> colors = Map.of();
    private static final Map<String, Integer> pending = new HashMap<>();
    private static Set<String> locked = Set.of();
    private static final Set<String> pendingLocked = new HashSet<>();
    private static final Set<IdWireEntity> loaded = Collections.newSetFromMap(new WeakHashMap<>());
    private static long revision;
    private static int nextPart;
    public static Map<String, Integer> colors() { return colors; }
    public static boolean isColorLocked(String id) { return id.equals("0") || locked.contains(id); }
    public static int color(String id, int fallback) { return id.equals("0") ? WireIds.RED : colors.getOrDefault(id, fallback); }
    public static void track(IdWireEntity wire) { loaded.add(wire); }
    public static void identityChanged(IdWireEntity wire) {
        track(wire);
        // BE updates can change connectivity without changing the block's face properties.
        ClientHoveredCircuit.clear();
    }
    public static void open(WireEditorS2CPacket packet) { Minecraft.getInstance().setScreen(new WireEditorScreen(packet)); }
    public static void accept(WireCatalogS2CPacket packet) {
        if (packet.part() == 0) { pending.clear(); pendingLocked.clear(); revision = packet.revision(); nextPart = 0; }
        if (packet.revision() != revision || packet.part() != nextPart++) { pending.clear(); pendingLocked.clear(); return; }
        pending.putAll(packet.colors());
        pendingLocked.addAll(packet.locked());
        if (!packet.last()) return;
        var old = colors; colors = Map.copyOf(pending); pending.clear();
        locked = Set.copyOf(pendingLocked); pendingLocked.clear();
        var level = Minecraft.getInstance().level;
        loaded.removeIf(wire -> wire.isRemoved() || wire.getLevel() != level);
        if (level != null) for (var wire : loaded) if (!Objects.equals(old.get(wire.wireId()), colors.get(wire.wireId()))) {
            var pos = wire.getBlockPos(); level.setBlocksDirty(pos, wire.getBlockState(), wire.getBlockState());
        }
        if (Minecraft.getInstance().screen instanceof WireEditorScreen screen) screen.catalogChanged();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { colors = Map.of(); locked = Set.of(); pending.clear(); pendingLocked.clear(); loaded.clear(); nextPart = 0; }
    @Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Colors {
        @SubscribeEvent public static void blocks(RegisterColorHandlersEvent.Block event) {
            event.register((state, level, pos, tint) -> level != null && pos != null && level.getBlockEntity(pos) instanceof IdWireEntity wire ? color(wire.wireId(), wire.color()) : WireIds.RED, ModBlocks.ID_WIRE.get());
        }
        @SubscribeEvent public static void items(RegisterColorHandlersEvent.Item event) {
            // Only the insulation is tinted; the second layer keeps copper and the ID tag neutral.
            event.register((stack, tint) -> tint == 0 ? color(IdWireItem.id(stack), IdWireItem.color(stack)) : -1, ModItems.ID_WIRE.get());
        }
    }
}
