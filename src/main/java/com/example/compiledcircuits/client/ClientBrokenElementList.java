package com.example.compiledcircuits.client;

import com.example.compiledcircuits.networking.BrokenElementListS2CPacket;
import java.util.List;

public final class ClientBrokenElementList {
    private static List<BrokenElementListS2CPacket.Entry> entries = List.of();
    private ClientBrokenElementList() {}
    public static void setEntries(List<BrokenElementListS2CPacket.Entry> value) { entries = List.copyOf(value); }
    public static List<BrokenElementListS2CPacket.Entry> getEntries() { return entries; }
    public static void clear() { entries = List.of(); }
}
