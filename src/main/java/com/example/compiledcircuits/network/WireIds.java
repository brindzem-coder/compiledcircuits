package com.example.compiledcircuits.network;

/** Numeric labels are strings so channel IDs are not limited to a Java integer. */
public final class WireIds {
    public static final int MAX_LENGTH = 64;
    public static final int RED = 0xff0000;
    private WireIds() {}
    public static String normalize(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_LENGTH) throw new IllegalArgumentException("Enter 1–64 digits");
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) < '0' || text.charAt(i) > '9') throw new IllegalArgumentException("ID must contain digits only");
        int start = 0;
        while (start < text.length() - 1 && text.charAt(start) == '0') start++;
        return text.substring(start);
    }
    public static int compare(String a, String b) { int size = Integer.compare(a.length(), b.length()); return size == 0 ? a.compareTo(b) : size; }
}
