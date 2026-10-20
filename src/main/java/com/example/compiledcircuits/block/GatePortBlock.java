package com.example.compiledcircuits.block;

/** A port is a directed terminal, never a bridge between adjacent wires. */
public class GatePortBlock extends EndpointBlock {
    private final boolean input;
    public GatePortBlock(Properties properties, boolean input) { super(properties); this.input = input; }
    public boolean isInput() { return input; }
}
