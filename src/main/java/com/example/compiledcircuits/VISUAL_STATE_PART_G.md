# Visual outlines — Part G validation report

## Executed

Command: gradlew.bat build visualChecks runGameTestServer --offline --console=plain
Result: BUILD SUCCESSFUL, 2026-09-09, 1m 2s.

- Membership protocol: 9 assertions; membership cache: 20.
- Hover: 124 assertions (1024-node budget, 50,000/50,001 boundary, cancellation,
  immutable atomic results, stable cache and compiled zero-BFS).
- Priority/focus: 15 assertions.
- Membership mutation routing: 15 assertions.
- Forge SERVER environment: Stage 7 suite, 185 assertions and one required
  persistence/repair GameTest passed. No client classloading failure.
- Total: 368 counted assertions plus scale checks and the world integration test.
- Latest client log confirms LevelChunkVisualIndexMixin mixed into LevelChunk.
- Packaged build/libs/compiledcircuits-1.0.0.jar (238733 bytes) inspected: manifest
  MixinConfigs, required client-only configuration, SRG refmap m_6978_ for the exact
  setBlockState(BlockPos,BlockState,boolean):BlockState descriptor, renderer/cache
  class entries. This is archive verification, not a production-launch test.
- git diff --check passed.

## Synthetic scale measurements

Single JVM run, 10k first (cold/JIT startup included); NOT game FPS or tick timings.
Payload excludes channel discriminator/compression/transport framing.

| Elements | Parts | Payload bytes | Build ms | Encode ms | Decode ms | Client assembly/commit ms | 10,000 cached hover calls ms |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 10,000 | 3 | 90,096 | 206.623 | 7.494 | 21.911 | 9.534 | 28.724 |
| 50,000 | 13 | 450,416 | 100.833 | 10.857 | 12.247 | 55.984 | 4.468 |

Validated every encoded size against the byte counter and 64 KiB per-part cap,
round-tripped every packet, checked complete membership and zero compiled BFS.
The cold timing and 50k atomic commit cost mean this does not establish hitch-free
large-world performance. Actual warmed server/client tick and GPU profiling remain.

## Added/changed paths (relative to project root)

- src/main/java/com/example/compiledcircuits/networking/CompiledElementPositionsS2CPacket.java:
  exact encodedBytes() diagnostic; no wire-format change.
- src/main/java/com/example/compiledcircuits/networking/CompiledElementSync.java:
  latest snapshotBytes/snapshotBuildNanos getters, reset at stop; no per-block logs.
- src/test/java/com/example/compiledcircuits/client/VisualScaleTest.java:
  reproducible 10k/50k CPU/payload check.
- build.gradle: visualScaleCheck and aggregate visualChecks.

Protocol remains 9. Full-dimension membership caps: 1,000,000 entries, 4096 per part,
64 MiB staging estimate, 30s assembly timeout, four parts/player/tick. Physical
index budgets remain 8 palette checks and 8192 state reads/tick; hover 1024 nodes/tick
and 50,000 total. Renderer traverses index buckets, culls before position reads,
uses one line batch, and performs no discovery/BFS/setBlock.

## Not verified by this run

- Actual client physical-index lifecycle automation (ClientCircuitBlockIndexTest
  is not implemented): min/max Y, negative/chunk boundaries, repeat packets,
  unload during discovery, remote changes, prediction rollback, explosion/fill.
- Multiplayer delivery and visual transition timing, stale connection callbacks
  across real reconnects, and a production packaged-JAR server/client launch.
- GPU state/occlusion in all graphics modes, particles/translucency, future opaque
  full-cube circuits and future IWireConnectable implementations.
- Real 10k+ client/server tick/frame profiling. Synthetic timings are not substitutes.

User reported A-F basic in-game behavior working, including the repaired IntelliJ
Mixin launch and live compile/decompile. That confirmation does not cover every
edge case above. Stage G automation/build/package audit is complete; full manual
acceptance and real-world performance validation remain open.
