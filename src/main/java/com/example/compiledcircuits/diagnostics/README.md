# Performance diagnostics

Disabled by default. Requires command permission level 2 (cheats in singleplayer).

1. Run `/ccperf start` after warm-up.
2. Perform the scenario without pausing singleplayer.
3. Run `/ccperf stop`.
4. Run `/ccperf export`. Chat prints the JSON path in `debug/compiledcircuits-performance` under the server working directory (normally `run` for runClient).

`/ccperf status` reports the state. `/ccperf reset` discards the active and stopped capture. Starting a new capture discards the previous stopped capture; export first. Repeated start while recording is rejected. Export requires a stopped capture; repeated export cannot overwrite the existing UUID file. Stopping the server clears in-memory captures, so export before leaving the world.

Counters include scan reads/visited positions/queue peak/success, lookup calls/hits/misses/networks/elements, compile duration, pending enqueue and processed positions, audit work, repair outcomes, snapshot builds, and actual packet encoder invocations/payload bytes. Timings use nanoseconds. Nested durations overlap and must not be summed. Missing counter keys mean no recorded occurrences, not proof the scenario exercised that code.

Only server-thread operations contribute gameplay counters. Packet encoding can happen on other threads and uses a synchronized session guard; it never serializes a packet twice. Encode counts are not recipient counts: a broadcast may encode once for multiple deliveries. Payload excludes the channel discriminator and transport framing. Singleplayer may include both server and client encoders; it is not a measurement of remote network traffic.

Tick samples cover Forge START/HIGHEST to END/LOWEST, not sleep or the entire Minecraft tick. First partial tick is excluded. At most 72000 samples are retained; droppedTickSamples records overflow. Percentiles apply to retained samples only. Heap is whole-JVM start/end usage, not retained mod memory; no forced GC. Enabled counters have overhead and should use identical instrumentation for comparisons.

Export includes Java version, JVM flags, heap limit and available processor count. It does not automatically identify world seed, scenario, code commit or graphical settings: retain these with each report. JFR remains the tool for allocation/GC analysis and sampling CPU work.

Command compilation (`/circuit compile`) records `compile.command.calls`, `.success`, `.rejected`, `.exceptions` and `.nanos`. The timer covers synchronous handler execution, including selection validation, scan, conflict checks, network creation and command feedback. Early rejections and propagated exceptions are timed too. Brigadier parsing and time between commands are outside the timer, as are deferred ticks and delivery. Existing `compile.calls`/`.nanos` refer to the GUI compileSelected path and are kept separate. Timings include nested scan/lookup work; do not add them together. Missing outcome keys mean zero recorded occurrences.
