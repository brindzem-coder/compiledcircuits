package com.example.compiledcircuits.network;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record CompiledBlockStateSnapshot(String blockId, Map<String, String> properties) {
    public CompiledBlockStateSnapshot {
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
    }
}
