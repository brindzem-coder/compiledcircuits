# Stage 04 — direct server membership lookups

Date: 2026-09-28. Base commit: d20d5db. Implementation follows fixes/04-server-lookups.md after stage 01. Changes are uncommitted.

## Implementation

`findElementLocation`, `findNetworkContaining`, `findNetworkByInput` and `findNetworkByOutput` use the existing per-SavedData membership index. Each performs one index probe; absent positions do not fall back to a network/element scan. Input/output lookups validate the authoritative element role. `CompiledNetwork.getElementAt` uses a private local position map constructed from the same immutable membership; it is neither a second server ownership index nor separately serialized.

The stage 01 admission/load/remove/replace lifecycle remains authoritative, including conflict isolation and reservations. Rename, folders, damage, repair and powered changes do not rebuild membership. Queries do not read worlds or reference/load chunks. Signal evaluation, OR semantics, damaged LOW behavior and notifications are unchanged.

`scheduleCheck` rejects unowned/isolated positions before allocating queue entries. Repair still schedules the repaired block and its six neighbors; owned neighbors pass the filter. Deduplication remains a position set. Execution calls `findElementLocation` again and only reads block state after confirming current membership and a loaded chunk. It never retains the originally queued network object. Audit still visits known elements directly; it was not replaced by index rebuilding.

Diagnostics retain lookup calls/hits/misses/timing and add per-method `indexProbes`. `pending.scheduleCalls` counts all scheduling attempts, `pending.unownedSkipped` counts filtered attempts, and `pending.uniqueEnqueued` counts unique admitted queue entries. Old `networksVisited` / `lookup.elementsVisited` scan counters are no longer emitted by these paths; absent counters mean zero for comparison. `lookup.localElement.indexProbes` covers the local query.

## Validation

Passed: Gradle 8.8 / Java 17, `build visualChecks runGameTestServer --offline --console=plain`: BUILD SUCCESSFUL in 1m 28s, exit 0; all 9 GameTests passed, including Stage7 with 185 checks. `git diff --check` passed. Log: baseline-results/server-lookups-validation.log (ignored local artifact).

Existing membership lifecycle tests now compare public lookups and input/output role filters with a reference scan, across mutations, dimensions, legacy migration and save/load. The new ServerLookupGameTests exercises 5000 elements as one network and as 500 networks, sending 1000 foreign positions through event scheduling plus containing/input/output queries in each configuration. It asserts constant probe counts, no scan counters and no queued foreign positions, without timing thresholds. It also exercises queued deletion/replacement, damage/repair, and retained membership in a chunk that stays unloaded.

No percentage speedup, MSPT improvement or baseline-world performance improvement is claimed. Tests exercise the common server scheduling path used by break/place/explosion handlers; they do not simulate an actual large explosion. Manual gameplay and comparable performance captures on the user's baseline worlds remain separate follow-up checks. Stage 01's manually skipped isolation visualization remains unverified.

Measured deterministic test counters, for EACH topology (1 x 5000 and 500 x 10): 1000 foreign scheduling attempts skipped, 4000 total probes across the four query methods, zero foreign queue entries and zero network/element scan counts. These are operation counts from synthetic server tests, not timings or baseline-world speedup measurements.

## Manual client results — 2026-10-01

User reported all requested checks 1–5 passed on the updated client: two-input OR combinations; damage/repair of wire, input and output; unrelated block placement/breakage; travel away and return with signal switching; decompile/recompile and world exit/re-entry, including rejection of duplicate compilation. These are user-reported results, not independently observed. The travel test does not independently establish that chunks actually unloaded.

Comparative baseline-world performance captures have not yet been collected for stage 04. Deterministic automated operation-count evidence remains recorded above. Stage 01 isolated-record visual checks remain skipped by user choice. No code change or build was needed to record this report.
