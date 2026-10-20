package com.example.compiledcircuits.network;

/** Admission limits, shared by commands and packets. They do not change saved-world limits. */
public final class OperationLimits {
    private OperationLimits() {}
    public static final int NAME = 64, PATH = 256, IDS = 1024;
    public static final int ELEMENTS = 50_000, REQUESTS_PER_WINDOW = 20, WORK_PER_WINDOW = 200_000;
    public static final int WINDOW_TICKS = 20, MAX_ACTORS = 4096;
}
