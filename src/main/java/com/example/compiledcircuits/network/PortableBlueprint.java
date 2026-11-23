package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.IWireConnectable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import java.io.*;

/** Versioned, bounded file format containing structure and port settings, never executable logic. */
public final class PortableBlueprint {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final int PART_BYTES = 24 * 1024;
    private PortableBlueprint() {}

    public static CircuitBlueprint uncompiled(CircuitBlueprint source) {
        var tag = new CompoundTag();
        var list = new ListTag();
        int id = 0;
        for (var element : source.elements(BlockPos.ZERO, 0)) {
            var state = element.resolveState().state().orElseThrow();
            if (!element.getBlockId().startsWith("compiledcircuits:") || !(state.getBlock() instanceof IWireConnectable))
                throw new IllegalArgumentException("File contains a non-circuit block");
            var saved = element.save(); saved.putInt("id", ++id); list.add(saved);
        }
        tag.put("elements", list); tag.putLong("origin", BlockPos.ZERO.asLong());
        tag.putString("name", source.name().isBlank() ? "Imported circuit" : source.name().substring(0, Math.min(128, source.name().length())));
        tag.putBoolean("uncompiled", true);
        return new CircuitBlueprint(tag);
    }

    public static byte[] encode(CircuitBlueprint source) {
        try {
            var root = new CompoundTag(); root.putInt("formatVersion", 1);
            root.put("blueprint", uncompiled(source).save());
            var bytes = new ByteArrayOutputStream();
            NbtIo.write(root, new DataOutputStream(bytes));
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Blueprint file exceeds 4 MiB");
            return bytes.toByteArray();
        } catch (IOException error) { throw new IllegalArgumentException("Could not encode blueprint", error); }
    }

    public static CircuitBlueprint decode(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid blueprint file size");
        try {
            var input = new DataInputStream(new ByteArrayInputStream(bytes));
            var root = NbtIo.read(input, new NbtAccounter(16L * 1024 * 1024));
            if (input.available() != 0 || root.getInt("formatVersion") != 1 || !root.contains("blueprint", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Unsupported blueprint file format");
            // Ignore all compiled-network/runtime fields supplied by a file.
            var source = root.getCompound("blueprint"); var raw = new CompoundTag();
            raw.put("elements", source.getList("elements", Tag.TAG_COMPOUND).copy());
            raw.putLong("origin", source.getLong("origin")); raw.putString("name", source.getString("name"));
            raw.putBoolean("uncompiled", true);
            return uncompiled(new CircuitBlueprint(raw));
        } catch (IOException | RuntimeException error) {
            throw new IllegalArgumentException("Invalid blueprint file: " + error.getMessage(), error);
        }
    }
}
