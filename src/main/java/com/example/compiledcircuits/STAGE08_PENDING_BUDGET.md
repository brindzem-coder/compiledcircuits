# Stage 08 — bounded integrity checks and repair

Implementation, automated verification and the seven requested manual client checks completed 2026-10-03. Base commit: `3ef91d2` (stage 06). Scope: `fixes/08-pending-budget.md`, after 06 and before 03 in `implementation-order.md`. Gradle 8.8, Java 17.0.17+10, Minecraft 1.20.1, Forge 47.4.10. No user worlds or baseline backups are modified by the automated scenarios. No commit is created by the agent.

## Scheduler and signal contract

All checks, placements, chunk queries and neighbor callbacks remain on the server thread. The saved-data-owned scheduler has a bounded, insertion-ordered point queue, one full-recheck state per registered network, a stable ID-based audit cursor, and round-robin repair jobs. Before enqueue, membership is resolved through the existing index. Unowned positions create no job. A queued point retains the network and element identity; execution resolves them again. Retiring/replacing an owner, including reusing its integer ID, cannot apply an old result to a new owner.

The point is removed before its check, while an in-flight gate remains active. A callback can therefore enqueue a fresh change without that change being erased on completion. Duplicate waiting events coalesce. Audit advances the immutable element collection of each network, does not restart on unrelated membership mutations, and reads a small network's element at most once per server tick. Completed audit slots can be unused; ordinary audit does not turn a healthy network off.

Known potentially meaningful changes immediately gate `getEffectiveSignal()` LOW through `INTEGRITY_PENDING`, separately from physical `DAMAGED`. Other mandatory checks must finish before HIGH is permitted. Harmless or subsequently cancelled events can produce a short LOW pulse. A physical damage record is created only after examining the actual available block. Tentative snapshot-captured repair writes and rollback writes are not treated as committed changes. The central stage-06 `LevelChunk.setBlockState` observer also schedules integrity, covering ordinary commands and piston/state changes. Direct palette mutations by another mod remain outside that observer's contract.

`integrityUnverified` persists the need for confirmation across saves. A loaded record starts a full recheck and cannot recover HIGH just because its previously invalidated wire is unavailable. The stage-07 initial input/runtime reconciliation is still required. OR logic, exact-state option, repair costs and modification permissions are unchanged.

## Budgets and memory

These server config entries live under `workBudget`; they require world restart. Forge range validators reject out-of-range values and invalid types.

| Setting | Default | Allowed range |
|---|---:|---:|
| `totalOperationsPerTick` | 12,288 | 128–65,536 |
| `totalMicrosPerTick` | 9,000 | 100–100,000 |
| `integrityMicrosPerTick` | 2,000 | 100–20,000 |
| `pointChecksPerTick` | 256 | 1–8,192 |
| `fullRecheckStepsPerTick` | 256 | 1–8,192 |
| `repairStepsPerTick` | 32 | 1–1,024 |
| `auditStepsPerTick` | 128 | 1–4,096 |
| `maxPendingPositions` | 2,048 | 1–65,536 |
| `maxRepairJobs` | 8 | 1–64 |
| `maxRetainedRepairTargets` | 100,000 | 1–1,000,000 |
| `repairLifetimeTicks` | 2,400 | 20–72,000 |
| `repairLifetimeSeconds` | 180 | 1–3,600 |

The operation allowance reserves two slots for coalesced synchronization. Of the remaining slots, integrity receives one sixth, scanning two thirds, and runtime the remainder; unused shares are not lent. Individual lane caps also apply. At defaults the group caps are 2,047 integrity, 8,190 scanning, and 2,049 runtime operations; runtime retains its stricter 2,048 cap, and the default integrity lane caps sum to 672. This prevents a low global limit from letting audit consume every slot before runtime can progress. A step includes transitions/completion, so a repair allowance of 32 does not promise 32 placements.

Tick START reserves up to 16 audit steps within its configured allowance. Tick END rotates point/recheck/repair/audit work; recheck and repair queues rotate networks/jobs rather than draining one large job first. Existing compilation jobs use the same accounting frame and retain their stricter per-job limits. Integrity types together have 2 ms, scanning 5 ms, and runtime 2 ms, subject to the shared 9 ms allowance. Reentrant runtime drains are deferred while another unit executes.

These are cooperative checks between units, not a hard Minecraft tick deadline. A block callback, snapshot encoder, allocation/GC pause or neighbor notification may overrun one unit. No next unit in an exhausted allowance starts. Diagnostics measure units, elapsed work, queue peaks/current states, age, and operation overruns. Instrumentation and framework overhead also contribute to real tick time. The defaults follow the measured 1,000-position baseline described below, rather than assuming that the old full drain was cheap.

## Overflow and unavailable chunks

A full point queue promotes a network to one full-recheck generation, not another position list. New changes increment the generation without restarting its iterator. At the end of a pass, a changed generation requires another pass; only a complete current pass clears the gate. A load notification wakes parked work. Unavailable elements are not read as air or force-loaded, and a parked pass is not retried every tick. Unloading an untouched, previously verified wire alone does not invalidate it.

Explosion/multiplace lists larger than 256 entries increment a dimension generation in constant work. No copy or live event is retained. Every network's guard observes that generation immediately; the fair audit cursor discovers and queues the affected full passes incrementally. This deliberately includes untouched networks in that dimension. Memory for fallback state is bounded by registered networks/dimensions, independent of event count.

## Repair and decompile

The shared stage-02 service validates the actor, every requested ID, permissions and request/work limits before repair admission. There is at most one job per actor and one per network. Queue and retained-target caps reject the entire request with `BUSY`; stage-02 rate limits still apply. Both commands and the GUI use this service, including the single-network repair API.

Damage records use an immutable AVL map. Capturing a job's target root is O(1) per requested network, without copying all broken entries. Subsequent inserts/removals path-copy O(log N) nodes; existing job snapshots stay stable. At most 1,024 network descriptors and 50,000 selected network elements pass the existing request limit. New damage never extends an accepted repair job.

Each target rechecks current owner/record identity, actor/context, loaded dependencies, world border/height, supported state, occupancy and Forge placement protection. The fixed radius-two dependency neighborhood must be available before supported placement helpers run. Already correct elements are not placed again. A changed damage record is skipped. Successful placements enqueue integrity for the position and its six neighbors; `placed` is not a claim of final repaired status.

Loss of rights, dimension/session changes, removal/replacement, timeout or stop prevents subsequent writes. Completed placements stay in the world and are checked normally. The actor receives acceptance/rejection and one terminal summary containing placed, already correct, occupied, unloaded, protected, unsupported, invalid, changed, failed and unprocessed remaining counts. Global damage notices are coalesced. Jobs are transient and do not resume after restart.

Decompile removes every selected network from active membership/runtime visibility atomically, before any neighbor callback. Its element claims, chunk buckets and output notifications retire incrementally. Old cleanup checks identity and cannot erase a replacement's claim. Active and retired runtime work alternate. Retirement admission is capped at 200,000 remaining element/output/chunk-bucket units, returning `BUSY` before mutation when full. Final publication/removal visits admitted network descriptors rather than copying or deleting their entire element collections. Existing atomic metadata operations retain their stage-02 bounds and semantics.

Logical LOW is immediate. Neighbor delivery is budgeted and can take multiple ticks for many outputs; it observes the current signal when delivered. A very short harmless LOW pulse may therefore not reach every neighbor. Once changes stop, available outputs receive their notifications. Unavailable output chunks recover through the stage-07 load path. No fixed latency in milliseconds is promised under continuous changes or unavailable chunks.

## Stage-03 boundary

`DamageNotifications` is only the prerequisite coalescer: one GUI/broken-list refresh and one dimension refresh per eligible tick, using existing packet formats. Repeated element changes no longer rebuild and encode full snapshots per element. Dimension refreshes rotate, and decompile also marks this queue instead of synchronously broadcasting all damage data.

Full snapshot construction/encoding is still a cooperative SYNC unit and can exceed its allowance; the stage-03 protocol/snapshot redesign is not implemented here. The separate compiled-membership visual snapshot path and explicit GUI list requests retain their previous behavior. Real-client transfer costs are not measured by fake-player tests. Stage-07 chunk-event metadata fan-out is also still proportional to affected indexed networks; stage 08 budgets the resulting physical checks, load retries and neighbor work, not the complete Forge event dispatcher.

## Automated evidence

Tests use the isolated `run-stage7-tests` server. Elapsed times are observations; pass/fail checks use work counters, state, real server ticks and convergence after events stop, rather than fragile speed thresholds.

- `baseline-results/stage08-final-write.log`: `build visualChecks runGameTestServer --offline --console=plain`, **BUILD SUCCESSFUL**, all **43** GameTests passed. Environment `CC_RUNTIME_RESTART_PHASE=write` saved the real test world and NBT fixture.
- `baseline-results/stage08-final-read.log`: a separate `runGameTestServer --offline --console=plain` process with `CC_RUNTIME_RESTART_PHASE=read`, **BUILD SUCCESSFUL**, all **43** GameTests passed. `PENDING_RESTART_READ` confirms actual missing-wire reconciliation, no resumed stale job, and HIGH only after a fresh repair. The existing stage-07 two-process 0/15 reconciliation also passed.
- `git diff --check`: clean. Logs are ignored local artifacts. The copied JSON evidence and hashes are retained in `baseline-results/stage08/results.json` and adjacent reports.

Early regression runs exposed obsolete immediate-repair assertions and test-fixture issues after the expanded batch layout. Repair/lifecycle assertions now allow bounded completion on real ticks while retaining immediate LOW and permission/rollback assertions. Async fixtures retain their own chunks; the indirect-input fixture no longer aliases its output position. A repeated GameTest callback uses a fresh wrapper because reusing its scheduler key loses rescheduling. These changes do not relax production chunk availability or signal safety.

New coverage includes duplicate/harmless events; fresh full-pass generations; overflow and cross-dimension repair; fair small/large jobs; partial permission-loss cancellation; exact admission caps; mixed repair outcomes; new damage excluded from a captured target set; immutable snapshot mutation; parked unload/load and reused IDs; timeout/stop; minimum global budget and config boundaries; a million-entry explosion list that throws if traversed; 2,304 output notifications; 50,000-element and 1,024-network decompile; and actual two-process persistence of pending integrity with a queued repair before shutdown. Existing stage 01/02/04/06/07 tests remain in the suite.

### Measurements

The pre-change instrumented test at `3ef91d2` queued 1,000 known missing wire positions. Its full drain took **803.023 ms**, performed all 1,000 point checks in one call and encoded each growing full broken snapshot 1,000 times. This justified reducing per-tick point work and coalescing notifications before selecting the defaults. Source capture: `before-1000-pending.json`.

The final same-size burst (`measurement.json`) finished all 1,000 points with peak **256 point units/tick** and oldest point age **3 ticks**. Its first drain took **1.8101 ms** and had confirmed 349 distinct broken positions, including audit work. Aggregate `processPending` time across calls was **5.1585 ms**; separate coalesced SYNC work took **4.5691 ms**. Those separate paths, runtime initialization, audit and test ticks must not be mistaken for an identical single-call timing comparison with the old synchronous drain. This is a bounded-work/encoder comparison, not a claim about general gameplay FPS or an exact speedup factor.

| Encoder metric for the complete 1,000-position burst | Before | Final capture |
|---|---:|---:|
| Broken list encoder calls | 1,000 | 1 |
| Broken list encoded payload bytes | 49,987,578 | 99,875 |
| Broken position encoder calls | 1,000 | 1 |
| Broken position encoded payload bytes | 4,025,873 | 8,022 |

These are actual encoder invocations/payload bytes, not broadcast-recipient counts or bytes measured on a real client connection. The exact number of coalesced packets can vary with scheduling; an earlier successful run used two snapshots rather than one.

Other final-process observations:

- `overflow.json`: point queue cap 4, point peak 4, full-recheck peak 8, repair peak 2; both dimensions converge, including a new generation during a pass, then all 83 captured missing wires are placed.
- `repair_outcomes.json`: exactly one each of PLACED, CORRECT, OCCUPIED, PROTECTED, UNLOADED, UNSUPPORTED and INVALID; seven captured targets, zero remaining, a new eighth damage excluded, and no chunk acquired for the far target.
- `repair.json`: the small job completes before the large one. Permission-loss cancellation preserves six placements and reports 54 remaining; a later retry completes. `timeout_stop.json`: 21 placements before timeout, 39 remaining, and no writes after stop.
- `minimum_budget.json`: with total cap 128 and audit allowance 128, runtime still progresses (peak 21 units), repair places all 24 wires, and observed total peak is 42 units. This exercises the reserved group shares, not just config parsing.
- `many_outputs.json`: the 2,304-output case immediately becomes logically LOW. Final confirmation and complete LOW notification delivery take **33 server ticks** in this run (23 in the preceding write run). Full-recheck peak is 256 and runtime peak 1,337; eventual lamp OFF/ON and all output delivery are asserted.
- `large_decompile.json`: service return for 50,000 elements takes **0.1564 ms**; the 1,024-network/49,152-element batch takes **5.9272 ms**. Atomic removal work totals **2.0711 ms** across both operations, with no per-element traversal in publication. Retired cleanup reaches but does not exceed 2,048 units/tick; old cleanup preserves a replacement claim. Timed service work excludes test fixture construction and verification loops.

Overruns are real and retained in the evidence: the output case records a **14.7823 ms** maximum unit and **18.7881 ms** maximum accounted frame; the decompile-cleanup case records a **24.2387 ms** maximum unit and **24.4506 ms** frame. Thus the configured 9 ms is expressly not a hard wall-clock bound. These runs do not attribute pauses to a specific callback or GC without a profiler. Subsequent units stop once the shared allowance is exhausted. Stage 03 still needs to address remaining full-snapshot costs.

## Manual client validation — passed 2026-10-03

The user reports that all seven checks from the subsequent chat checklist behaved exactly as expected. That seven-step checklist supersedes the earlier six-item draft in this document. Results are user-observed unless specifically corroborated by the recording below:

1. Breaking and manually replacing a wire: LOW while broken, damage entry clears and HIGH returns without toggling the input.
2. Queued gradual repair and a repeated request: **32 wires** repaired; **two button presses**, with **one BUSY response**. No RATE_LIMITED response was observed or required: the second request is an overlap check, not a request-rate stress test.
3. Occupied position: the free target is restored; stone is preserved, counted as occupied, and the network stays damaged until corrected.
4. New damage during an accepted job: the newly broken wire is excluded from the captured target set and requires a later repair request.
5. Spectator transition during repair: subsequent writes stop, completed placements remain, and an explicit retry after restoring permission completes the remainder.
6. Save and Quit during repair, then reopen: completed placements persist; stale repair does not resume, damaged output stays LOW and a fresh repair succeeds.
7. Decompile during repair: the old compiled owner disappears, remaining placements stop, existing blocks remain and the restored physical circuit can be compiled again without stale ownership.

The user reports completing the checklist including its final settings restoration/recheck. The recorded check 2 used the deliberately slowed **one repair step/tick** setting, independently confirmed by `work.REPAIR.tickPeak = 1`; the recording does not independently verify the later restored config value. Stage 08's requested manual acceptance checks are complete. A remote dedicated-server client session, manual third-party protection-plugin integration and physical chunk travel were not separately reported in this seven-step checklist; automated lifecycle/protection coverage is described above.

### Inspected client recording

Source: `run/debug/compiledcircuits-performance/d0023c35-28d4-4130-90fa-2a29363ca477.json`, copied to `baseline-results/stage08/manual-client-repair-32.json`; capture hash and user-reported outcomes are recorded in `baseline-results/stage08/results.json`.

- Duration **20.754 seconds**, 415 tick samples, zero dropped samples.
- `repair.completedJobs = 1`, `repair.result.PLACED = 32`, `repair.elements = 32`, `repair.remainingAtCompletion = 0`.
- Exactly one concurrent job (`repair.jobsPeak = 1`) and 32 retained targets at peak. Two encoded bulk-action requests corroborate the two button presses; the BUSY response itself is user-reported, not a dedicated diagnostic counter.
- Repair consumes **33 units**, including completion, with peak **1 unit/tick** and maximum observed job age **33 ticks** (about 1.65 seconds at 20 TPS).
- Pending queue peaks at **5 positions**, with **254 duplicates coalesced**. Point checks peak at 5/tick; audit continues at up to 128 steps/tick.
- At the last sampled state, point/recheck/runtime queues, active repair jobs and retained repair targets are all zero. No repair cancellation/failure or operation-overrun counter is recorded in this capture.
- Maximum measured individual budgeted unit: **0.514 ms**; maximum accounted budgeted frame: **1.060 ms**. Measured Forge tick interval p95: **2.396 ms**, maximum **3.839 ms**. This interval is not the complete Minecraft tick, and a 32-wire correctness recording is not a general load benchmark.

The absence of RATE_LIMITED is expected for two clicks and is not a failed check. Initial admission/overlap/rate limits also have automated coverage. No additional recording is required to validate this client scenario.
