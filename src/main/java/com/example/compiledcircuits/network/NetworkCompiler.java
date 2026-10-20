package com.example.compiledcircuits.network;
import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
public final class NetworkCompiler {
    private NetworkCompiler() {}
    public static boolean compileSelected(ServerPlayer player, String requestedName) {
        long started = PerformanceDiagnostics.begin(); PerformanceDiagnostics.add("compile.calls",1);
        try {
            if (player == null) return false;
            var source = player.createCommandSourceStack();
            return NetworkOperations.reply(source, NetworkOperations.execute(source, NetworkOperations.Action.COMPILE, List.of(),0,requestedName)) == 1;
        } finally { PerformanceDiagnostics.elapsed("compile",started); }
    }
}
