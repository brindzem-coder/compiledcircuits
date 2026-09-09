# Stage 7 implementation

## Files
Paths below are relative to the Forge project root.

- `src/main/java/com/example/compiledcircuits/network/CompiledBlockStateSnapshot.java`: immutable block ID and sorted defensive property map.
- `src/main/java/com/example/compiledcircuits/network/CompiledBlockStateCodec.java`: property API capture, strict validation and explicit EXACT/LEGACY/UNRESOLVED results.
- `src/main/java/com/example/compiledcircuits/network/CompiledBlockStateMatcher.java`: shared block/state comparison.
- `src/main/java/com/example/compiledcircuits/network/CompiledCircuitElement.java`: provenance, raw state NBT preservation, lazy resolution and once-per-element diagnostics.
- `src/main/java/com/example/compiledcircuits/network/CompiledElementFactory.java`: captures the already-read server state; both GUI and command compile use this factory.
- `src/main/java/com/example/compiledcircuits/network/CompiledNetwork.java` and `NetworkSavedData.java`: migration flag propagation and dirty marking.
- `src/main/java/com/example/compiledcircuits/network/NetworkIntegrityManager.java`: shared matcher and 128-slot round-robin loaded-element audit; cursors/config cache cleared on server stop.
- `src/main/java/com/example/compiledcircuits/event/NetworkIntegrityEvents.java`: audit after pending END-tick checks.
- `src/main/java/com/example/compiledcircuits/network/NetworkRepairManager.java`: exact targets, unloaded/invalid counters, safe placement and neighbor rechecks.
- `src/main/java/com/example/compiledcircuits/networking/NetworkBulkActionC2SPacket.java`: aggregated skip diagnostics.
- `src/main/java/com/example/compiledcircuits/config/ServerConfig.java` and `CompiledCircuits.java`: SERVER config registration.
- `src/test/java/com/example/compiledcircuits/network/CompiledBlockStateCodecTest.java`, `CompiledBlockStateMigrationTest.java`, `CompiledBlockStateMatcherTest.java`: assertions executed inside Forge GameTest, not an unbootstrapped JUnit environment.
- `src/gametest/java/com/example/compiledcircuits/gametest/Stage7GameTests.java` and `src/gametest/resources/data/compiledcircuits/structures/empty.nbt`: registered-mod integration test and isolated template.
- `build.gradle`: GameTest source set, isolated `run-stage7-tests` directory. `.gitignore` excludes generated test worlds/logs.

## NBT
Existing id/pos/type/blockId keys remain unchanged. New compiled elements add:

```snbt
stateDataVersion: 1,
stateOrigin: "COMPILED",
compiledBlockState: {Name: "compiledcircuits:input_endpoint", Properties: {facing: "east"}}
```

Legacy elements store version 1 and origin LEGACY without a snapshot. Old records migrate without world reads or ID changes. Invalid/future state tags are preserved defensively and do not become legacy or default states.
Storage remains `<world>/data/compiledcircuits_networks.dat`.

## Configuration
`<world>/serverconfig/compiledcircuits-server.toml`: `exactBlockStateIntegrity = false` by default. Restart to change comparison policy. Exact mode includes ALL properties, including dynamic Basic Wire connection flags. Snapshot capture and missing-endpoint restoration remain exact even with this option disabled.

## Verification (2026-09-09)
`gradlew.bat build runGameTestServer --offline --console=plain`: successful.
One Forge GameTest passed with 185 assertions, including all 12 input/output facing states, all 64 wire states, repeater enum/integer/boolean states, strict invalid NBT rejection and raw preservation, legacy migration/idempotence, matcher mode matrix, factory capture, NBT load followed by actual east-facing endpoint repair, integrity transitions and occupied-stone protection.
The initial test setup failed before assertions due to a template namespace error; the corrected isolated template passed.
`git diff --check`: clean.

Not claimed as tested: interactive GUI regression, manual full world exit/restart/re-entry, unloaded/cross-dimension repair in-game, or exhaustive audit scheduling stress tests. The persistence test performs NBT save/load; the integration test restores a loaded snapshot in a real Forge server world. Existing user worlds were not modified.

## Limits
- Legacy orientation is unknown; only legacy allowlisted components use defaults.
- BlockEntity configuration is not persisted; gates and other components remain unsupported for auto repair.
- Occupied same-ID state mismatches in exact mode require manual correction or removal before repair.
- Dynamic wire flags can remain mismatched when surrounding topology differs; the saved snapshot is never adjusted to hide this.
- Unloaded chunks are skipped, never force-loaded by audit or repair.
- `RepairResult.repaired` counts placements, not confirmed repairs; the integrity manager owns confirmation and runtime recovery.
