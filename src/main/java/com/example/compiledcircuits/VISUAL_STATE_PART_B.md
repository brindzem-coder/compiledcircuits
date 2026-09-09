# Visual State Outlines — Part B

Implemented physical circuit tracking only. No hover, outline rendering, GUI, compiled snapshots or focus lifecycle changes. Protocol remains 9.

Package-relative files:
- block/CircuitElementPredicates.java: IWireConnectable identity predicate.
- client/ClientCircuitBlockIndex.java: loaded chunk/section buckets, generation, topology revision, near-camera scan queue and counters.
- client/CircuitVisualClientEvents.java: client tick and chunk/level/logout integration.
- mixin/client/LevelChunkVisualIndexMixin.java: observation-only RETURN injector for LevelChunk.setBlockState(BlockPos, BlockState, boolean).

Forge-root files: src/main/resources/compiledcircuits.visual.mixins.json and build.gradle.
Mixin configuration uses the client list only. Annotation processor generates the refmap using SRG converted from the resolved Forge mappings. Both packaged resources and dev runs receive it. Production manifest registers MixinConfigs; dev runs use explicit config plus SRG-to-MCP refmap remapping. No hand-written refmap or Forge/Gradle update.

Budgets: at most 8 section palette checks and 8192 cell reads per client tick. Air/non-circuit palettes skip cell scans. Sections with circuit palettes scan all 4096 cells atomically on the client thread; no partial scan result survives the tick, so mutations cannot interleave and an overlay is unnecessary. Setter mutations update the index immediately. Jobs hold keys/generation, not chunks. Reads use getChunk(..., false); unloaded sections never force-load.

The Forge 47.4.10 ClientChunkCache patch posts ChunkEvent.Load after both new and repeated full chunk packet application. It also posts Unload. Therefore no duplicate replaceWithPacketData hook was added.

Validation: build successful; packaged JAR inspected for client-only config, generated refmap mapping setBlockState to m_6978_, and manifest. Dev client launched through resource/texture initialization; log confirmed Mixin applied to LevelChunk. An initial dev missing-refmap warning was addressed by packaging generated resources for development and enabling SRG-to-MCP remapping.

Not performed: interactive placement/break/remote edits, repeat-packet and unload stress testing, numerical performance measurements, or production-client gameplay with the packaged JAR. Pink/Cyan rendering begins in later parts.
