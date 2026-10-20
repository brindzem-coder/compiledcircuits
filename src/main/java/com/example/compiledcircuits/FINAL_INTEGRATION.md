# Final integration verification

Started 2026-10-05. Production code under verification: `fa24fb1` on `fixes`.
Status: **in progress**, not final acceptance.

## Clean automated run — PASS

- Gradle 8.8, Eclipse Adoptium Java 17.0.17+10, Minecraft 1.20.1 / Forge 47.4.10.
- `gradlew.bat clean build visualChecks runGameTestServer -Pstage09LegacyInputs=<temporary-copy-directory> --offline --console=plain`
- BUILD SUCCESSFUL in 1m 49s; all 68 required GameTests passed.
- Hover 124, manager 78, membership packet 28, membership cache 33, mutation engine 87, priority 15 and Stage7 185 checks passed.
- Three original legacy NBT files matched the stage09 manifest SHA256 values before copying. Tests used temporary copies outside build so clean could not remove their inputs. Round-trip tests, including forced over-capacity loading, passed for 3 networks/233 elements, 1/1026 and 32/1024.
- Raw ignored log: project-root `baseline-results/final-integration-clean.log`.
- Git working tree was clean before documenting this run; `git diff --check` passed.

This run starts an isolated GameTest server. It does not certify a real client session, a multi-process restart, or before/after gameplay performance.

## Technical integration review — 2026-10-07

Reviewed the change inventory from baseline commit `a8c7f91` through `fa24fb1`, and traced the current cross-stage paths below. This is a focused integration review, not a claim that every possible defect is excluded.

- Ownership: saved-data admission, prepared compilation publication/rollback, replacement and retirement share `NetworkMembershipIndex`. Claims include dimensions; retired cleanup checks object identity. Capacity is rechecked before publication and ID consumption.
- Signal: output blocks and manager snapshots read `getEffectiveSignal()`. Saved powered state alone cannot authorize HIGH; runtime readiness, input availability, damage and pending integrity gate it.
- Work: integrity, repair, scan, runtime and damage/highlight/membership delivery share `ServerWorkBudget`. Repair captures immutable damage targets and revalidates actor/context/owner; decompile retires active membership before deferred callbacks.
- Requests: command, single-packet and bulk-packet mutations route through `NetworkOperations`; registration constrains packet direction. Decoder limits precede allocation of target lists. Permissions/atomic refusal/rate-limit tests remain in the suite.
- Delivery: membership/damage/highlight clients gate replies by connection and contextual tokens; staging is bounded, and publication is atomic. Membership capacity changes advance revisions and permit recovery. Legacy full broken-list packet types are not registered; old full-list builders are retained for comparison tests, not live broadcast delivery. The synchronous membership snapshot helper is called by tests; live delivery uses the incremental engine.
- Worlds: compile/runtime reads use available chunks; repair validates dependencies before placement. No force-load/ticket call was found in these production operation paths. Forge placement callbacks and world load/save are not hard wall-clock bounded operations.
- Packaging: `build/libs/compiledcircuits-1.0.0.jar` SHA256 `90b01b5f465dbb2d8434e8c8b9ada12edd8a9cf68fdc0bacc395942ea431aea1`; both required server mixins are present, test-only GameTest/scan blocks are absent.

### Observations retained for follow-up

1. The first write-phase full suite failed `longChain`: only 267/512 networks were HIGH at its 100-poll deadline, all runtime statuses READY. Both restart-write fixtures completed. The subsequent read-phase suite passed all 67 tests; the chain settled in 15 ticks. The cause of the initial deadline failure is **not established**; no deadline or production budget was changed to hide it. A repeated write/read pair is recorded below.
2. `MembershipQuarantineEvents` still drains its chunk-event queue and traverses affected isolated-record positions without `ServerWorkBudget`. This is an exceptional legacy-quarantine path, separate from the now-budgeted administrative raw-record removal. Its large-conflict-world latency remains unmeasured; do not claim that every callback/queue in the mod is budgeted. No data-loss failure was reproduced here.
3. The historical range `git diff --check a8c7f91..HEAD` reports extra blank lines at EOF in four tracked client TOML files, `MembershipIndexGameTests.java` and `STAGE10_MANAGER.md`. These are formatting findings, not build failures. The current working diff check is separate.

## Separate-process restart and capacity — 2026-10-07

Production code and test deadlines were unchanged throughout these runs.

| Run / ignored log in project-root baseline-results | Result |
| --- | --- |
| final-integration-restart-write.log | FAIL: longChain deadline; both restart write markers present |
| final-integration-restart-read.log | PASS: all 67 required tests; both restart read markers; chain 15 ticks |
| final-integration-restart-write-repeat.log | PASS: all 67 required tests; both restart write markers; chain 24 ticks; build 1m 2s |
| final-integration-restart-read-repeat.log | PASS: all 67 required tests; both restart read markers; chain 16 ticks; build 58s |
| final-integration-capacity.log | PASS: membershipCapacityScaleCheck; build 24s |

The restart commands were `gradlew.bat runGameTestServer --offline --console=plain`, with process environment `CC_RUNTIME_RESTART_PHASE=write` then `read` in separate Gradle/JVM invocations. There are 67 tests here because the optional copied-legacy-NBT generator was not enabled; that additional test already passed in the clean 68-test run.

Confirmed across actual process restarts: saved HIGH/LOW is reconciled to actual inputs (outputs 0/15); saved pending integrity starts LOW; a physically absent wire remains damaged; a stale repair job does not resume; a fresh repair restores HIGH. These tests use `run-stage7-tests`, not the user's reference worlds.

Capacity command: `gradlew.bat membershipCapacityScaleCheck --offline --console=plain` (Java 17, task heap limit 2 GiB).

- 1,000,000 membership records, 245 parts, 9,013,102 encoded payload bytes; maximum part 36,918 bytes.
- 64 simulated recipients, 15,680 packets, peak 8 globally and 1 per recipient per simulated tick (limits 8 and 2). Complete delivery/client application in 3,179 simulated server ticks; one peer deliberately withheld ACK.
- Shared retained encoded-data estimate peaked at 13,250,880 bytes, within the 64 MiB protocol budget. This estimate is not total Java heap use.
- Direct client apply took 1,287.839 ms in aggregate across 245 calls; maximum call 76.992 ms. No hard frame-time guarantee is inferred.
- Heap before the test: 830,391,296 bytes; sum of heap-pool peak readings: 1,906,835,456 bytes. Pool peaks may occur at different times and are not a simultaneous retained-memory measurement.
- These synthetic timings and simulated clients do not establish real multiplayer catch-up latency, FPS or TPS improvements.

Technical execution and focused integration review are complete **with the observations above retained**. The first chain timeout remains unexplained rather than reclassified as a pass. No production fix, test relaxation, commit or push was made. Final acceptance still requires the following client scenarios and baseline comparison; the quarantine performance observation should be resolved or explicitly accepted before declaring the entire plan complete.

## Acceptance scope and client validation

### Client launch repair — 2026-10-08

The user could not launch the IntelliJ client. Gradle daemon log `daemon-14328.out.log` records a Java `ResolutionException` for duplicate packages in modules `compiledcircuits` and `main`. Ordinary `build` reproduced successfully. The old IDE configuration referenced the absent `build/classpath/runClient_minecraftClasspath.txt` after clean, ran only `prepareRunClientCompile`, and included only the visual Mixin configuration.

Enabled `minecraft.enableIdeaPrepareRuns = true` in `build.gradle` and regenerated configurations with `genIntellijRuns`. The generated client now runs `prepareRunClient` plus `prepareRunClientCompile` and includes both visual/runtime Mixin configurations. `runClient` passed the previous module-resolution failure and completed render resource/texture initialization; the client was left running for the user. No world was opened by the agent. Evidence: project-root `baseline-results/client-start-build.log`, `client-start-idea-prepare.log`, `client-start-run.log`, and `client-start-fixed-validation.log`. This repairs development launch preparation, not network/gameplay code. The final working state is now `fa24fb1` plus this Gradle change and this report.

### Agreed reduced manual scope — 2026-10-19

The user approved three final manual blocks: one lifecycle test on a working copy of the old large-network world; one short two-client dedicated-server test including damage/repair, dimension travel and reconnect; and matched performance recordings for the large network and 32 small networks (idle, switching, compilation, mass damage and repair).

The separate new-world repeat and exhaustive manual permutations of permissions, malformed packets, legacy conflicts, respawn and chunk transitions are omitted from this final manual pass. Earlier stage-specific user reports and automated evidence retain their original scope; omitted scenarios are not marked as newly passed. Technical observations above remain open separately.

### Block 1 — PASS, user report 2026-10-19

The user explicitly confirmed completing the entire detailed chat checklist with every result matching the stated expectations. A single confirmation covers the checklist; no per-item response is required.

- Working copy of the backed-up large-network world: existing network loaded and input switching worked.
- 100 ordinary wires removed: damage was recognized and the output became LOW with the input still on.
- Slowed repair restored some but not all wires; switching to spectator cancelled subsequent placements while preserving completed work and leaving damage/LOW.
- A fresh repair in Creative restored the remaining wires, cleared damage and returned HIGH without toggling the input.
- Normal Save and Quit/reopen preserved the network ID, restored blocks and healthy signal; stale repair did not resume.
- Decompile removed the record while preserving physical blocks; recompilation succeeded without stale ownership conflicts and input switching worked.
- The temporary repairStepsPerTick setting was restored as instructed. Its exact restored numeric value was not separately supplied or inspected.

These are user-observed outcomes, not an agent inspection or a performance capture. Exact world-folder name, network IDs and partial placement counts were not separately supplied. Block 1 is complete in the agreed reduced scope.

### Block 2 — PASS, user report 2026-10-19

The user confirmed completing all seven steps of the two-client dedicated-server chat checklist, with every result matching the expectations.

- Both clients saw the same healthy large network and consistent input/output switching.
- Removing 100 ordinary wires produced LOW and matching damage lists/markers on both clients.
- Player B moved to the Nether while A kept the network loaded. Overworld markers did not appear in Nether space; global GUI records were allowed to remain visible.
- A repaired the network while B was away. On returning, B received restored blocks, HIGH and cleared damage without stale markers after synchronization.
- During another repair initiated by A, B disconnected before completion. A's repair continued successfully; B reconnected to the healthy current state without stale damage returning.
- Final switching worked on both clients. The user reports completing graceful save-all flush/stop and restoring the temporary repairStepsPerTick setting as instructed; its exact value was not independently inspected.

Evidence is the user's explicit checklist confirmation, not independently inspected logs or a performance capture. No separate respawn, identical-coordinate dual-network or chunk-unload scenario is inferred from this reduced test. Block 2 is complete in the agreed scope.

### Block 3 — complete, 2026-10-20

The user supplied exactly 24 recordings, confirmed their prescribed order and correct network behavior, and reported no erroneous or extra captures. Files were selected by local modification date and sorted chronologically, consistent with that confirmation. Each group has three runs: large idle, toggle, damage/repair, compile, then the same four scenarios for 32 small networks.

Evidence in project-root `baseline-results/final-performance-2026-10-20`: `REPORT.md`, `manifest.json` (numbered mapping, source paths, SHA256 for current and baseline files, actual durations, metrics and counters), and 24 preserved `raw` copies. All captures parse, have positive duration and nonempty nonnegative tick samples, with zero dropped samples. All six repair runs report 32 damage transitions, 32 placements, 32 repair transitions, one completed job and zero remaining targets; checked work queues are empty. Compile runs report one or 32 queued/completed successful networks as appropriate.

| Scenario | Baseline p95 range, ms | Current p95 range, ms |
| --- | --- | --- |
| Large idle | 6.036–6.078 | 1.237–1.367 |
| Large toggle | 1.741–2.095 | 1.337–1.526 |
| Large damage/repair | 2.316–2.592 | 1.325–1.605 |
| Large compile | 2.446 (one run) | 1.363–2.168 |
| Small idle | 6.098–6.336 | 1.190–1.319 |
| Small toggle | 2.304–2.424 | 1.313–1.352 |
| Small damage/repair | 2.726–2.910 | 1.344–1.437 |
| Small compile | 2.680 (one run) | 1.426–1.520 |

Damage protocol encoded payload fell from about 107 KB to 5.3–6.4 KB per scenario, including new ACK payload but excluding GUI/vanilla/transport framing on both sides. Small-network repair changed from 32 old repair calls to one bulk operation. Small-network switching uses 224 index probes versus 3696 old network visits for 224 calls. Compilation is now asynchronous: large-network job wall time is 470–957 ms, CPU 17–37 ms; the old 34.5-ms synchronous command timer is not an equivalent metric. Snapshot payload did not decrease.

The user accidentally broke a grass block in run 19 or 20. Run 19 has two additional unowned-position skips, consistent with this report but not proof of block identity. Network damage/repair counts remain exactly 32 in all three runs; none was excluded and no repeat is needed. Small timing deviations are accepted; analysis uses actual durations.

These separated-date observations do not establish a causal fivefold mod speedup, FPS gains, lower memory use or hard tick deadlines. Baseline idle variability and changed timer scopes are documented in the report. Functional behavior and matched world/settings are user-reported rather than reconstructed from JSON. No new TNT or exceptional legacy-conflict stress coverage is inferred.

All three agreed reduced manual blocks are complete. The unexplained initial longChain timeout and exceptional unbudgeted legacy-quarantine path remain recorded technical observations; these measurements do not resolve them. No commit or push was made.

Original worlds in `baseline-2026-09-20` remain reference backups. Use fresh working copies. Gracefully save/stop servers; do not use an IDE kill as the persistence check. GUI geometry changes and the teleport button remain deferred until the original plan is verified.
