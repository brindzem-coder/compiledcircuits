# Visual State Outlines — Part A

Implemented logical membership sync only. Protocol 8 -> 9; the new S2C packet is appended after existing registrations. No BlockState, Stage 7 snapshots, focus lifecycle or renderer changes.

## Files
Package-relative production paths:
- networking/CompiledElementPositionsS2CPacket.java
- networking/CompiledElementSync.java
- networking/ModNetworking.java
- client/ClientCompiledElements.java
- client/CircuitVisualClientEvents.java
- client/ClientPacketHandlers.java
- event/CircuitVisualSyncEvents.java

Tests at Forge-root-relative src/test/java/com/example/compiledcircuits/client/ClientCompiledElementsTest.java and src/test/java/com/example/compiledcircuits/networking/CompiledElementPositionsPacketTest.java. build.gradle adds membershipCheck.

## Guarantees and limits
- Full logical positions from getElements(), including broken reservations; duplicate positions rejected.
- Atomic maps: position -> network ID and network ID -> immutable cached set.
- UNKNOWN before completion; incomplete updates retain previous complete view.
- Current-connection snapshot may arrive before ClientLevel. Level replacement resets maps and rebinds only matching-dimension snapshots. Old connection callbacks fail the connection identity guard.
- Login/dimension/respawn trigger snapshots. Dirty-dimension helper coalesces builds, with one build per requested dimension/tick; central compile/decompile mutation hooks belong to Part F.
- 4096 entries/part, less than 64 KiB encoded data per part; maximum 1,000,000 entries and 245 parts per snapshot, 64 MiB accumulated encoded-size budget, 30-second assembly timeout. Limits are documented protocol constants, not user configuration. Oversize snapshots fail rather than truncate.
- Four parts per player per server tick. Full-dimension building still runs synchronously on the server; no throughput/FPS claim or 10k performance benchmark.
- Disconnect clears membership and pending assembly. Server stop clears new queues. Existing selection and broken focus are untouched.

## Validation
compileJava successful. 29 assertions passed (20 cache, 9 packet): out-of-order assembly, duplicate parts/positions, immutable views, empty snapshot, timeout, dimension transition, packet before level, respawn/reconnect cleanup, maximum-size part roundtrip and bounds.
Repeat with gradlew.bat membershipCheck --offline --console=plain.
No interactive login/teleport/multiplayer test or dedicated-server launch performed for Part A. No Mixin required or installed yet: physical index, hover, outlines and rendering priorities are Parts B-E.
