package com.example.compiledcircuits.client;

import com.example.compiledcircuits.CompiledCircuits;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.List;

@Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, value = Dist.CLIENT)
public final class CircuitKeyMappings {
    private record CommandKey(KeyMapping key, String command) {}
    private static CommandKey binding(String command, int key, KeyModifier modifier) {
        return new CommandKey(new KeyMapping("key.compiledcircuits." + command.replace(' ', '_'),
                KeyConflictContext.IN_GAME, modifier, InputConstants.Type.KEYSYM, key,
                "key.categories.compiledcircuits"), "circuit " + command);
    }
    private static final List<CommandKey> COMMANDS = List.of(
            binding("compile", GLFW.GLFW_KEY_N, KeyModifier.NONE),
            binding("compile_named", GLFW.GLFW_KEY_N, KeyModifier.CONTROL),
            binding("decompile", GLFW.GLFW_KEY_U, KeyModifier.CONTROL),
            binding("gui", GLFW.GLFW_KEY_G, KeyModifier.NONE),
            binding("open_selected", GLFW.GLFW_KEY_H, KeyModifier.NONE),
            binding("rename", GLFW.GLFW_KEY_M, KeyModifier.NONE),
            binding("optimize", GLFW.GLFW_KEY_O, KeyModifier.NONE),
            binding("table create", GLFW.GLFW_KEY_T, KeyModifier.CONTROL),
            binding("table remove", GLFW.GLFW_KEY_T, KeyModifier.SHIFT),
            binding("table status", GLFW.GLFW_KEY_UNKNOWN, KeyModifier.NONE));

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        for (var command : COMMANDS) {
            boolean clicked = false;
            while (command.key().consumeClick()) clicked = true;
            if (clicked && mc.screen == null && mc.player != null && mc.level != null && mc.getConnection() != null)
                mc.getConnection().sendCommand(command.command());
        }
    }

    @Mod.EventBusSubscriber(modid = CompiledCircuits.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
            COMMANDS.forEach(command -> event.register(command.key()));
        }
    }
}
