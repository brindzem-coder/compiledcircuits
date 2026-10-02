# Stage 07 — runtime lifecycle

Date: 2026-10-01. Base commit: 9a54b0c. Scope: fixes/07-runtime-lifecycle.md, following implementation-order.md. Implementation and automated validation complete; manual client checks 1–7 passed by user report on 2026-10-02; the requested dedicated-server chunk-separation checks also passed by user report on 2026-10-02. The requested delayed-feedback oscillator acceptance also passed by user report on 2026-10-02. Status: implemented and verified for the documented automated and manual scenarios. No commit created by the agent.

## Findings and contract

The previous runtime exposed saved `powered` directly at an output, stopped its OR scan at the first HIGH, and had no chunk lifecycle reconciliation. Initial inspection established those code paths; new regression scenarios exercise their required replacements. A real chunk unload test additionally exposed that Forge ChunkEvent.Unload occurs after loss of FULL accessibility, so that event alone was insufficient.

`powered` is the input OR, retained in NBT for compatibility. `runtimeReady` and `inputsAvailable` are independent, nonpersistent gates. Broken records remain the independent physical-integrity source. Effective signal is 15 only when runtime is verified, every input dependency is available, the network is undamaged, and input OR is true. Otherwise it is 0. Isolated records from stage 01 have no active runtime or output owner and remain LOW.

| Situation | Effective output | Recovery |
|---|---|---|
| New admission / data load / available physical members awaiting validation | LOW, INITIALIZING | Bounded physical checks and fresh input read |
| Saved or confirmed physical damage | LOW, DAMAGED | Actual matching block state; unavailable broken members stay broken |
| At least one required input dependency unavailable | LOW, UNAVAILABLE after evaluation | Dependency return triggers reconciliation without lever action |
| Verified, available, undamaged | OR of all inputs, 0 or 15 | Normal input changes |
| Verified wire chunk unloads | Existing confirmed output retained | No invented damage; verify its physical members on return |
| Output chunk unloads | Do not read/notify it | Check and notify its neighborhood when available again |
| Isolated/conflicting data | LOW, BLOCKED by absence from active membership | Explicit stage 01 administration |

If multiple causes apply, clearing one does not clear the others. Saved HIGH is never an activation permission. A returning physical chunk may temporarily put the network LOW while its members are checked, including a previously healthy wire/output chunk. No HIGH is emitted from unchecked returned members. The GUI and `/circuit list` use effective output; manager headings explicitly identify the last received output state, since its existing list is a snapshot rather than a live signal subscription. Known broken networks are displayed OFF. There is no new packet format; protocol remains 11.

## Ownership, dependencies and events

NetworkSavedData owns a derived NetworkRuntime. Add/load construct its index; replace/remove discard the old identity before any later callback. The index maps dimension+chunk to affected runtime entries and physical member lists, using authoritative CompiledNetwork elements. It is not another position-ownership authority. Unload tombstones survive removal/replacement until the chunk is actually unavailable or returns.

Input dependency chunks include the input, its six neighbors, and their six neighbors. This follows the locally inspected Forge 1.20.1-47.4.10 SignalGetter bytecode: getBestNeighborSignal -> getSignal -> shouldCheckWeakPower -> getDirectSignalTo. Potential chunk dependencies are indexed with topology. Each input reads all six sides even after HIGH; the indirect ring is required only for a neighbor whose shouldCheckWeakPower returns true. Air does not invent an unavailable indirect dependency. A nonloading SignalGetter view uses getChunkNow, rejects unavailable reads, and tracks unsuccessful reads instead of treating missing space as a usable LOW value. Output endpoints recognize this view and still use the authoritative direct membership lookup. The standard vanilla/mod endpoint paths are covered; arbitrary third-party signal blocks with additional world dependencies have not been certified.

Output-neighbor chunks are indexed as notification dependencies as well. Chunk callbacks never read blocks or notify neighbors. They invalidate affected state and enqueue part iterators, without copying/scanning every element in that chunk. Load retries wait until FULL access is available. Off-thread callbacks are moved to the server thread.

A required server Mixin observes ChunkMap.onFullChunkStatusChange, together with Forge Load (the later physical Unload event is not a second availability authority). Demotion gates input-dependent networks before the next output query even while a chunk object is still accessible. Promotions enqueue checks. The Mixin is included in both development launches and the packaged manifest; it shares the generated refmap with the existing client observer. No client classes are referenced by the server Mixin.

Endpoint/wire onPlace/onRemove hooks invalidate physical checks, including direct block changes; existing event checks and the audit remain. The worker does not accept tentative Forge repair snapshots or their restoration as confirmed world state. Changing an input discards the partial input scan. Changed physical members are rechecked before activation. No world/chunk references or runtime readiness are persisted.

## Budget and cascade semantics

One server runtime has WORK_PER_TICK=2048, shared by immediate input requests and the END-tick continuation. A work unit checks one physical member, reads one input with a fixed vanilla neighborhood bound, processes one output notification (up to six available neighbors), or advances/commits one job phase. Each visited input is checked for availability even after another input reports HIGH. The queue is deduplicated and round-robin; callbacks enqueue instead of recursively draining. Each tick also retries at most 128 pending chunk loads and cleans at most 128 unload tombstones.

Small available circuits settle within the same tick while budget remains. A longer input scan/cascade retains the last confirmed ordinary signal until its next step; initialization, physical revalidation and dependency loss gate LOW immediately. Stable feedback can retain a self-sustained HIGH; the runtime does not invent cycle breaking. Bounded work limits a nonconverging cascade and gives other queued networks turns. Repeated unchanged checks do not notify outputs again.

Old output positions after replace/decompile have a separate retiring notification cursor sharing the same 2048-unit budget. They query current ownership through normal neighbor callbacks; no old computed signal is applied. State/index removal precedes these callbacks. Server stop clears queues, derived indexes and readiness.

This is the minimal runtime scheduler required by 07, not completion of 08 or 03. Existing pending integrity work and damage broadcasts still have their previous costs. The work-unit cap does not claim a hard wall-clock bound for vanilla callbacks, third-party block logic, or the entire tick. Scanning/commit admission remains stage 06 work; general repair/integrity jobs and packet batching remain 08/03.

## Verification and evidence

Toolchain: Gradle 8.8, Java 17.0.17+10, Minecraft 1.20.1, Forge 47.4.10.

RuntimeLifecycleGameTests cover compressed NBT disk round trips, saved HIGH/actual LOW, saved LOW/actual HIGH, lamps, ordinary same-tick changes, mixed available/unavailable inputs, indirect strong-power dependencies at a chunk edge, returned damaged wires, repair while another input is unavailable, returned outputs, identity replacement, self-feedback, two-network feedback, all 36 input/wire/output load/unload order combinations, saved unloaded broken records, unchanged rechecks, and replacement during pending unload.

Additional separate batches cover 5003-member initialization alongside a small circuit, changing an already visited physical member while initialization is pending, a 512-network chain, and actual server chunk removal/return. Only test setup uses explicit chunk loads/tickets; runtime code never creates tickets. The dedicated test fixture releases its temporary forced chunks on completion.

A two-process scenario uses CC_RUNTIME_RESTART_PHASE=write for the first GameTest server, then read for a fresh JVM using the same isolated world. The first process saves world blocks and a named NetworkSavedData fixture with deliberately stale opposite OR values; the next process reads both from disk and asserts outputs 0/15 without input changes. This tests the real world/disk/process boundary; it is not just assignment to a field. The read fixture restores the original stale saved values afterward so repeat reads remain reproducible.

Local logs: baseline-results/runtime-validation-final.log and baseline-results/runtime-restart-read.log. Raw diagnostic JSON is in run-stage7-tests/debug/compiledcircuits-performance. These are disposable headless Forge GameTest worlds, not the user's baseline backups.

Final result: `build visualChecks runGameTestServer --offline --console=plain`, with CC_RUNTIME_RESTART_PHASE=read: BUILD SUCCESSFUL in 59s, exit 0. All 25 required GameTests passed, including the existing Stage7 185 checks. The final run recorded:

- Initialization/repair: 2048 peak work units per tick, 5021 total runtime units, 48 signal-view block reads, 7 runtime integrity reads, 4 input evaluations, and 6 output notifications. The large network first became ready after 2 ticks; the small network progressed in the first tick. Runtime callbacks totaled 12.099 ms across this capture (not a per-tick bound).
- Chain: 512 networks converged in 4 ticks without recursive overflow.
- Actual unload/return: LOW after real FULL-access loss, no fake damage, then automatic HIGH after return; complete scenario took 38 test ticks, including chunk work.
- Real two-process restart: persisted world and stale opposite OR values reloaded; resulting outputs 0/15 without input changes.
- All 36 lifecycle order combinations, preserved unloaded damage, unchanged rechecks without output notifications, and replacement during pending unload passed.

These are synthetic scenario observations, not a baseline-world speedup claim. Timing samples are diagnostic only; they do not establish a reliable p95/p99 comparison. The initial test-only bootstrap failure was fixed by resolving registry keys only on a running server; asynchronous test polling was changed to schedule fresh callbacks rather than overwriting/removing its own scheduled instance. Actual unload testing drove the FULL-status hook and removal of the later physical Unload event as a competing availability source. The acceptance assertions were retained.

## Manual client acceptance — 2026-10-02

The user reports that all seven requested checks passed: compilation with HIGH and LOW inputs; ordinary switching including the two-input OR truth table; full client restart with HIGH and LOW; leaving the network area and returning without a lever change; damage followed by restart and final repair; decompile/recompile with an enabled input; and the manager's effective output after refresh. This is user-reported interactive evidence, not an agent-observed run. Leaving the area alone does not establish actual chunk unload; actual unload/return was separately exercised by GameTests.

## Dedicated-server acceptance — 2026-10-02

The user reports reproducing the requested checks and obtaining all expected results: the loaded output goes LOW when a required input becomes unavailable, no physical damage is introduced by unload, and the output automatically recovers when the input returns without toggling its lever. The reported checks include the two-input case: another available HIGH input does not override the missing-input gate. This records the user's report; player count, chunk-loading method and independent unload telemetry were not supplied and are not inferred.

## Delayed-feedback oscillator acceptance — 2026-10-02

The user reports completing the requested procedure successfully: a compiled Input-to-Output network feeds back through vanilla redstone and four repeaters at maximum delay, started by a stone-button pulse. The procedure called for 3–5 minutes of sustained switching with the circuit loaded, normal operation of a separate compiled network alongside it, no hang/crash or spurious damage messages, and cessation after breaking the external feedback path and allowing the remaining pulse to clear. This is user-reported evidence; no exact elapsed time or timing telemetry was supplied.

This confirms a persistent cycle with intentional repeater delay. It is not evidence of an indefinitely oscillating zero-delay circuit. Stable direct feedback and a 512-network cascade were covered separately by automated tests.

## Acceptance scope

All requested manual scenarios are reported passed, alongside the documented automated checks. Stage 07 is ready to commit within this verified scope. Particular third-party compatibility and a baseline-world performance comparison are not claimed. No further optimization stage was implemented as part of this acceptance update.
