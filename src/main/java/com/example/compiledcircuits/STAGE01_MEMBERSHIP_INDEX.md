# Stage 01 — authoritative membership and index (steps 1–3)

## Implemented

`NetworkSavedData` owns one `NetworkMembershipIndex` per saved-data instance. Keys contain canonical dimension and immutable BlockPos. Index construction uses elements without world reads, chunk loading, static ownership or a second NBT copy.

`CompiledNetwork` derives immutable wires/inputs/outputs from elements. The compatibility constructor validates that supplied role sets exactly agree. The role-only constructor materializes deterministic legacy elements. New candidates reject nonpositive IDs, duplicate element IDs/positions (including cross-role overlap), mismatched role sets and known mod block/role mismatches. The elements collection is defensively copied; all membership getters are read-only.

`getMembershipOwners` returns deterministic immutable claims. `findIndexedElementLocation` returns an owner only for one claim; null can mean absent OR ambiguous. Load-time overlapping claims are isolated before constructing the active index; their reservations are kept separately from active ownership. Existing hot lookups have not switched to the index; stage 04 will use this same index.

Lifecycle coverage: atomic single/group add, explicit same-ID replacement, single/bulk remove, post-migration load. Identity-based removal preserves other claims; updates precede synchronization dirty marking. Damage, repair, powered/name/folder changes leave membership intact.

## Safe validation of saved membership

Modern NBT must contain correctly typed membership fields, valid element roles, unique role positions and matching role sets. Legacy records without elements are migrated before validation/indexing. A malformed elements field is not treated as absent legacy data.

`NetworkSavedData.load` preserves rejected network records verbatim under `invalidMembershipRecords`, with a stable per-record UUID and reason. Duplicate network IDs preserve ALL involved records before any map insertion; no arbitrary winner. Surviving valid networks are indexed normally. Repeated save/load retains isolated records without automatically reactivating them. Accessors return defensive copies. Diagnostics are written to the server log.

Known isolated positions and network IDs remain reserved. Records with incomplete reservation scope block all new compilation conservatively until explicit administrative deletion; healthy existing networks remain available. Invalid records are not exposed as ordinary active networks/snapshots. Do not use earlier mod builds on these modified saves: older loaders do not understand the preserved-record field.

Steps 1–3 now implement atomic admission, load-time isolation, reservations, administrative deletion and client blocking status. Manual normal-world checks are recorded below; isolated-record rendering and chunk-unload acceptance remain unverified. Runtime lookup optimization is still reserved for stage 04.

## Verification

`MembershipIndexGameTests` compares index results with a reference scan after mutations/load; covers dimensions, mutable BlockPos sources, immutable views, metadata/damage, conflicting claims, replacement, removal, invalid batch IDs and legacy migration. `authoritativeMembership` tests all nine role overlap combinations, role derivation, duplicate IDs, invalid IDs, block/role mismatch, malformed raw fields, lossless isolation round-trips, defensive raw access, healthy records beside invalid ones, blocked admission and duplicate network ID preservation.

The synthetic VisualScaleTest now supplies authoritative elements through the new constructor instead of inconsistent empty role sets. Algorithm and tested sizes remain unchanged.

Historical first run failed because the new fixture's switch omitted future enum roles; that test was fixed. The earlier foundation then passed build/visualChecks and 3 GameTests. Current validation is recorded below. No interactive client or complete conflict-quarantine acceptance scenario is claimed; no user backup worlds are modified.

Step 1 validation (2026-09-27): `gradlew.bat build visualChecks runGameTestServer --offline --console=plain` on Java 17: BUILD SUCCESSFUL in 55s, exit 0; all visualChecks and 4 GameTests passed, including both membership tests; Stage7 reports 185 checks. `git diff --check` passed. Log: baseline-results/membership-authority-validation.log (local ignored artifact). Step 1 code and automated validation are complete; remaining stage 01 work listed above is not complete. No Git commit created.

## Step 2 — atomic admission (2026-09-27)

All production compilation paths commit through `NetworkSavedData.addNetwork`; `addNetworks` also checks conflicts within an entire candidate group before changing anything. Ordinary addition rejects an existing network ID. `replaceNetwork` is explicit and must target an existing ID; it may reuse only that old network's own claims. Foreign owners still block replacement. Rejected replacement retains the original object and its index entries.

The membership index prepares changes to touched positions before commit. Candidate IDs and positions are processed deterministically, with all roles sharing one occupancy check. Conflict errors include the owner name/ID, dimension and position. Successful commits update saved networks, membership and the ID counter before synchronization invalidation. Membership mutations enforce the owning thread.

`getNextNetworkId` previews rather than consumes an ID. Successful admission advances the persisted counter; rejection does not. Loading repairs a stale counter against active IDs. Zero is the persisted exhausted-ID sentinel, including after the maximum-ID network is removed. No wraparound or silent overwrite is permitted.

Both command and GUI server compilation catch validation/admission errors and show the reason. GUI conflict preflight also uses the all-role membership index. Other runtime hot lookups remain stage 04 work. Tests cover all nine inter-network role combinations, batch self-conflicts and duplicate IDs, failed replacement, same-position role changes, dimension changes, stale/exhausted IDs, unchanged dirty/sync state after rejection, and command/GUI rejection of a stored wire whose physical block became an input. GUI success and existing command-success tests exercise normal admission.

Step 2 validation (2026-09-27): Gradle 8.8 / Java 17, `gradlew.bat build visualChecks runGameTestServer --offline --console=plain`: BUILD SUCCESSFUL in 2m 13s, exit 0. All 6 GameTests passed, including atomicAdmission and compilationAdmission; Stage7 reports 185 checks. visualChecks passed, including 15 mutation checks. `git diff --check` passed. Log: baseline-results/membership-admission-validation.log (local ignored artifact). No interactive client check or complete stage 01 quarantine is claimed. No Git commit created.

## Step 3 — saved conflict isolation (2026-09-27)

Load validates every record before active-index construction. All networks sharing a position (including cross-role claims and overlap chains) are excluded from active data, without choosing a winner. A candidate overlapping an already isolated record is also isolated. Original NBT, including powered/broken state, remains verbatim in a record with a stable UUID and reason. Duplicate network IDs remain separate records, including loaded candidates whose ID is already held by an isolated record even at disjoint positions. Missing/duplicate isolation UUIDs receive unique administrative identities. Unreadable network/isolation lists are preserved as raw evidence with unknown reservation scope.

`MembershipReservations` takes the union of known element and role-set coordinates. It retains every claimant; removing one record cannot free another record's position. Known reservations allow unrelated compilation and compilation at the same coordinates in another dimension. Incomplete coordinates/dimensions trigger a global admission block. Isolated network IDs contribute to the next-ID counter and cannot be added explicitly. No isolated record is automatically reactivated after another is removed or after save/load.

Runtime and repair operate only on active networks: an isolated output resolves to no active network and therefore returns LOW; automatic integrity repair cannot modify or reactivate its raw evidence. Healthy networks continue working. Deferred server notifications update only loaded neighbors of known blocked positions after load and chunk load; the new notification code does not request unloaded chunks.

Operator commands (permission level 2):
- `/circuit conflicts list` — record UUID, original name/network ID and reason.
- `/circuit conflicts remove <record-uuid>` — permanently remove exactly that isolated record and its own claims. This is explicit deletion, not recovery, repair or reactivation. Inspect/back up evidence before choosing to delete it.

Protocol **10** replaces protocol 9 on both client/server. Snapshot parts carry active membership and a separate deduplicated list of blocked positions plus an unknown-scope flag. Both collections share the existing packet/assembly budgets and commit together after complete assembly. If reservations would overflow the shared entry budget, healthy membership is still sent and client unowned positions are conservatively marked blocked instead of failing the entire snapshot. Healthy snapshots remain valid; previous active client membership is removed when the replacement snapshot commits. Reservations never appear as ordinary active ownership. Blocked circuit positions have orange outlines and do not enter uncompiled hover traversal; targeting them with the selector displays a blocking message. For unknown scope, unowned positions are conservatively displayed as blocked while known healthy membership remains usable. Existing explicit-selection/broken-outline priorities remain unchanged.

Automated coverage adds all nine saved cross-role conflicts, original powered-NBT preservation, repeat load/save, overlap chains, known/unknown scope, per-record release, duplicate network IDs, persisted-reservation overlap, reserved-only multipart snapshots, actual output LOW and healthy output HIGH, no automatic repair, admin permission checks, packet round-trip/byte accounting, and atomic client transition from active to blocked membership.

Final validation (2026-09-27): Gradle 8.8 / Java 17, `gradlew.bat build visualChecks runGameTestServer --offline --console=plain`: BUILD SUCCESSFUL in 51s, exit 0. All 8 GameTests passed (Stage7: 185 checks); visualChecks passed, including packet: 12, client membership: 26, mutation: 15, hover: 124 and priority: 15 checks. `git diff --check` passed. Log: baseline-results/membership-quarantine-validation.log (ignored local artifact). Interactive rendering, login/reconnect and unloaded-chunk gameplay acceptance have not been manually checked; automated cache/server tests do not replace those checks. Tests use synthetic NBT and the disposable GameTest world, not baseline backup worlds. No Git commit created.

## User-reported manual acceptance — 2026-09-28

The user confirmed the following in the current game build (reported in chat; not independently observed by the agent):
- Breaking a compiled wire shows the red damaged-position indication. Another network cannot compile onto that reserved position. No new isolated record appears, as the conflicting admission is rejected before saving.
- Restoring the correct wire repairs network A and restores operation.
- Decompiling A releases its former positions; compiling again there succeeds and input/output switching works.
- Leaving and re-entering the world retains the compiled network and its operation. Recompiling its occupied positions remains rejected.

Manual compilation via the GUI was not available in the reported scenario; server-side named compilation remains covered by automated tests. The user explicitly chose to skip manually constructing a conflicting NBT world and to retain the isolation mechanism. Orange isolated-record rendering and isolated-record reconnect/chunk-unload behavior are therefore not marked as manually passed. Ordinary save/re-entry success does not prove those isolated-record scenarios.

Status: implementation and automated checks complete; the above normal-world manual checks passed by user report. Manual isolation acceptance remains incomplete/skipped, without a reported failing check. Next planned implementation is 04-server-lookups.md, reusing the same index; do not claim every stage 01 acceptance scenario was verified. No new build or code change was needed to record these results, and no commit was created by the agent.
