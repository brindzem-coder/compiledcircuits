# Stage 06 — bounded compilation without chunk loading

Date: 2026-10-02. Base commit: 2bd117d. Scope: fixes/06-scanning-chunks.md, after stage 07 in implementation-order.md. Implementation complete; automated results below. The user subsequently reported successful client checks 1–5 from the chat checklist; the cancellation-reason qualification and remaining manual scope are recorded below. No commit created by the agent.

## Availability and consistency

Compilation runs exclusively on the server thread. Each needed read uses the stage 07 availability contract: ServerChunkCache.getChunkNow, an explicit LevelChunk.getFullStatus >= FULL check, and the runtime's FULL-demotion tombstone. The FULL check also strengthens the shared runtime reader. The actual 1.20.1/Forge 47.4.10 mapped bytecode was inspected: getChunkNow checks the main thread, its cache, visible holder/currentlyLoading, and already-completed FULL future via getNow. It neither creates tickets nor calls the requesting/waiting chunk API. The explicit FULL check excludes readable objects whose current status is insufficient. A job retains positions and immutable BlockState references, never a LevelChunk between ticks.

The current block's canWireConnect is checked before reading its neighbor, then the neighbor must allow the reverse direction. Unknown potential continuation causes INCOMPLETE_UNLOADED; it is never substituted with air. The message asks the player to visit the area normally and retry. An out-of-height neighbor needs no chunk access. An invalid world-border start/frontier is explicitly rejected; final member bounds are checked again before publication, including a border that moved during the job.

A required server Mixin observes LevelChunk.setBlockState at HEAD, comparing old/requested states. The inspected Level.setBlock bytecode calls this method; normal command, piston, explosion, and mod setBlock paths therefore share the observer. A cancelled/tentative write may conservatively invalidate a job. Mods directly modifying palette/section storage or illegally changing a server world off-thread are not claimed to be covered.

Chunk subscriptions act as revision witnesses: every actual attempted state change or FULL transition in an observed chunk sets an irreversible changed flag on each subscribed job. Subscriptions include rejected frontier neighbors, so adding a branch to an earlier boundary is detected. Each hook visits at most eight jobs. Unload followed by reload cannot clear the flag. Validation and final publication check the flag; there is no automatic retry. Unrelated changes in the same chunk can deliberately produce CHANGED_DURING_SCAN. Stop clocks and other changing mechanisms near the graph while compiling, then restart them after completion.

## Jobs, preparation and atomic publication

Commands and CompileNamedC2SPacket/NetworkCompiler use the same NetworkOperations COMPILE service. Stage 02 field/rights/request/work limits run before job allocation or world reads. Opening the name form performs no scan. NetworkScanner retains only its compatibility ScanResult value for indexed conflict queries; the synchronous BFS has been removed. CompiledElementFactory remains available for internal/test callers, but no user compilation path invokes it.

A successful request initially returns QUEUED, not a completed network. One active job per player; another request returns BUSY. Changing or clearing the selection (even reselecting the same coordinates), dimension/session changes, loss of permission and server stop cancel the work. Context and rights are checked before every productive step. The captured name is already validated by stage 02.

Phases: SCANNING → VALIDATING → ASSEMBLING → PREPARING → INDEXING → FINAL_VALIDATING → COMMITTING → CLEANUP. Rejection also enters bounded CLEANUP. SCANNING deduplicates at enqueue and counts all directional/frontier work. A sorted position map preserves deterministic element IDs without a final bulk sort. VALIDATING rereads one captured state per unit; ASSEMBLING creates one element snapshot per unit. PREPARING stages one membership claim and the element's fixed-neighborhood runtime dependencies. INDEXING inserts one prepared chunk bucket per unit. FINAL_VALIDATING checks dependency availability incrementally; change witnesses remain armed through publication.

Pending claims reserve positions against overlapping jobs and ordinary admissions, but authoritative membership queries filter unpublished candidates. They have no permanent ID, no entry in the saved network map, no saved record/dirty flag, and no active runtime output. A sealed builder transfers ownership of prepared collections without a bulk copy. COMMITTING performs a bounded number of map/state operations: assign the current permanent ID, publish the completed membership, activate the prepared runtime entry, advance the ID and mark data/sync dirty once. It does not loop over candidate elements; standard map insertion remains amortized rather than a hard real-time guarantee.

Failure removes staged claims and runtime buckets incrementally, including pending chunk-load retry entries. Cleanup also unlinks revision subscriptions. No ID is consumed on rejection. A player remains BUSY until cleanup completes. At server stop cleanup is synchronous because no later ticks exist, and its total storage is bounded by the same job/memory caps. Data saved before shutdown cannot contain pending candidates.

After publication the existing stage 07 runtime performs bounded initialization, keeping outputs LOW until ready; it does not gain another signal algorithm. Input OR and 0/15 semantics, unavailable dependency recovery and physical damage gates remain unchanged. Stage 03 snapshot construction/delivery and stage 08 general integrity queues remain separate work. The existing full visual snapshot builder is not made incremental by stage 06, and fake-player GameTests do not measure real-client snapshot delivery.

## Limits

| Limit | Value |
|---|---:|
| Elements per job | 50,000 |
| Frontier operations per job | 350,003 (start, dequeue, six directions and termination) |
| Observed chunks per job | 8,192 |
| Active/queued jobs | 8; one per player |
| Retained discovered elements across jobs | 100,000 |
| Work per job per tick | 2,048 units |
| Work across compilation jobs per tick | 8,192 units |
| Round-robin quantum | 64 units |
| Time allowance per job / all jobs per tick | 2 ms / 5 ms |
| Job lifetime | 2,400 server ticks or 180 seconds wall time |
| Existing runtime work per tick | 2,048 units, separately shared by runtime operations |

Element, frontier and chunk caps are independent: a very spread-out graph can reach the chunk cap before 50,000 elements. Memory is bounded by retained entries and fixed per-element structures, not an exact JVM byte count. There is no unbounded waiting list. Time budgets are cooperative checks between bounded units, not preemption of a JVM pause, hash resize or block callback. Work counters are deterministic acceptance checks; elapsed times are measurements, not flaky time asserts.

Result reasons include INVALID_START, INCOMPLETE_UNLOADED, TOO_LARGE, CHANGED_DURING_SCAN, CONFLICT, FORBIDDEN, BUSY, TIMEOUT and CANCELLED. Existing INVALID_ARGUMENT/RATE_LIMITED validation still applies. Two overlapping jobs cannot both commit; with more complex overlaps, conservative temporary reservations may reject both, requiring an explicit retry.

## Automated evidence

See the generated measurement section below. Gradle 8.8, Java 17.0.17+10, Minecraft 1.20.1, Forge 47.4.10; offline build, visualChecks and isolated runGameTestServer. User worlds and baseline backups were not used.

New GameTests cover:
- Small graph roles, exact endpoint facing snapshot and deterministic IDs; two overlapping actors and one final owner/ID.
- Actual setblock state change, a new branch on an already checked boundary, FULL unload/reload, selection replacement, rights loss, session cleanup, timeout and shutdown.
- Hidden partial membership and runtime indexes, rollback after a partial runtime bucket plus a load notification, and successful retry without stale reservations/load retries.
- Unloaded start and potentially connected unloaded neighbor, zero forbidden ChunkEvent.Load observations and unchanged forced tickets. Test fixtures explicitly acquire only a level-33 FULL ticket for the available center; production requests none.
- Test-only directional endpoints at min/max build height, skipping disallowed missing horizontal chunks and rejecting a non-mutual connection. These test blocks are absent from the shipped jar.
- Queue cap of eight, immediate invalid height/border starts, and the stage 02 early work limiter.
- Two concurrent 50,000-element scans, a small job that finishes first, deduplicated cycles, the global retained-element cap, a defined 50,001-element refusal, and full post-commit runtime initialization of the accepted maximum graph.

The earlier permission, membership, diagnostics, lookup, signal-neighbor and runtime lifecycle regressions also run. The existing actual chunk-unload test runs; the optional two-process stage 07 restart fixture is not rerun in write/read mode for stage 06. Timeout is tested by advancing a test job's recorded age, not by sleeping three minutes. Simulated FULL transitions exercise scan invalidation; command writes exercise the real central block hook. Physical piston/explosion scans, real disconnect/dimension UI flows and real-player multiplayer compilation remain manual acceptance items rather than claimed automated coverage.

The old compile.command.nanos and compile.nanos counters now measure request handling only. compile.command.queued is separate from compile.completed. Full asynchronous work is represented by compile.job.cpuNanos (sum of timed steps), phase counters and compile.job.wallNanos (successful jobs' end-to-end latency, summed across jobs). Overlapping wall durations are not server CPU time. These new metrics must not be compared directly with the old synchronous full-command timer.

## Recorded validation — final run

`build visualChecks runGameTestServer --offline --console=plain` passed; all **30 required GameTests** passed. The packaged jar contains the new required server Mixin and its remapped `LevelChunk.setBlockState` target (`m_6978_`); test-only blocks are absent. The no-force hook recorded **0** loads for unavailable start/neighbor positions and no forced-ticket changes. `git diff --check` passed.

Evidence lives in project-root `baseline-results/stage06-safety.json`, `stage06-maximum.json` and `stage06-validation.json`; the ignored raw log is `stage06-validation.log`. The maximum capture includes two competing 50,000-element jobs, one small successful job, the rejected 50,001-element attempt, cleanup and initialization. These are aggregate test-window times, not one job's latency or an in-game baseline comparison.

| Phase | Work units | Aggregate measured ms |
|---|---:|---:|
| SCANNING | 1,050,007 | 1230.409 |
| VALIDATING | 100,005 | 112.592 |
| ASSEMBLING | 100,005 | 612.414 |
| PREPARING | 50,005 | 32.897 |
| INDEXING | 22 | 0.046 |
| FINAL_VALIDATING | 27 | 0.028 |
| COMMITTING | 2 | 0.217 |
| CLEANUP | 78 | 0.222 |

- Maximum atomic publication body: **0.1296 ms**. COMMITTING phase time also includes result notification and cleanup transition.
- Work peaks: **2,048** per job/tick, **4,079** across scan jobs/tick; three simultaneous jobs. Small-job-first fairness assertion passed.
- Retained-element peak: **100,000**; frontier peak: **910**. Each maximum graph enqueues/dequeues its members once despite cycles.
- Full runtime initialization reached READY: **24 ticks**, **50,002** integrity reads for the large and small successes; runtime peak **2,048** units/tick, **31.129 ms** aggregate timed runtime work.
- Observed scan-tick maximum: **51.535 ms**; largest individual timed step **51.037 ms**. GC logging recorded pauses up to **50.709 ms** within this capture. This supports the explicit limitation that cooperative 2/5 ms allowances are not a hard JVM wall-time guarantee; no claim of every tick under 5 ms is made.

## Manual client acceptance — user report

The user reported that all five client scenarios from the chat checklist were observably exercised and behaved as described. This numbering refers to the actual chat instructions, not the earlier draft checklist in this file. Results are user-reported, not agent-observed telemetry.

1. Small circuit: queued/completed messages, normal input/output operation, decompilation and recompilation passed as instructed.
2. Large circuit and duplicate request: queue was visible; the second request explicitly returned BUSY. Completion and operation passed as instructed.
3. Selection-change cancellation scenario: the user observed cancellation with a reason describing changes. No exact status code was supplied. Record the safe rejection/retry outcome as reported, but do not label this as an independently demonstrated CANCELLED selection-hook result: chunk-change invalidation could have won the race. Explicit selection cancellation and cleanup are also covered by automated tests.
4. World-change scenarios: cancellation with a changes-related reason was observed; the user reported the instructed break/add-branch and subsequent stable retry behavior passed.
5. Unloaded selected circuit: the user reported the instructed refusal while away and successful retry after returning/loading the area. This is the unloaded-start scenario; it is not a separate manual demonstration of a loaded start with an unloaded continuation. The latter is covered by automated tests.

The implementation, automated suite and these five client scenarios are verified to the scope above. Dedicated-server two-player overlap, physical piston/explosion changes during a scan, and real disconnect/dimension/spectator transitions during pending work have not been reported as manually exercised for stage 06. Automated overlapping actors, central command writes and cancellation tests provide the separately described coverage. Performance measurements in the user's prepared worlds have not been repeated after stage 06. The validation JSON records the automated-run snapshot, when manual checks were still pending; this section records the later user report.

Conservative cancellation near changing blocks in the same chunk is expected. It never authorizes publishing a partial graph or treating an unavailable chunk as air.
