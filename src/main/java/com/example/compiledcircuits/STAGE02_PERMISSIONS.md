# Stage 02 — shared server permissions and admission

Date: 2026-10-01. Scope: fixes/02-permissions.md. Base commit: 8cbd338. Implementation and automated checks complete; manual client acceptance pending. No commit created by the agent.

## Policy and entry points

Networks remain shared. Ordinary survival/creative players with mayBuild may mutate them. Spectators cannot mutate, including operators; players without mayBuild cannot compile, decompile, edit networks/folders or repair. Read operations remain available to spectators. Existing operator-level-2 access to isolation diagnostics is retained. Removing isolated records requires level 2 and rejects a spectator actor; it does not require mayBuild for an otherwise authorized non-spectator administrator. Console support for list, rename, folder-path move and isolation administration remains; operations needing selection, GUI or repair require a real server player. Null actors are rejected, not converted into fake players.

All user requests use `NetworkOperations.execute`, returning OK / FORBIDDEN / NOT_FOUND / INVALID_ARGUMENT / CONFLICT / RATE_LIMITED. Permission, raw fields, target existence, work admission and mutation are separate steps on the server thread. Low-level SavedData APIs remain for load, migration, audit and tests; current command/packet user mutations no longer call them directly.

| Entry | Shared operations |
|---|---|
| NetworkActionC2SPacket | Highlight; network rename/move/decompile; folder create/rename/move/delete |
| NetworkBulkActionC2SPacket | Highlight; network move/decompile/repair; folder move/delete |
| CompileNamedC2SPacket / NetworkCompiler | Named compilation through the same service as command compilation |
| CircuitCommands | Compile, decompile, rename, folder-path move, list/GUI/selection/debug, isolation list/remove |
| Public NetworkRepairManager.repairNetwork | Defensive permission, identity, size and shared quota checks before the package-private authorized worker |

Packets derive the actor from Context.getSender. Permissions are checked inside the queued work. All registered packets now declare PLAY_TO_CLIENT or PLAY_TO_SERVER; C2S handlers also explicitly reject wrong-direction contexts. Protocol is **11** on both sides (was 10). No membership NBT format change is introduced here.

The compile-name dialog no longer performs a redundant preflight world scan; submission does the authoritative bounded scan and admission. Opening the dialog still requires mutation permission and a server-side selection. Command full-operation and named-compile diagnostics remain present. Scanning refuses unloaded start/neighbor chunks instead of forcing chunk loading.

## Bounds and atomicity

Named constants in `OperationLimits` define:

| Bound | Value |
|---|---|
| Network/folder name (raw length before trim) | 64 characters; stored trimmed, nonempty |
| Command folder path | 256 characters; each component follows folder-name rules |
| Folder separators | / and backslash forbidden within a name |
| Raw bulk ID count | 1024, checked before allocation/deduplication |
| Target membership per request | 50000 elements across all selected networks |
| Metadata entries / broken-entry response work | 50000 each before full response construction |
| Requests per actor per window | 20 |
| Admitted work units per actor per window | 200000 |
| Window | 20 server ticks, not wall-clock seconds |
| Error notices | At most one per actor per 20 ticks; no full list on rejection |
| Limiter state | At most 4096 actor entries; logout, player clone and server stop cleanup |

Work units conservatively charge metadata entries, broken response entries and selected membership sizes; compile reserves its full 50000-element scan budget before scanning. Commands and packets share the actor budget. Read requests are bounded too. Server/console actors use their server's bucket; player objects separate sessions. If the actor-state cap itself is exhausted, requests are refused without allocating another entry or repeatedly sending errors.

Root folder 0 is valid as a create parent or move destination, not as a deletable folder or network ID. Single-target actions reject multiple IDs. Duplicate bulk IDs are processed once, but do not bypass the raw wire-size bound. Network/folder mutations validate the whole group before commit through the stage 01 APIs. Permission and validation failures do not setDirty, notify successful mutation, or change the world. Quotas may be consumed by rejected attempts to bound retry work.

Isolation listing is capped at 1024 bounded summaries, avoiding copies of raw NBT. Isolation removal also admits conservative record/position reconstruction work before rebuilding reservations. These synchronous admission limits can refuse oversized legacy requests; no data is truncated or deleted as a fallback. Large-job pagination and queued processing remain later plans.

## Repair and Forge placement cancellation

Repair remains free; no item use or inventory debit was introduced. Whole-request permission/ID/size validation precedes placements. The worker rechecks actor rights and network object identity per element, and retains dimension, height, world border, mayInteract, loaded-chunk, occupied/BlockEntity and supported-block checks. It additionally checks survival/collision conditions for the restored block. Per-position skips/failures are reported separately from whole-request rejection; protection cancellation contributes to the explicitly labelled failed/protected count, never placed.

`ProtectedRepairPlacement` follows Forge 1.20.1-47.4.10's `ForgeHooks.onPlaceItemIntoWorld` contract, verified in the locally installed forge-1.20.1-47.4.10-sources.jar: capture BlockSnapshots, tentatively set the block with notification deferred, call ForgeEventFactory.onBlockPlace/onMultiBlockPlace, restore snapshots in reverse order under restoringBlockSnapshots if canceled, otherwise invoke onPlace and markAndNotifyBlock. The event therefore observes the old snapshot and actual tentative new state. Nested snapshot capture is refused. Rights and membership are checked again after the event and before commit. No fake player or invented permission event is used in production.

The exact-state repair worker remains synchronous. A loss of permission stops further placements; accepted earlier placements are not rolled back. This is not a multi-tick job system. Tests prove cancellation on the supported Forge event; compatibility with every third-party claims/protection mod is not claimed.

## Verification

New PermissionsGameTests cover survival/creative, mayBuild=false, spectator and elevated spectator sources; all action categories; actual single/bulk/named packet handlers without GUI; equivalent commands; atomic invalid batches; root IDs, names, folders/cycles; console behavior; wrong packet direction; permission loss between enqueue and execution; Forge cancellation and loss of rights during tentative placement; unchanged blocks, inventory and saved dirty state; normal permitted repair; raw packet sizes/enums; request windows and session reset; 50000-element boundary; and rejection before a fifth full-budget scan.

An initial existing diagnostics test failed because unrelated fixtures reused the same global fake-player session and consumed its newly shared scan quota. Those fixtures now use distinct test player instances; the production quota was not relaxed. Existing stage 01/04 and repair tests remain in the suite.

Final result: Gradle 8.8 / Java 17, `build visualChecks runGameTestServer --offline --console=plain`: BUILD SUCCESSFUL in 1m, exit 0. All 14 GameTests passed, including 5 permissions tests and the existing Stage7 185 checks; visualChecks passed. `git diff --check` passed. Log: baseline-results/permissions-validation.log (ignored local artifact). Interactive client/multiplayer UX and a particular third-party protection mod have not been tested. Manual acceptance is the next step; test worlds are disposable GameTest fixtures and no baseline backup worlds were edited.
