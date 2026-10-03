# Stage 03 — revisioned damage synchronization

Implementation: 2026-10-03, based on stage-08 commit `9b73527`. Scope: `fixes/03-damage-sync.md`; implementation order remains 00 → 01 → 04 → 02 → 07 → 06 → 08 → **03** → 05 → 09 → 10 → 11. Java 17.0.17+10, Gradle 8.8, Forge 47.4.10, Minecraft 1.20.1. No user worlds or baseline backups were changed by the agent. No commit created by the agent. Stage 03 is complete: automated verification and manual client acceptance, including the actual-block tooltip recheck, have passed. Manual evidence is recorded below.

## Server changes and publication

`DamageLedger` belongs to `NetworkSavedData`. Immutable AVL roots retain a published base and current records; element changes use `(networkId, elementId)`. Integrity callbacks capture a root and a dirty key, not a full sorted list. Same-tick damage/repair coalesces relative to the published base. Server physical transitions, SavedData dirtiness, pending LOW gating and runtime reconciliation still happen independently of packet delivery.

Observers cover confirmed damage/repair, changes to the actual block of an already broken element, network registration/removal, network rename/move, and folder rename/move. Folder path refresh advances incrementally. Loaded quarantined networks are not registered in the runtime/ledger. Removing a network emits a network tombstone. Visible actual-block updates preserve a damage occurrence token, so an accepted repair still reports occupancy correctly; repair followed by a new damage occurrence invalidates the old repair target.

The existing ordered END handler runs pending integrity, compilation, runtime and then damage flush. Publication captures immutable roots before building. Changes after capture stay in the next publication. Legacy full damage broadcasts are disabled; their builders/codecs remain only as reference fixtures and the existing row datatype. They are not registered wire messages. Ordinary network-list and membership synchronization retain their separate contracts.

## Stream and recovery

There is one GLOBAL normalized stream. Each committed client revision produces both the global GUI rows and the current-dimension markers. Positions are deduplicated from all surviving records. No open GUI subscription is required. `DamageView` incrementally prepares both views and publishes them together; focus is pruned against the same committed data.

Protocol **12** registers explicit packet directions. A context UUID changes with connection, level or player identity, including respawn and returning to the same dimension. A server epoch identifies the coordinator. Every part identifies batch, kind, base/target revision, part count/index and total records/encoded budget. Empty full snapshots explicitly clear the state. Delta application requires the exact base. Reordered parts and identical repeats are accepted; inconsistent parts, duplicate keys/metadata, wrong bases, invalid totals and timeouts leave the client unconfirmed. Old connection/context packets cannot repopulate cleared markers.

The client requests initial/recovery state independently of GUI. The server checks the requesting player's current dimension as a context witness; scope is fixed GLOBAL, not client-selected. Requests use stage-02 admission and a per-peer 40-tick cooldown. Client states distinguish UNKNOWN, STALE, OVER_CAPACITY and READY; the GUI additionally shows SYNCING while preparing a view. Unconfirmed rows cannot be selected/repaired/decompiled and markers are hidden until publication.

One captured full snapshot and shared delta batches are encoded incrementally, reused across peers. A peer acknowledges only after assembly and presentation finish. At most one delivery is outstanding per peer. A rolling journal supplies contiguous following revisions. Overflow drops queued deltas and recovers with a new captured full state; it does not restart an in-flight snapshot on every mutation. Starting a full snapshot subsumes queued revisions through that snapshot's revision. Full building takes precedence over queued delta encoding. Slow peers retain bounded immutable batches; after quiescence, missing journal bases trigger recovery. ACK timeout releases a stalled delivery. Disconnect and server stop discard delivery state.

## Limits

Named constants are in `DamageProtocol`.

| Limit | Value |
|---|---:|
| Encoded physical part, including application header | 32 KiB maximum |
| Parts / logical batch | 2,048 |
| Records / batch and committed state (metadata included) | 1,000,000 |
| Assembly / encoded batch budget | 64 MiB |
| Raw committed record bytes | 64 MiB minus 2,048 × 133 bytes |
| Retained encoded batches/builders, shared globally | 128 MiB |
| Rolling journal | 8 batches / 8 MiB |
| Queued delta builders | 8 |
| Dirty keys, including network descriptors | 8,192, then snapshot recovery |
| Active peers / pending capacity notices | 64 / 64 |
| Delivery per server tick / per peer | 8 / 2 parts |
| Shared sync work units per tick | min(1,024, totalOperationsPerTick / 8) |
| Client assembly work per client tick | 512 records/part transitions |
| Client presentation work per tick | 1,024 steps and cooperative 2 ms |
| Resync cooldown | 40 ticks |
| Assembly idle / absolute timeout | 200 / 20,000 client ticks |
| Server ACK timeout after final part | 20,000 server ticks |
| Name / resource ID / folder path | 64 / 256 / 1,024 characters |
| Chat window | 20 server ticks from its first transition |

Byte caps are checked against actual FriendlyByteBuf encoding. The 128-byte per-part reservation conservatively covers the application header; transport framing is separate. Retention accounts shared batches once, including deliveries no longer in the journal. Builders pause before exceeding the global encoded cap. JVM object overhead, persistent tree nodes, presentation collections, temporary bounded encoder buffers and transport buffers are additional memory, not included in that byte figure. Record, part, peer and queue limits bound their cardinalities. Over-capacity state is explicit and can recover after the data fits again; it is never reported as an empty healthy state. More than 64 simultaneous peers receive capacity status and retry; this release does not silently claim support beyond that cap.

Stage 03 supersedes stage 08's two sync slots. With default total 12,288, sync reserves 1,024 units, integrity's group share is 1,877, runtime's share/cap is 2,048, and scan's share is 7,339, also subject to each lane's existing caps. Non-sync work stops before the last min(1.5 ms, total time / 4), leaving sync a time opportunity inside the shared 9 ms budget. Checks are cooperative between bounded units, not hard real-time guarantees. A callback/allocation/GC can overrun a unit.

Chat counts **physical confirmed transitions**, not final delta entries. Continuous activity does not move the first deadline. Each summary contains damaged/repaired counts and distinct affected-network count, never thousands of names. Metadata-only changes do not add physical transitions. Network-count storage is capped at 262,144 and displays “at least” if saturated.

## Verification and evidence

Automated scenarios use `run-stage7-tests`, not `run/saves`. Logs are under the project-root `baseline-results/`:

- `stage03-validation.log`: build, visual checks and initial 48 GameTests passed after fixing repair occurrence identity.
- `stage03-restart-write.log`: build, visual checks and 50 GameTests passed; `CC_RUNTIME_RESTART_PHASE=write` saved real restart fixtures before the server exited.
- `stage03-restart-read.log`: both actual restart read fixtures passed. This intermediate suite had one unrelated failure: the newly added chat test reused a scheduled Runnable identity and timed out. Wrapping its next scheduled invocation fixed the test without changing production chat behavior.
- `stage03-final.log`: final `build visualChecks runGameTestServer --offline --console=plain` passed, including **all 51 required GameTests**. Restart environment variable was unset for this final run; the actual two-process checks are evidenced by the preceding write/read runs. `git diff --check` passed.

New deterministic integration tests connect the production server engine to independent production client replicas through actual packet encode/decode. They cover multipart atomicity, reversed/repeated parts, incompatible revisions, empty snapshots, timeouts/recovery, same-dimension context replacement, coalescing, metadata changes, removal during delivery, three receivers, 12 receivers under continuous mutations, 100/1,000/10,000 broken elements followed by repair, global rows vs dimension markers, explicit capacity/recovery, recipient admission, encoded retention, and fixed chat deadlines. Existing integrity/runtime/repair tests exercise the physical signal behavior. The separate write/read processes exercise existing runtime and pending-work restart fixtures.

These are **in-process client state-machine integrations on a dedicated GameTest server**, not three real connected Minecraft clients. Actual rendering, portal/respawn event timing, connection lifecycle, and dedicated-server multiplayer UI were subsequently checked by the user; see the manual results below. Tests exceeding a header/metadata limit do not allocate a full 64 MiB assembly; maximum-capacity heap behavior has not been profiled. Malformed raw wire packets are rejected by decoding and may cause Forge to disconnect the sender; logically invalid decoded batches permit resync.

Diagnostics include `damage.snapshots`, `damage.deltas`, `damage.encodedBytesBuilt`, `damage.physicalPackets`, `damage.payloadBytesSent`, `damage.build.nanos`, backlog/journal gauges, packet peaks, capacity/overflow/timeout and chat transition counters. The replica exposes its staging byte count for tests. Existing server-thread `/ccperf` captures do not measure a remote client's heap or frame time.

### Measured reference comparison

Final run, deterministic synthetic networks, three independent receiver replicas, all in the Overworld. The legacy reference invokes the retained full global-row builder/sorter plus dimension-marker builder and encodes each once. Legacy bytes below multiply that combined encoding by three recipients. New bytes include actual application headers and record payloads for all three recipients, excluding initial empty snapshots. Neither figure includes Forge/TCP framing. New shared build time sums budgeted builder steps; legacy time measures its single synchronous build+encode. These single samples are not statistically controlled benchmarks and include instrumentation/JIT/allocation effects.

| Broken elements | Legacy bytes × 3 | New encoded bytes, 3 peers | Legacy build+encode, ms | New shared build steps, ms | New damage parts / repair parts | Simulated ticks, damage + repair |
|---:|---:|---:|---:|---:|---:|---:|
| 100 | 30,066 | 18,897 | 12.473 | 15.317 | 3 / 3 | 2 |
| 1,000 | 302,691 | 189,135 | 7.713 | 15.132 | 6 / 3 | 4 |
| 10,000 | 3,029,691 | 1,893,861 | 47.352 | 26.089 | 60 / 3 | 40 |

This compares one legacy snapshot per burst against the new bounded transfer, not an invented legacy full broadcast per element. Stage 08 already coalesced full broadcasts. New encoding is slower in the two small single samples, faster in the largest, and sends about 37% fewer application bytes in these fixtures. Large transfers deliberately use more small physical parts. The important verified behavior is bounded incremental work, shared encoding, bounded delivery/backlog and exact eventual state. It does not establish an in-game FPS/MSPT gain. The synthetic tick count is the deterministic engine clock, not a measured wall-clock latency for real connected clients. Raw figures and an evidence summary are retained in `baseline-results/stage03/`.

## Completed manual acceptance

### User report, 2026-10-03

- Check 1: user could not visually distinguish LOW from marker timing. Do not claim a human-observed ordering; the transitions can occur within one rendered frame. Runtime ordering is covered by automated checks and code order.
- Check 2: user removed 64 wires with `/fill`, received a 64-damage summary, requested one repair through the GUI, saw 64 queued targets and completion with 64 placed blocks. Two later integrity summaries split the repaired count. Capture `bcd1d178-a179-4e34-ba84-5886de589340.json` verifies damaged=64, repaired=64, chatSummaries=3, completedJobs=1, PLACED=64, remainingAtCompletion=0. This is not duplicate repair accounting: placement completion and confirmed integrity transitions are different notifications; physical checks crossed a fixed chat-window boundary.
- Check 3: LOW, marker and one damage notice were observed, but no visible actual-block update. Inspection found that the data was synchronized but the GUI row displayed only the expected block. Added a hover tooltip with `Expected` and `Actual`; the user subsequently repeated check 3 and confirmed all expected behavior: Actual Air → Stone → Dirt, followed by removal of the row and marker after restoring the correct wire.
- Tooltip follow-up: `build visualChecks --offline --console=plain` passed (`baseline-results/stage03-tooltip-validation.log`). This display-only correction did not rerun the full server suite.
- Checks 4–7: user reports all expected behavior, including the dedicated server scenario with three real connected clients. These supplement the automated in-process client-replica tests above.

The client capture lasted 49.549 seconds (991 sampled ticks). It records 26 deltas, 26 S2C parts and 26 ACKs; damage part encoding total 5,968 bytes, shared damage build time 7.988 ms across the capture, and a peak of one damage part per tick/player. No full damage snapshot was built during this capture. Pending queue, repair jobs/targets and runtime queue were zero at the end. The journal's eight entries are retained recovery history, not eight outstanding deliveries. Repair work peaked at five steps per tick; oldest job age was 21 ticks. One cooperative operation overrun was recorded (maximum 3.065 ms); total accounted work peaked at 8.806 ms. These are instrumented work metrics, not complete Minecraft tick time or proof of an FPS improvement.

### Completed checklist

1. Without opening the manager, break one wire of a powered compiled network: output LOW immediately; damage marker appears after sync. Repair it: output recovers after integrity/runtime checks, and the last row/marker disappears.
2. Leave the Broken screen open; damage/repair a group. Check counts, positions and actual-block information. Replace an already incorrect block with another incorrect block: its information changes without another damage transition notice.
3. Rename/move a damaged network and rename its folder; the damaged row/path updates. Decompile the damaged network; no rows, markers or selected highlights remain for it.
4. Repeat damage/repair continuously for over 3 seconds. Chat produces short summaries while activity continues, rather than a message for each wire or only after activity stops.
5. With damage present, leave/rejoin, respawn and travel between dimensions (including returning quickly). Each context acquires current state; old markers must not appear in the wrong world. Brief UNKNOWN/STALE/SYNCING during recovery is expected.
6. Dedicated server: two players in one dimension and a third in another. Everyone's manager includes global damage, while markers match their own dimension, including before opening the manager.
7. For a large damage burst, record `/ccperf start`, perform damage and repair, wait for delivery to settle, `/ccperf stop`, `/ccperf export`. Compare against an equivalent baseline world; automated microbenchmarks below do not establish an FPS/MSPT improvement.

The existing screen still refreshes some selection/list structures when a committed view arrives. Stage 03 bounds protocol assembly and prepares the sorted view incrementally; broader GUI/render optimization remains stage 10. Stages 05, 09 and 10 have not been implemented here.
