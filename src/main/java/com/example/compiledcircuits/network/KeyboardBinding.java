package com.example.compiledcircuits.network;

import java.util.*;

/** Stable physical input codes. No client classes: safe to decode on a dedicated server. */
public record KeyboardBinding(List<Integer> codes) {
    public static final int MOUSE = 1000, WHEEL_UP = 1100, WHEEL_DOWN = 1101,
            LEFT = 1110, RIGHT = 1111, UP = 1112, DOWN = 1113;
    public static final int MAX_KEYS = 8;
    public KeyboardBinding {
        if (codes.isEmpty() || codes.size() > MAX_KEYS) throw new IllegalArgumentException("Choose 1–8 inputs");
        var sorted = new TreeSet<Integer>(codes);
        if (sorted.size() != codes.size() || sorted.stream().anyMatch(c -> !valid(c) || c == 256)
                || sorted.contains(290) && (sorted.contains(341) || sorted.contains(345)))
            throw new IllegalArgumentException("Esc and Ctrl+F1 are reserved");
        codes = List.copyOf(sorted);
    }
    public static boolean valid(int c) {
        return c >= 32 && c <= 348 || c >= MOUSE && c < MOUSE + 8
                || c == WHEEL_UP || c == WHEEL_DOWN || c >= LEFT && c <= DOWN;
    }
    public static KeyboardBinding parse(String value) {
        if (value.length() > 64) throw new IllegalArgumentException("Binding too long");
        try { return new KeyboardBinding(Arrays.stream(value.split("\\+", -1)).map(Integer::parseInt).toList()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid binding", error); }
    }
    public String encode() { return codes.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("+")); }
    public boolean matches(Set<Integer> pressed) { return pressed.containsAll(codes); }
    public String label() { return codes.stream().map(KeyboardBinding::name).collect(java.util.stream.Collectors.joining("+")); }
    private static String name(int c) {
        if (c >= 65 && c <= 90 || c >= 48 && c <= 57) return Character.toString((char)c);
        if (c >= 290 && c <= 314) return "F" + (c - 289);
        if (c >= 320 && c <= 329) return "Num" + (c - 320);
        if (c >= MOUSE && c < MOUSE + 8) return "Mouse" + (c - MOUSE + 1);
        return switch(c) {
            case 32 -> "Space"; case 39 -> "'"; case 44 -> ","; case 45 -> "-"; case 46 -> ".";
            case 47 -> "/"; case 59 -> ";"; case 61 -> "="; case 91 -> "["; case 92 -> "\\"; case 93 -> "]"; case 96 -> "`";
            case 257 -> "Enter"; case 258 -> "Tab"; case 259 -> "Backspace"; case 260 -> "Insert";
            case 261 -> "Delete"; case 262 -> "Right"; case 263 -> "Left"; case 264 -> "Down"; case 265 -> "Up";
            case 266 -> "PgUp"; case 267 -> "PgDn"; case 268 -> "Home"; case 269 -> "End";
            case 280 -> "Caps"; case 281 -> "ScrollLock"; case 282 -> "NumLock"; case 283 -> "Print"; case 284 -> "Pause";
            case 330 -> "Num."; case 331 -> "Num/"; case 332 -> "Num*"; case 333 -> "Num-";
            case 334 -> "Num+"; case 335 -> "NumEnter"; case 336 -> "Num=";
            case 340 -> "LShift"; case 341 -> "LCtrl"; case 342 -> "LAlt"; case 343 -> "LSuper";
            case 344 -> "RShift"; case 345 -> "RCtrl"; case 346 -> "RAlt"; case 347 -> "RSuper"; case 348 -> "Menu";
            case WHEEL_UP -> "WheelUp"; case WHEEL_DOWN -> "WheelDown";
            case LEFT -> "MouseLeft"; case RIGHT -> "MouseRight"; case UP -> "MouseUp"; case DOWN -> "MouseDown";
            default -> "Key" + c;
        };
    }
}
