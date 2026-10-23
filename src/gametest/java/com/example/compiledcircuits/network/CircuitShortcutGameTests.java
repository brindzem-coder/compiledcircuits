package com.example.compiledcircuits.network;

import com.example.compiledcircuits.command.CircuitCommands;
import com.example.compiledcircuits.networking.OpenRenameS2CPacket;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.*;
import io.netty.buffer.Unpooled;
import java.util.List;

@GameTestHolder("compiledcircuits") @PrefixGameTestTemplate(false)
public class CircuitShortcutGameTests {
    @GameTest(template="empty", batch="circuit_shortcuts")
    public static void shortcutCommandsAndLegacyRenameRemainExecutable(GameTestHelper h) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        CircuitCommands.register(dispatcher);
        var source = h.getLevel().getServer().createCommandSourceStack();
        for (var suffix : List.of("compile", "compile_named", "decompile", "gui", "open_selected",
                "rename", "optimize", "table create", "table remove", "table status", "rename 1 New circuit name")) {
            var parsed = dispatcher.parse("circuit " + suffix, source);
            h.assertTrue(!parsed.getReader().canRead() && parsed.getContext().getCommand() != null,
                    "Shortcut must resolve to an executable command: " + suffix);
        }
        h.succeed();
    }
    @GameTest(template="empty", batch="circuit_shortcuts")
    public static void renameDialogPacketPreservesTargetAndName(GameTestHelper h) {
        var original = new OpenRenameS2CPacket(123, "Мережа з пробілами 123");
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            OpenRenameS2CPacket.encode(original, buf);
            h.assertTrue(original.equals(OpenRenameS2CPacket.decode(buf)), "Dialog preserves exact ID and Unicode name");
        } finally { buf.release(); }
        h.succeed();
    }
}
