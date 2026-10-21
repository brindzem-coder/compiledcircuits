package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.example.compiledcircuits.networking.*;
import com.example.compiledcircuits.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, value = Dist.CLIENT)
public final class ClientBenchmarks {
    private static final Map<Integer, List<String>> reports = new LinkedHashMap<>();
    private static Object connection;
    private static long lastRequest;
    private static void context() {
        var current = Minecraft.getInstance().getConnection();
        if (current != connection) { reports.clear(); connection = current; lastRequest = 0; }
    }
    public static void accept(BenchmarkS2CPacket packet) {
        context(); reports.put(packet.id(), packet.lines());
        while (reports.size() > 128) reports.remove(reports.keySet().iterator().next());
    }
    public static List<String> lines(int id) {
        context();
        long now = System.nanoTime();
        if (connection != null && now - lastRequest > 1_000_000_000L) {
            lastRequest = now; ModNetworking.CHANNEL.sendToServer(new BenchmarkRequestC2SPacket(id));
        }
        return reports.getOrDefault(id, List.of("Loading measurements..."));
    }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        reports.clear(); connection = null; lastRequest = 0;
    }
    @SubscribeEvent public static void overlay(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui
                || !ClientNetworkSelection.isForLevel(mc.level)) return;
        if (!mc.player.getMainHandItem().is(ModItems.NETWORK_SELECTOR.get())
                && !mc.player.getOffhandItem().is(ModItems.NETWORK_SELECTOR.get())) return;
        Integer id = null;
        for (var positions : List.of(ClientNetworkSelection.getWires(), ClientNetworkSelection.getInputs(), ClientNetworkSelection.getOutputs())) {
            if (!positions.isEmpty()) { id = ClientCompiledElements.networkIdAt(positions.iterator().next()); break; }
        }
        if (id == null) return;
        var lines = lines(id); var g = event.getGuiGraphics(); int y = 8;
        for (int i = 0; i < Math.min(11, lines.size()); i++) {
            String text = mc.font.plainSubstrByWidth(lines.get(i), Math.max(40, mc.getWindow().getGuiScaledWidth() - 20));
            g.fill(6, y - 1, 10 + mc.font.width(text), y + 10, 0xB0000000);
            g.drawString(mc.font, text, 8, y, 0xFFFFFF); y += 11;
        }
    }
}
