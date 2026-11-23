package com.example.compiledcircuits.network;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

/** One ordered, size-limited transfer. Rejected parts never expose a partial blueprint. */
public final class BlueprintTransfer {
    private UUID id;
    private int total;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    public byte[] accept(UUID token, int offset, int size, byte[] part) {
        if (size < 1 || size > PortableBlueprint.MAX_BYTES || part.length < 1 || part.length > PortableBlueprint.PART_BYTES
                || offset < 0 || offset > size - part.length) throw new IllegalArgumentException("Invalid blueprint transfer size");
        if (offset == 0) { id = token; total = size; bytes.reset(); }
        if (!token.equals(id) || size != total || offset != bytes.size()) throw new IllegalArgumentException("Out-of-order blueprint transfer");
        bytes.writeBytes(part);
        return bytes.size() == total ? bytes.toByteArray() : null;
    }
}
